package com.shelf.borrow.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class BorrowRabbitMqConfig {

    public static final String BORROW_EXCHANGE = "shelf.borrow.exchange";
    public static final String BORROW_APPLY_QUEUE = "borrow.apply.queue";
    public static final String BORROW_APPLY_KEY = "borrow.apply";

    // 死信交换机和队列
    public static final String BORROW_DLX_EXCHANGE = "shelf.borrow.dlx.exchange";
    public static final String BORROW_DLX_QUEUE = "borrow.apply.dlx.queue";
    public static final String BORROW_DLX_KEY = "borrow.apply.dlx";

    @Bean
    public DirectExchange borrowExchange() {
        return new DirectExchange(BORROW_EXCHANGE, true, false);
    }

    @Bean
    public Queue borrowApplyQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", BORROW_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", BORROW_DLX_KEY);
        return new Queue(BORROW_APPLY_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding borrowApplyBinding() {
        return BindingBuilder.bind(borrowApplyQueue())
                .to(borrowExchange())
                .with(BORROW_APPLY_KEY);
    }

    @Bean
    public DirectExchange borrowDlxExchange() {
        return new DirectExchange(BORROW_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue borrowApplyDlxQueue() {
        return new Queue(BORROW_DLX_QUEUE, true);
    }

    @Bean
    public Binding borrowApplyDlxBinding() {
        return BindingBuilder.bind(borrowApplyDlxQueue())
                .to(borrowDlxExchange())
                .with(BORROW_DLX_KEY);
    }
}