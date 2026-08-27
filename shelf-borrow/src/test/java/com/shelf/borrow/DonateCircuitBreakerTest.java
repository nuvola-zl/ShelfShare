package com.shelf.borrow;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.shelf.api.dto.donate.DeductStockRequest;
import com.shelf.api.feign.donate.DonateFeignApi;
import com.shelf.borrow.service.DonateStockService;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        // 禁用 Nacos 服务注册（防止启动时连 192.168.153.129:8848）
        "spring.cloud.nacos.discovery.enabled=false",
        // 禁用 RabbitMQ 监听器自动启动（防止连 192.168.153.129:5672）
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
public class DonateCircuitBreakerTest {

    @Autowired
    private DonateStockService donateStockService;

    @MockBean
    private DonateFeignApi donateFeignApi;

    // 启动时会连 Redis，mock 掉
    @MockBean
    private RedissonClient redissonClient;

//    // 启动时会连 MySQL，mock 掉（如果你本地 MySQL 没开的话）
//    @MockBean
//    private DataSource dataSource;

    // 防止 RabbitMQ 相关 bean 创建时出问题
    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        List<DegradeRule> rules = new ArrayList<>();
        DegradeRule rule = new DegradeRule();
        rule.setResource("deductStock");
        rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        rule.setCount(0.5);
        rule.setTimeWindow(30);
        rule.setMinRequestAmount(2);
        rule.setStatIntervalMs(1000);
        rules.add(rule);
        DegradeRuleManager.loadRules(rules);
    }

    @Test
    void testCircuitBreaker() {
        DeductStockRequest req = new DeductStockRequest();
        req.setIsbn("978-7-111-54493-7");
        req.setUserId(12345L);

        when(donateFeignApi.deductStock(any())).thenReturn(
                Result.error(ErrorCode.REMOTE_CALL_ERROR, "服务不可用")
        );

        RuntimeException ex1 = assertThrows(RuntimeException.class, () -> {
            donateStockService.deductStock(req);
        });
        System.out.println("第1次异常: " + ex1.getMessage());

        RuntimeException ex2 = assertThrows(RuntimeException.class, () -> {
            donateStockService.deductStock(req);
        });
        System.out.println("第2次异常: " + ex2.getMessage());

        long start = System.currentTimeMillis();
        RuntimeException ex3 = assertThrows(RuntimeException.class, () -> {
            donateStockService.deductStock(req);
        });
        long cost = System.currentTimeMillis() - start;

        System.out.println("第3次异常: " + ex3.getMessage());
        System.out.println("第3次耗时: " + cost + "ms");

        assertTrue(ex3.getMessage().contains("熔断保护") || ex3.getMessage().contains("暂不可用"),
                "熔断后应该走 blockHandler");
        assertTrue(cost < 100, "熔断后应该直接短路，不应等待超时");
    }
}