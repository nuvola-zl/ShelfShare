package com.shelf.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.shelf.user.entity.IdempotentRecord;
import com.shelf.user.entity.UserBorrowQuota;
import com.shelf.user.mapper.IdempotentRecordMapper;
import com.shelf.user.mapper.UserBorrowQuotaMapper;
import com.shelf.user.service.IUserBorrowQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBorrowQuotaServiceImpl extends ServiceImpl<UserBorrowQuotaMapper, UserBorrowQuota> implements IUserBorrowQuotaService {

    private final IdempotentRecordMapper idempotentRecordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increaseBorrowCount(Long userId, String requestId) {
        if (!insertIdempotent(requestId, "BORROW_INCREASE")) {
            log.info("增加借阅数幂等命中，跳过: requestId={}", requestId);
            return;
        }

        UserBorrowQuota quota = getById(userId);
        if (quota == null) {
            quota = new UserBorrowQuota();
            quota.setUserId(userId);
            quota.setCurrentBorrowCount(1);
            quota.setTotalBorrowCount(1);
            save(quota);
        } else {
            quota.setCurrentBorrowCount(quota.getCurrentBorrowCount() + 1);
            quota.setTotalBorrowCount(quota.getTotalBorrowCount() + 1);
            updateById(quota);
        }
        log.info("用户借阅数增加成功: userId={}, requestId={}", userId, requestId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decreaseBorrowCount(Long userId, String requestId) {
        if (!insertIdempotent(requestId, "BORROW_DECREASE")) {
            log.info("减少借阅数幂等命中，跳过: requestId={}", requestId);
            return;
        }

        UserBorrowQuota quota = getById(userId);
        if (quota == null) {
            log.warn("减少借阅数失败，用户额度记录不存在: userId={}", userId);
            return;
        }

        int newCurrent = Math.max(0, quota.getCurrentBorrowCount() - 1);
        quota.setCurrentBorrowCount(newCurrent);
        updateById(quota);
        log.info("用户借阅数减少成功: userId={}, requestId={}", userId, requestId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increaseOverdue(Long userId, String requestId) {
        if (!insertIdempotent(requestId, "OVERDUE_INCREASE")) {
            log.info("增加逾期次数幂等命中，跳过: requestId={}", requestId);
            return;
        }

        UserBorrowQuota quota = getById(userId);
        if (quota == null) {
            quota = new UserBorrowQuota();
            quota.setUserId(userId);
            quota.setCurrentBorrowCount(0);
            quota.setTotalBorrowCount(0);
            quota.setOverdueCount(1);
            save(quota);
        } else {
            quota.setOverdueCount(quota.getOverdueCount() + 1);
            updateById(quota);
        }
        log.info("用户逾期次数增加成功: userId={}, requestId={}", userId, requestId);
    }

    /**
     * 插入幂等记录，true=首次处理，false=已处理过
     */
    private boolean insertIdempotent(String requestId, String bizType) {
        try {
            IdempotentRecord record = new IdempotentRecord();
            record.setRequestId(requestId);
            record.setBizType(bizType);
            idempotentRecordMapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}