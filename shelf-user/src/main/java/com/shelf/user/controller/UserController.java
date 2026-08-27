package com.shelf.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;

import com.shelf.api.dto.user.UserStatusDTO;
import com.shelf.common.result.Result;
import com.shelf.user.domain.dto.UserProfileDTO;
import com.shelf.user.domain.vo.UserInfoVO;
import com.shelf.user.entity.UserBorrowQuota;
import com.shelf.user.service.IUserBorrowQuotaService;
import com.shelf.user.service.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final IUserService userService;
    private final IUserBorrowQuotaService quotaService;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/info")
    @SaCheckLogin
    public Result<UserInfoVO> getCurrentUserInfo() {
        Long userId = StpUtil.getLoginIdAsLong();
        return Result.success(userService.getUserInfo(userId));
    }

    @GetMapping("/{userId}/status")
    public Result<UserStatusDTO> getUserStatus(@PathVariable Long userId) {
        UserInfoVO user = userService.getUserInfo(userId);
        UserBorrowQuota quota = quotaService.getById(userId);

        UserStatusDTO dto = new UserStatusDTO();
        dto.setUserId(userId);
        dto.setRole(user.getRole());
        String frozenFlag = redisTemplate.opsForValue().get("user:frozen:" + userId);
        boolean isFrozen = "1".equals(frozenFlag) || (quota != null && quota.getOverdueCount() >= 3);
        dto.setFrozen(isFrozen);
        dto.setCurrentBorrowCount(quota != null ? quota.getCurrentBorrowCount() : 0);
        dto.setOverdueCount(quota != null ? quota.getOverdueCount() : 0);
        return Result.success(dto);
    }

    /**
     * 增加用户当前借阅数（borrow MQ消费者调用，幂等）
     */
    @PostMapping("/{userId}/borrow-count/increase")
    public Result<Void> increaseBorrowCount(@PathVariable Long userId,
                                            @RequestParam("requestId") String requestId) {
        quotaService.increaseBorrowCount(userId, requestId);
        return Result.success();
    }

    /**
     * 减少用户当前借阅数（borrow 归还/超时释放时调用，幂等）
     */
    @PostMapping("/{userId}/borrow-count/decrease")
    public Result<Void> decreaseBorrowCount(@PathVariable Long userId,
                                            @RequestParam("requestId") String requestId) {
        quotaService.decreaseBorrowCount(userId, requestId);
        return Result.success();
    }

    /**
     * 增加用户逾期次数（borrow 定时任务调用，幂等）
     */
    @PostMapping("/{userId}/overdue")
    public Result<Void> increaseOverdue(@PathVariable Long userId,
                                        @RequestParam("requestId") String requestId) {
        quotaService.increaseOverdue(userId, requestId);
        return Result.success();
    }

    @PostMapping("/{userId}/freeze")
    public Result<Void> freezeUser(@PathVariable Long userId) {
        redisTemplate.opsForValue().set("user:frozen:" + userId, "1");
        log.info("管理员冻结用户: userId={}", userId);
        return Result.success();
    }

    @PostMapping("/{userId}/unfreeze")
    public Result<Void> unfreezeUser(@PathVariable Long userId) {
        redisTemplate.delete("user:frozen:" + userId);
        log.info("管理员解冻用户: userId={}", userId);
        return Result.success();
    }

    /**
     * 完善/更新当前用户资料（注册第二步）
     */
    @PutMapping("/profile")
    @SaCheckLogin
    public Result<Void> updateProfile(@RequestBody @Valid UserProfileDTO dto) {
        Long userId = StpUtil.getLoginIdAsLong();
        userService.updateProfile(userId, dto);
        return Result.success();
    }
}