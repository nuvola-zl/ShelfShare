package com.shelf.borrow.task;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import com.shelf.api.dto.donate.ReleaseStockRequest;
import com.shelf.api.feign.donate.DonateFeignApi;
import com.shelf.api.feign.user.UserFeignApi;
import com.shelf.borrow.entity.BorrowRecord;
import com.shelf.borrow.entity.DeadLetterRecord;
import com.shelf.borrow.mapper.BorrowRecordMapper;
import com.shelf.borrow.mapper.DeadLetterRecordMapper;
import com.shelf.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowScheduledTask {

    private final BorrowRecordMapper borrowRecordMapper;
    private final DonateFeignApi donateFeignApi;
    private final UserFeignApi userFeignApi;
    private final StringRedisTemplate redisTemplate;
    private final DeadLetterRecordMapper deadLetterRecordMapper;

    private static final String LOCK_PREFIX = "shelf:task:lock:";

    /**
     * 1. 超时释放：每 30 分钟扫描一次
     * 7 天未领取 → 改 CANCELLED → 释放 DB 库存 → 回滚额度 → Redis 有 key 则回滚
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void releaseTimeoutRecords() {
        String lockKey = LOCK_PREFIX + "releaseTimeout";
        if (!tryLock(lockKey, 600)) {
            log.info("超时释放任务已被其他实例执行，跳过");
            return;
        }

        try {
            log.info("开始扫描超时未领取记录");
            LocalDateTime now = LocalDateTime.now();

            List<BorrowRecord> timeoutList = borrowRecordMapper.selectList(
                    new LambdaQueryWrapper<BorrowRecord>()
                            .eq(BorrowRecord::getStatus, "PENDING_PICKUP")
                            .lt(BorrowRecord::getPickupDeadline, now)
                            .last("LIMIT 100")
            );

            for (BorrowRecord record : timeoutList) {
                try {
                    // 1. 先释放 donate 的 DB 库存
                    ReleaseStockRequest req = new ReleaseStockRequest();
                    req.setInstanceId(record.getInstanceId());
                    req.setIsbn(record.getIsbn());
                    Result<Void> releaseResult = donateFeignApi.releaseStock(req);
                    if (!releaseResult.isSuccess()) {
                        log.error("超时释放库存失败: recordNo={}, instanceId={}, code={}, msg={}",
                                record.getRecordNo(), record.getInstanceId(), releaseResult.getCode(), releaseResult.getMsg());
                        continue; // 释放失败就跳过这条，不改本地状态，下次定时任务再来试
                    }

                    // 2. 再改本地状态为 CANCELLED（乐观锁，防止并发领取）
                    int updated = borrowRecordMapper.update(null,
                            new UpdateWrapper<BorrowRecord>()
                                    .eq("id", record.getId())
                                    .eq("status", "PENDING_PICKUP")
                                    .set("status", "CANCELLED")
                                    .set("cancel_reason", "SYSTEM_TIMEOUT"));
                    if (updated == 0) {
                        log.warn("超时释放状态已被并发修改，跳过: recordNo={}", record.getRecordNo());
                        continue;
                    }

                    // 3. 回滚用户额度（apply 时已占用）
                    Result<Void> decrResult = userFeignApi.decreaseBorrowCount(record.getUserId(), record.getRequestId());
                    if (!decrResult.isSuccess()) {
                        log.error("超时释放回滚额度失败（需人工补偿）: recordNo={}, userId={}, code={}, msg={}",
                                record.getRecordNo(), record.getUserId(), decrResult.getCode(), decrResult.getMsg());
                        // 落入死信表，管理员后续可手动补偿
                        DeadLetterRecord dlr = new DeadLetterRecord();
                        dlr.setType("PICKUP_TIMEOUT_RELEASE_FAIL");
                        dlr.setBizType("BORROW");
                        dlr.setBizId(record.getRecordNo());
                        dlr.setUserId(record.getUserId());
                        dlr.setErrorMsg("超时释放回滚额度失败: " + decrResult.getMsg());
                        dlr.setContext(JSON.toJSONString(Map.of(
                                "compensateType", "DECREASE_BORROW",
                                "requestId", record.getRequestId(),
                                "userId", record.getUserId(),
                                "recordNo", record.getRecordNo()
                        )));
                        deadLetterRecordMapper.insert(dlr);
                    }

                    // 4. 如果该 ISBN 还在 Redis 热门池中，回滚 Redis 库存
                    String stockKey = "stock:" + record.getIsbn();
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                        redisTemplate.opsForValue().increment(stockKey);
                    }

                    log.info("超时释放成功: recordNo={}", record.getRecordNo());

                } catch (Exception e) {
                    log.error("超时释放失败（下次定时任务将重试）: recordNo={}, error={}",
                            record.getRecordNo(), e.getMessage());
                }
            }
        } finally {
            releaseLock(lockKey);
        }
    }


    /**
     * 2. 到期提醒：每天早 9 点扫描
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void remindDueRecords() {
        String lockKey = LOCK_PREFIX + "remindDue";
        if (!tryLock(lockKey, 3600)) return;

        try {
            log.info("开始扫描到期提醒");
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime threeDaysLater = now.plusDays(3);

            List<BorrowRecord> list = borrowRecordMapper.selectList(
                    new LambdaQueryWrapper<BorrowRecord>()
                            .eq(BorrowRecord::getStatus, "BORROWED")
                            .isNull(BorrowRecord::getRemindTime)
                            .lt(BorrowRecord::getDueDate, threeDaysLater)
                            .ge(BorrowRecord::getDueDate, now)
                            .last("LIMIT 100")
            );

            for (BorrowRecord record : list) {
                try {
                    log.info("到期提醒: userId={}, recordNo={}, dueDate={}",
                            record.getUserId(), record.getRecordNo(), record.getDueDate());

                    borrowRecordMapper.update(null,
                            new UpdateWrapper<BorrowRecord>()
                                    .eq("id", record.getId())
                                    .set("remind_time", now));

                } catch (Exception e) {
                    log.error("提醒失败: recordNo={}", record.getRecordNo());
                }
            }
        } finally {
            releaseLock(lockKey);
        }
    }

    /**
     * 3. 逾期扫描：每天早 9 点 05 分
     * 先调 user 增加逾期次数，成功后再改状态，防止状态改了但逾期次数没加上
     */
    @Scheduled(cron = "0 5 9 * * ?")
    public void markOverdueRecords() {
        String lockKey = LOCK_PREFIX + "markOverdue";
        if (!tryLock(lockKey, 3600)) return;

        try {
            log.info("开始扫描逾期记录");
            LocalDateTime now = LocalDateTime.now();

            List<BorrowRecord> list = borrowRecordMapper.selectList(
                    new LambdaQueryWrapper<BorrowRecord>()
                            .eq(BorrowRecord::getStatus, "BORROWED")
                            .lt(BorrowRecord::getDueDate, now)
                            .last("LIMIT 100")
            );

            for (BorrowRecord record : list) {
                try {
                    // 先改本地状态（乐观锁），成功后再调 user 加逾期次数
                    int updated = borrowRecordMapper.update(null,
                            new UpdateWrapper<BorrowRecord>()
                                    .eq("id", record.getId())
                                    .eq("status", "BORROWED")
                                    .set("status", "OVERDUE"));
                    if (updated == 0) {
                        log.warn("逾期状态已被并发修改，跳过: recordNo={}", record.getRecordNo());
                        continue;
                    }

                    // 本地改成功了，再调 user 增加逾期次数
                    Result<Void> overdueResult = userFeignApi.increaseOverdue(record.getUserId(), record.getRequestId());
                    if (!overdueResult.isSuccess()) {
                        log.error("增加逾期次数失败: recordNo={}, userId={}, code={}, msg={}",
                                record.getRecordNo(), record.getUserId(), overdueResult.getCode(), overdueResult.getMsg());
                        // 这里注意：本地已经改成 OVERDUE 了，逾期次数加不上，不能回滚（确实逾期了）
                        // 所以只打日志告警，后续需要人工或通过补偿表补加逾期次数
                        // 落入死信表
                        DeadLetterRecord dlr = new DeadLetterRecord();
                        dlr.setType("OVERDUE_UNPROCESSED");
                        dlr.setBizType("BORROW");
                        dlr.setBizId(record.getRecordNo());
                        dlr.setUserId(record.getUserId());
                        dlr.setErrorMsg("增加逾期次数失败: " + overdueResult.getMsg());
                        dlr.setContext(JSON.toJSONString(Map.of(
                                "compensateType", "INCREASE_OVERDUE",
                                "requestId", record.getRequestId(),
                                "userId", record.getUserId(),
                                "recordNo", record.getRecordNo()
                        )));
                        deadLetterRecordMapper.insert(dlr);
                    }

                    log.info("标记逾期成功: recordNo={}", record.getRecordNo());

                } catch (Exception e) {
                    log.error("标记逾期失败（将重试）: recordNo={}", record.getRecordNo());
                }
            }
        } finally {
            releaseLock(lockKey);
        }
    }

    private boolean tryLock(String key, long seconds) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", seconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    private void releaseLock(String key) {
        redisTemplate.delete(key);
    }
}