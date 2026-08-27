package com.shelf.borrow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.shelf.api.dto.donate.BookInstanceDTO;
import com.shelf.api.dto.donate.DeductStockRequest;
import com.shelf.api.dto.donate.ReleaseStockRequest;
import com.shelf.api.dto.donate.ReturnStockRequest;
import com.shelf.api.dto.user.UserStatusDTO;
import com.shelf.api.feign.donate.DonateFeignApi;
import com.shelf.api.feign.user.UserFeignApi;
import com.shelf.borrow.config.BorrowRabbitMqConfig;
import com.shelf.borrow.domain.dto.ApplyDTO;
import com.shelf.borrow.domain.dto.BorrowApplyMessage;
import com.shelf.borrow.domain.dto.PendingBorrowDTO;
import com.shelf.borrow.domain.vo.BorrowRecordVO;
import com.shelf.borrow.entity.BookInstance;
import com.shelf.borrow.entity.BorrowRecord;
import com.shelf.borrow.entity.DeadLetterRecord;
import com.shelf.borrow.mapper.BookInstanceMapper;
import com.shelf.borrow.mapper.BorrowRecordMapper;
import com.shelf.borrow.mapper.DeadLetterRecordMapper;
import com.shelf.borrow.service.IBorrowService;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.context.UserContext;
import com.shelf.common.exception.BusinessException;

import com.shelf.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowServiceImpl extends ServiceImpl<BorrowRecordMapper, BorrowRecord> implements IBorrowService {

    private final UserFeignApi userFeignApi;
    private final DonateFeignApi donateFeignApi;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final RabbitTemplate rabbitTemplate;
    private final BookInstanceMapper bookInstanceMapper;
    private final DeadLetterRecordMapper deadLetterRecordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BorrowRecordVO apply(ApplyDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }

        String lockKey = "shelf:borrow:user:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            locked = lock.tryLock(3, -1, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作太频繁，请稍后再试");
            }

            // 0. 幂等
            if (StrUtil.isNotBlank(dto.getRequestId())) {
                BorrowRecord exist = baseMapper.selectOne(
                        new LambdaQueryWrapper<BorrowRecord>()
                                .eq(BorrowRecord::getRequestId, dto.getRequestId()));
                if (exist != null) {
                    log.info("申领幂等命中: requestId={}", dto.getRequestId());
                    BorrowRecordVO vo = new BorrowRecordVO();
                    BeanUtil.copyProperties(exist, vo);
                    return vo;
                }
            }

            // 1. Feign 查 user 状态
            Result<UserStatusDTO> userResult = userFeignApi.getUserStatus(userId);
            if (!userResult.isSuccess() || userResult.getData() == null) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户服务调用失败");
            }
            UserStatusDTO userStatus = userResult.getData();
            if (Boolean.TRUE.equals(userStatus.getFrozen())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "账号已冻结，无法申领");
            }
            if (userStatus.getCurrentBorrowCount() != null && userStatus.getCurrentBorrowCount() >= 5) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "借阅数量已达上限（5本）");
            }

            // 2. 检查是否已借过同一 ISBN
            Long existCount = baseMapper.selectCount(
                    new LambdaQueryWrapper<BorrowRecord>()
                            .eq(BorrowRecord::getUserId, userId)
                            .eq(BorrowRecord::getIsbn, dto.getIsbn())
                            .in(BorrowRecord::getStatus, Arrays.asList("PENDING_PICKUP", "BORROWED", "OVERDUE"))
            );
            if (existCount != null && existCount > 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "您已借阅该教材，归还后可再次申领");
            }

            // 3. 判断是否为热门（Redis 是否预热）
            String stockKey = "stock:" + dto.getIsbn();
            boolean isHot = Boolean.TRUE.equals(redisTemplate.hasKey(stockKey));

            if (isHot) {
                return applyHot(dto, userId, stockKey);
            } else {
                return applyNormal(dto, userId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 热门教材申领：Redis 预扣 + MQ 异步
     */
    private BorrowRecordVO applyHot(ApplyDTO dto, Long userId, String stockKey) {
        // ① 同步占用额度（防止狂刷）
        Result<Void> incrResult = userFeignApi.increaseBorrowCount(userId, dto.getRequestId());
        if (!incrResult.isSuccess()) {
            log.error("热门申领额度占用失败: userId={}, requestId={}, code={}, msg={}",
                    userId, dto.getRequestId(), incrResult.getCode(), incrResult.getMsg());
            throw new BusinessException(incrResult.getCode(), incrResult.getMsg());
        }

        // ② Redis 预扣库存
        Long remain = redisTemplate.opsForValue().decrement(stockKey);
        if (remain == null || remain < 0) {
            if (remain != null && remain < 0) {
                redisTemplate.opsForValue().increment(stockKey); // 回滚 Redis
            }
            // 回滚额度
            Result<Void> decrResult = userFeignApi.decreaseBorrowCount(userId, dto.getRequestId());
            if (!decrResult.isSuccess()) {
                log.error("热门申领回滚额度失败: userId={}, requestId={}, code={}, msg={}",
                        userId, dto.getRequestId(), decrResult.getCode(), decrResult.getMsg());
                DeadLetterRecord dlr = new DeadLetterRecord();
                dlr.setType("BORROW_COMPENSATE_FAIL");
                dlr.setBizType("BORROW");
                dlr.setBizId(dto.getRequestId());
                dlr.setUserId(userId);
                dlr.setErrorMsg("热门申领回滚额度失败: " + decrResult.getMsg());
                dlr.setContext(JSON.toJSONString(Map.of(
                        "compensateType", "DECREASE_BORROW",
                        "requestId", dto.getRequestId(),
                        "userId", userId,
                        "reason", "applyHot Redis库存不足回滚"
                )));
                deadLetterRecordMapper.insert(dlr);
            }
            throw new BusinessException(ErrorCode.PARAM_ERROR, "库存不足，已被抢完");
        }

        try {
            // ③ 生成凭证号
            String recordNo = generateRecordNo();

            // ④ 写 Redis 临时记录（TTL=10分钟，防 MQ 丢失）
            String pendingKey = "borrow:pending:" + recordNo;
            PendingBorrowDTO pending = new PendingBorrowDTO(userId, dto.getIsbn(), dto.getRequestId(), LocalDateTime.now());
            redisTemplate.opsForValue().set(
                    pendingKey,
                    JSON.toJSONString(pending),
                    1,
                    TimeUnit.DAYS  //todo时间延长一点吧：当 donate 服务异常时，MQ 消息会进入死信队列，同时 Redis 中保留 pending 凭证。
                    // 为了避免自动化补偿任务与管理员人工重试产生竞态，将 pending 的存活周期设置为 1 天，并取消自动清理逻辑，改为纯告警模式。这样管理员在一天内随时可以通过后台重试死信，而不会触发重复扣减。」
            );

            // ⑤ 发 MQ 异步处理真实扣减
            BorrowApplyMessage msg = new BorrowApplyMessage();
            msg.setRecordNo(recordNo);
            msg.setUserId(userId);
            msg.setIsbn(dto.getIsbn());
            msg.setRequestId(dto.getRequestId());

            rabbitTemplate.convertAndSend(
                    BorrowRabbitMqConfig.BORROW_EXCHANGE,
                    BorrowRabbitMqConfig.BORROW_APPLY_KEY,
                    msg
            );

            // ⑥ 立刻返回前端（状态：处理中）
            BorrowRecordVO vo = new BorrowRecordVO();
            vo.setRecordNo(recordNo);
            vo.setIsbn(dto.getIsbn());
            vo.setStatus("PROCESSING");
            return vo;

        } catch (Exception e) {
            // 异常回滚 Redis 库存 + 额度
            redisTemplate.opsForValue().increment(stockKey);
            try {
                userFeignApi.decreaseBorrowCount(userId, dto.getRequestId());
            } catch (Exception ex) {
                log.error("热门申领异常回滚额度失败: userId={}, requestId={}", userId, dto.getRequestId());
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "申领失败，请重试");
        }
    }

    /**
     * 非热门教材申领：直接同步扣 DB，不走 MQ
     */
    private BorrowRecordVO applyNormal(ApplyDTO dto, Long userId) {
        // ① 同步扣 DB 库存（FIFO 分配实体书）
        DeductStockRequest deductReq = new DeductStockRequest();
        deductReq.setIsbn(dto.getIsbn());
        deductReq.setUserId(userId);
        Result<BookInstanceDTO> deductResult = donateFeignApi.deductStock(deductReq);
        if (!deductResult.isSuccess() || deductResult.getData() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "库存不足");
        }
        BookInstanceDTO instance = deductResult.getData();

        try {

            // ② 占用额度
            Result<Void> incrResult = userFeignApi.increaseBorrowCount(userId, dto.getRequestId());
            if (!incrResult.isSuccess()) {
                // 额度占用失败，直接抛异常，本地事务回滚，前面扣的库存也会在 catch 里释放
                throw new BusinessException(incrResult.getCode(), incrResult.getMsg());
            }

            // ③ 直接生成本地借阅记录
            String recordNo = generateRecordNo();
            BorrowRecord record = new BorrowRecord();
            record.setRecordNo(recordNo);
            record.setRequestId(dto.getRequestId());
            record.setUserId(userId);
            record.setInstanceId(instance.getInstanceId());
            record.setIsbn(dto.getIsbn());
            record.setBookTitle(instance.getTitle());
            record.setInstanceCode(instance.getInstanceCode());
            record.setLocation(instance.getLocation());
            record.setStatus("PENDING_PICKUP");
            record.setBorrowTime(LocalDateTime.now());
            record.setPickupDeadline(LocalDateTime.now().plusDays(7));
//            record.setQrCode(Base64.getEncoder().encodeToString(recordNo.getBytes()));//todo目前还不需要编码
            baseMapper.insert(record);

            // ④ 返回前端（已生成记录，可直接查看）
            BorrowRecordVO vo = new BorrowRecordVO();
            BeanUtil.copyProperties(record, vo);
            return vo;

        } catch (Exception e) {
            // 回滚额度和 DB 库存
            Result<Void> decrResult = userFeignApi.decreaseBorrowCount(userId, dto.getRequestId());
            if (!decrResult.isSuccess()) {
                log.error("非热门申领回滚额度失败: userId={}, requestId={}, code={}, msg={}",
                        userId, dto.getRequestId(), decrResult.getCode(), decrResult.getMsg());
                // 落入死信表，管理员后续可手动补偿
                DeadLetterRecord dlr = new DeadLetterRecord();
                dlr.setType("BORROW_COMPENSATE_FAIL");
                dlr.setBizType("BORROW");
                dlr.setBizId(dto.getRequestId());
                dlr.setUserId(userId);
                dlr.setErrorMsg("非热门申领回滚额度失败: " + decrResult.getMsg());
                dlr.setContext(JSON.toJSONString(Map.of(
                        "compensateType", "DECREASE_BORROW",
                        "requestId", dto.getRequestId(),
                        "userId", userId,
                        "reason", "applyNormal catch 补偿失败"
                )));
                deadLetterRecordMapper.insert(dlr);
            }
            ReleaseStockRequest releaseReq = new ReleaseStockRequest();
            releaseReq.setInstanceId(instance.getInstanceId());
            releaseReq.setIsbn(dto.getIsbn());
            Result<Void> releaseResult = donateFeignApi.releaseStock(releaseReq);
            if (!releaseResult.isSuccess()) {
                log.error("非热门申领释放库存失败: instanceId={}, isbn={}, code={}, msg={}",
                        instance.getInstanceId(), dto.getIsbn(), releaseResult.getCode(), releaseResult.getMsg());
                DeadLetterRecord dlr = new DeadLetterRecord();
                dlr.setType("BORROW_COMPENSATE_FAIL");
                dlr.setBizType("BORROW");
                dlr.setBizId(dto.getRequestId());
                dlr.setUserId(userId);
                dlr.setErrorMsg("非热门申领释放库存失败: " + releaseResult.getMsg());
                dlr.setContext(JSON.toJSONString(Map.of(
                        "compensateType", "RELEASE_STOCK",
                        "instanceId", instance.getInstanceId(),
                        "isbn", dto.getIsbn(),
                        "requestId", dto.getRequestId()
                )));
                deadLetterRecordMapper.insert(dlr);
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "申领失败，请重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pickupConfirm(String recordNo) {
        BorrowRecord record = baseMapper.selectOne(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getRecordNo, recordNo));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "凭证不存在");
        }
        if (!"PENDING_PICKUP".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该记录已处理或状态异常，当前状态：" + record.getStatus());
        }

        // 1. 更新借阅记录
        record.setStatus("BORROWED");
        record.setPickupTime(LocalDateTime.now());
        record.setDueDate(LocalDateTime.now().plusMonths(4));
        baseMapper.updateById(record);

        // 2. 直接操作表：实体书状态改为 BORROWED
        BookInstance instance = bookInstanceMapper.selectById(record.getInstanceId());
        if (instance != null) {
            instance.setStatus("BORROWED");
            bookInstanceMapper.updateById(instance);
        }

        // 3. 【注意】额度在 apply() 同步阶段已占用，此处不再重复增加
        log.info("领取确认成功: recordNo={}, userId={}", recordNo, record.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnBook(String recordNo) {
        BorrowRecord record = baseMapper.selectOne(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getRecordNo, recordNo));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "凭证不存在");
        }
        if (!"BORROWED".equals(record.getStatus()) && !"OVERDUE".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该记录不可归还，当前状态：" + record.getStatus());
        }

        // 1. 先调 donate 恢复 DB 库存
        ReturnStockRequest req = new ReturnStockRequest();
        req.setInstanceId(record.getInstanceId());
        req.setIsbn(record.getIsbn());
        Result<Void> returnResult = donateFeignApi.returnStock(req);
        if (!returnResult.isSuccess()) {
            // 远程归还失败，本地事务回滚，不能改本地状态
            throw new BusinessException(returnResult.getCode(), "归还库存失败：" + returnResult.getMsg());
        }

        // 2. 同步本地实体书状态（兜底，与 donate 保持一致）
        BookInstance instance = bookInstanceMapper.selectById(record.getInstanceId());
        if (instance != null) {
            instance.setStatus("AVAILABLE");
            bookInstanceMapper.updateById(instance);
        }

        // 3. 如果该 ISBN 还在 Redis 热门池中，恢复 Redis 库存
        String stockKey = "stock:" + record.getIsbn();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
            redisTemplate.opsForValue().increment(stockKey);
        }

        // 4. 释放用户额度
        Result<Void> decrResult = userFeignApi.decreaseBorrowCount(record.getUserId(), record.getRequestId());
        if (!decrResult.isSuccess()) {
            log.error("归还释放额度失败（需人工补偿）: recordNo={}, userId={}, code={}, msg={}",
                    recordNo, record.getUserId(), decrResult.getCode(), decrResult.getMsg());
        }

        // 5. 更新借阅记录
        record.setStatus("RETURNED");
        record.setReturnTime(LocalDateTime.now());
        baseMapper.updateById(record);

        log.info("归还成功: recordNo={}, userId={}", recordNo, record.getUserId());
    }

    @Override
    public List<BorrowRecordVO> listMyBorrows() {
        Long userId = UserContext.getUserId();
        List<BorrowRecord> list = baseMapper.selectList(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getUserId, userId)
                        .orderByDesc(BorrowRecord::getCreateTime));
        return list.stream().map(r -> {
            BorrowRecordVO vo = new BorrowRecordVO();
            BeanUtil.copyProperties(r, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public BorrowRecordVO getRecordDetail(String recordNo) {
        BorrowRecord record = baseMapper.selectOne(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getRecordNo, recordNo));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "凭证不存在");
        }
        BorrowRecordVO vo = new BorrowRecordVO();
        BeanUtil.copyProperties(record, vo);
        return vo;
    }

    private String generateRecordNo() {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "shelf:borrow:seq:" + day;
        Long seq = redisTemplate.opsForValue().increment(key);
        if (seq == null) seq = 1L;
        // 格式与文档一致：BR20260821-000001
        return String.format("BR%s-%06d", day, seq);
    }
}