package com.shelf.borrow.task;

import com.alibaba.fastjson2.JSON;
import com.shelf.api.feign.user.UserFeignApi;
import com.shelf.borrow.domain.dto.PendingBorrowDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowCompensateTask {

    private final StringRedisTemplate redisTemplate;
    private final UserFeignApi userFeignApi;

    /**
     * 每 5 分钟扫描一次
     * 如果 pending 超过 10 分钟还没被消费完，回滚 Redis 库存 + 回滚用户额度
     */
//    @Scheduled(cron = "0 */5 * * * ?")
//    public void compensatePendingBorrow() {
//        Set<String> keys = redisTemplate.keys("borrow:pending:*");
//        if (keys == null || keys.isEmpty()) return;
//
//        for (String key : keys) {
//            String json = redisTemplate.opsForValue().get(key);
//            if (json == null) continue;
//
//            try {
//                PendingBorrowDTO pending = JSON.parseObject(json, PendingBorrowDTO.class);
//                if (pending == null) continue;
//
//                if (ChronoUnit.MINUTES.between(pending.getCreateTime(), LocalDateTime.now()) > 10) {
//                    // 1. 回滚 Redis 库存（如果该 ISBN 还在热门池中）
//                    String stockKey = "stock:" + pending.getIsbn();
//                    if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
//                        redisTemplate.opsForValue().increment(stockKey);
//                    }
//
//                    // 2. 回滚用户额度（apply 时已占用）
//                    try {
//                        userFeignApi.decreaseBorrowCount(pending.getUserId(), pending.getRequestId());
//                    } catch (Exception e) {
//                        log.error("补偿任务回滚额度失败: requestId={}, userId={}", pending.getRequestId(), pending.getUserId());
//                    }
//
//                    // 3. 删除 pending 记录
//                    redisTemplate.delete(key);
//                    log.warn("申领超时回滚库存和额度: key={}, isbn={}", key, pending.getIsbn());
//                }
//            } catch (Exception e) {
//                log.error("补偿任务解析失败: key={}", key);
//            }
//        }
//    }

    @Scheduled(cron = "0 */5 * * * ?")
    public void alarmPendingBorrow() {
        Set<String> keys = redisTemplate.keys("borrow:pending:*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) continue;

            try {
                PendingBorrowDTO pending = JSON.parseObject(json, PendingBorrowDTO.class);
                if (pending == null) continue;

                long hours = ChronoUnit.HOURS.between(pending.getCreateTime(), LocalDateTime.now());
                if (hours >= 2) {  // 超过2小时就告警，提醒管理员
                    log.warn("【告警】pending 申领超过{}小时未处理，请管理员关注: key={}, isbn={}, userId={}",
                            hours, key, pending.getIsbn(), pending.getUserId());
                }
            } catch (Exception e) {
                log.error("补偿任务解析失败: key={}", key);
            }
        }
    }
}