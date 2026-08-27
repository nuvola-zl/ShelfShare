package com.shelf.api.feign.user;

import com.shelf.api.dto.user.UserStatusDTO;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserFeignApiFallbackFactory implements FallbackFactory<UserFeignApi> {

    @Override
    public UserFeignApi create(Throwable cause) {
        log.error("【熔断降级】user 服务调用失败: {}", cause.getMessage());

        return new UserFeignApi() {
            @Override
            public Result<UserStatusDTO> getUserStatus(Long userId) {
                log.error("【熔断降级】查询用户状态失败，userId={}", userId);
                UserStatusDTO safe = new UserStatusDTO();
                safe.setUserId(userId);
                safe.setFrozen(true);
                safe.setCurrentBorrowCount(999);
                safe.setOverdueCount(999);
                return Result.success(safe);
            }

            @Override
            public Result<Void> increaseBorrowCount(Long userId, String requestId) {
                log.error("【熔断降级】增加借阅计数失败，userId={}", userId);
                return Result.error(ErrorCode.REMOTE_CALL_ERROR, "用户服务暂不可用，借阅计数更新失败，请稍后重试");
            }

            @Override
            public Result<Void> decreaseBorrowCount(Long userId, String requestId) {
                log.error("【熔断降级】减少借阅计数失败，userId={}", userId);
                return Result.error(ErrorCode.REMOTE_CALL_ERROR, "用户服务暂不可用，归还计数更新失败，请稍后重试");
            }

            @Override
            public Result<Void> increaseOverdue(Long userId, String requestId) {
                log.error("【熔断降级】增加逾期计数失败，userId={}", userId);
                return Result.error(ErrorCode.REMOTE_CALL_ERROR, "用户服务暂不可用，逾期记录更新失败，请稍后重试");
            }
        };
    }
}