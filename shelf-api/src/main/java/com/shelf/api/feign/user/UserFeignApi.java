package com.shelf.api.feign.user;

import com.shelf.api.dto.user.UserStatusDTO;
import com.shelf.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "shelf-user",
        path = "/api/user",
        fallbackFactory = UserFeignApiFallbackFactory.class  // 改这里
)
public interface UserFeignApi {

    @GetMapping("/{userId}/status")
    Result<UserStatusDTO> getUserStatus(@PathVariable("userId") Long userId);

    @PostMapping("/{userId}/borrow-count/increase")
    Result<Void> increaseBorrowCount(@PathVariable("userId") Long userId,
                                     @RequestParam("requestId") String requestId);

    @PostMapping("/{userId}/borrow-count/decrease")
    Result<Void> decreaseBorrowCount(@PathVariable("userId") Long userId,
                                     @RequestParam("requestId") String requestId);

    @PostMapping("/{userId}/overdue")
    Result<Void> increaseOverdue(@PathVariable("userId") Long userId,
                                 @RequestParam("requestId") String requestId);
}