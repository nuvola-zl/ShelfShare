package com.shelf.donate.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    public static final String DONATE_EXCHANGE = "shelf.donate.exchange";
    public static final String DONATE_INBOUND_QUEUE = "donate.inbound.queue";
    public static final String DONATE_INBOUND_KEY = "donate.inbound";

    // 死信交换机和队列
    public static final String DONATE_DLX_EXCHANGE = "shelf.donate.dlx.exchange";
    public static final String DONATE_DLX_QUEUE = "donate.inbound.dlx.queue";
    public static final String DONATE_DLX_KEY = "donate.inbound.dlx";

    @Bean
    public DirectExchange donateExchange() {
        return new DirectExchange(DONATE_EXCHANGE, true, false);
    }

    @Bean
    public Queue donateInboundQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DONATE_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", DONATE_DLX_KEY);
        return new Queue(DONATE_INBOUND_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding donateInboundBinding() {
        return BindingBuilder.bind(donateInboundQueue())
                .to(donateExchange())
                .with(DONATE_INBOUND_KEY);
    }

    @Bean
    public DirectExchange donateDlxExchange() {
        return new DirectExchange(DONATE_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue donateInboundDlxQueue() {
        return new Queue(DONATE_DLX_QUEUE, true);
    }

    @Bean
    public Binding donateInboundDlxBinding() {
        return BindingBuilder.bind(donateInboundDlxQueue())
                .to(donateDlxExchange())
                .with(DONATE_DLX_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}