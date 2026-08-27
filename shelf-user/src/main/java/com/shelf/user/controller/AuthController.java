package com.shelf.user.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.result.Result;
import com.shelf.user.domain.dto.UserLoginDTO;
import com.shelf.user.domain.dto.UserRegisterDTO;
import com.shelf.user.domain.vo.UserLoginVO;
import com.shelf.user.service.IUserService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IUserService userService;

    /**
     * 初始化限流规则：登录接口防暴力破解
     */
    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule rule = new FlowRule();
        rule.setResource("userLogin");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(5);  // 每秒最多 5 次登录请求
        rule.setLimitApp("default");

        rules.add(rule);
        FlowRuleManager.loadRules(rules);
        System.out.println("【Sentinel】userLogin 限流规则已加载: QPS=5");
    }

    @PostMapping("/login")
    @SentinelResource(value = "userLogin", blockHandler = "loginBlockHandler")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    /**
     * 限流兜底方法
     */
    public Result<UserLoginVO> loginBlockHandler(UserLoginDTO dto, BlockException ex) {
        return Result.error(ErrorCode.SYSTEM_BUSY, "登录请求过于频繁，请稍后再试");
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody UserRegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }
}