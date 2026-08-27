package com.shelf.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shelf.user.entity.UserBorrowQuota;


public interface IUserBorrowQuotaService extends IService<UserBorrowQuota> {

    /**
     * 增加用户借阅数（幂等）
     */
    void increaseBorrowCount(Long userId, String requestId);

    /**
     * 减少用户借阅数（幂等）
     */
    void decreaseBorrowCount(Long userId, String requestId);

    /**
     * 增加用户逾期次数（幂等）
     */
    void increaseOverdue(Long userId, String requestId);
}