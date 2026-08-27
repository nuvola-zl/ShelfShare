package com.shelf.user;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.shelf.user.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
@AutoConfigureMockMvc
public class LoginFlowLimitTest {

    @Autowired
    private MockMvc mockMvc;

    // ========== 关键：mock 掉登录业务，绕过账号密码校验 ==========
    @MockBean
    private IUserService userService;

    @BeforeEach
    void setUp() {
        // mock 登录直接返回成功
        when(userService.login(any())).thenReturn(new com.shelf.user.domain.vo.UserLoginVO());

        List<FlowRule> rules = new ArrayList<>();
        FlowRule rule = new FlowRule();
        rule.setResource("userLogin");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(3);
        rule.setLimitApp("default");
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }

    @Test
    void testLoginFlowLimit() throws InterruptedException {
        int total = 10;
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(total);

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger blocked = new AtomicInteger(0);

        String json = "{\"studentNo\":\"2024001\",\"password\":\"123456\"}";

        for (int i = 0; i < total; i++) {
            executor.execute(() -> {
                try {
                    // ========== 去掉 isOk() 断言，直接拿结果判断 ==========
                    MvcResult result = mockMvc.perform(post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                            .andReturn();

                    String content = result.getResponse().getContentAsString();
                    if (content.contains("A00005") || content.contains("请稍后再试") || content.contains("过于频繁")) {
                        blocked.incrementAndGet();
                    } else {
                        success.incrementAndGet();
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

        System.out.println("================ 登录限流测试结果 ================");
        System.out.println("总请求数: " + total);
        System.out.println("正常通过数: " + success.get());
        System.out.println("被限流拦截数: " + blocked.get());
        System.out.println("================================================");

        assertTrue(blocked.get() > 0, "限流未生效，所有请求都通过了！");
    }
}