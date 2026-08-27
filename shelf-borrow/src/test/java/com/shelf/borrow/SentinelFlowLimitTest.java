package com.shelf.borrow;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SentinelFlowLimitTest {

    @Autowired
    private MockMvc mockMvc;

    private String testToken;

    @BeforeEach
    void setUp() {
        // ========== 模拟登录，让 @SaCheckLogin 通过 ==========
        // 不需要真实账号！99999 是随便写的测试用户ID
        StpUtil.login(99999);
        testToken = StpUtil.getTokenValue();
        System.out.println("测试 token: " + testToken);

        // 加载限流规则
        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        rule.setResource("borrowMyRecords");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(5);
        rule.setLimitApp("default");
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }

    @Test
    void testMyRecordsFlowLimit() throws InterruptedException {
        int totalRequests = 20;
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger blockCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.execute(() -> {
                try {
                    // ========== 注意：header 名是 Authorization，和你的配置一致 ==========
                    MvcResult result = mockMvc.perform(get("/api/borrow/my-records")
                                    .header("Authorization", testToken))
                            .andExpect(status().isOk())
                            .andReturn();

                    String content = result.getResponse().getContentAsString();
                    // 根据你的 ErrorCode 调整判断条件
                    if (content.contains("A00005") || content.contains("请稍后再试") || content.contains("过于频繁")) {
                        blockCount.incrementAndGet();
                    } else {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("================ 借阅查询限流测试结果 ================");
        System.out.println("总请求数: " + totalRequests);
        System.out.println("正常通过数: " + successCount.get());
        System.out.println("被限流拦截数: " + blockCount.get());
        System.out.println("====================================================");

        assert blockCount.get() > 0 : "限流未生效，所有请求都通过了！";
    }
}