package com.shelf.borrow.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.shelf.borrow.domain.dto.ApplyDTO;
import com.shelf.borrow.domain.vo.BorrowRecordVO;
import com.shelf.borrow.service.IBorrowService;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.result.Result;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/borrow")
@RequiredArgsConstructor
public class BorrowController {

    private final IBorrowService borrowService;

    @PostConstruct
    public void initFlowRules() {
        // ========== 1. 限流规则（你已有的）==========
        List<FlowRule> flowRules = new ArrayList<>();

        FlowRule applyRule = new FlowRule();
        applyRule.setResource("borrowApply");
        applyRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        applyRule.setCount(50);
        applyRule.setLimitApp("default");
        flowRules.add(applyRule);

        FlowRule listRule = new FlowRule();
        listRule.setResource("borrowMyRecords");
        listRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        listRule.setCount(10);
        listRule.setLimitApp("default");
        flowRules.add(listRule);

        FlowRuleManager.loadRules(flowRules);

        // ========== 2. 熔断规则（新增）==========
        List<DegradeRule> degradeRules = new ArrayList<>();
        DegradeRule degradeRule = new DegradeRule();

        // 资源名必须和 @SentinelResource(value = "deductStock") 一致
        degradeRule.setResource("deductStock");

        // 按异常比例熔断
        degradeRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);

        // 50% 的请求异常就打开熔断器
        degradeRule.setCount(0.5);

        // 统计时间窗口：10 秒内
        degradeRule.setStatIntervalMs(10 * 1000);

        // 最少 5 个请求才开始统计（防止刚启动就熔断）
        degradeRule.setMinRequestAmount(5);

        // 熔断后，30 秒内所有请求直接走 blockHandler，不再调 donate
        degradeRule.setTimeWindow(30);

        degradeRules.add(degradeRule);
        DegradeRuleManager.loadRules(degradeRules);

        System.out.println("【Sentinel】限流+熔断规则已加载");
    }

    @PostMapping("/apply")
    @SaCheckLogin
    @SentinelResource(value = "borrowApply", blockHandler = "applyBlockHandler")
    public Result<BorrowRecordVO> apply(@Valid @RequestBody ApplyDTO dto) {
        return Result.success(borrowService.apply(dto));
    }

    public Result<BorrowRecordVO> applyBlockHandler(ApplyDTO dto, BlockException ex) {
        return Result.error(ErrorCode.SYSTEM_BUSY, "申领过于火爆，请稍后再试");
    }

    @GetMapping("/my-records")
    @SaCheckLogin
    @SentinelResource(value = "borrowMyRecords", blockHandler = "myRecordsBlockHandler")
    public Result<List<BorrowRecordVO>> listMyBorrows() {
        return Result.success(borrowService.listMyBorrows());
    }

    public Result<List<BorrowRecordVO>> myRecordsBlockHandler(BlockException ex) {
        return Result.error(ErrorCode.SYSTEM_BUSY, "查询过于频繁，请稍后再试");
    }
}