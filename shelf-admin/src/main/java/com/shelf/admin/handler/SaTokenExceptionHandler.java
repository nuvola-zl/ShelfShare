package com.shelf.admin.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 处理 Sa-Token 注解鉴权抛出的异常，返回统一的 Result 结构（HTTP 200 + 业务码）。
 * 网关已统一处理未登录，这里主要兜底 @SaCheckRole("ADMIN") 等角色校验失败的情况。
 */
@Slf4j
@RestControllerAdvice
public class SaTokenExceptionHandler {

    @ExceptionHandler(NotRoleException.class)
    public Result<Void> handleNotRole(NotRoleException e) {
        log.warn("角色校验失败: {}", e.getMessage());
        return Result.error(ErrorCode.FORBIDDEN, "无权限访问");
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(NotLoginException e) {
        log.warn("登录校验失败: {}", e.getMessage());
        return Result.error(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
    }
}