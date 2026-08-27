package com.shelf.donate.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.rabbitmq.client.Channel;
import com.shelf.donate.domain.dto.DonateInboundMessage;
import com.shelf.donate.entity.DeadLetterRecord;
import com.shelf.donate.mapper.DeadLetterRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 捐赠死信消费者
 * 当 donate.inbound.queue 中的消息消费失败进入死信队列后，
 * 将失败信息持久化到 dead_letter_record 表，供管理员人工兜底处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DonateDeadLetterConsumer {

    private final DeadLetterRecordMapper deadLetterRecordMapper;

    @RabbitListener(queues = "donate.inbound.dlx.queue")
    public void handleDeadLetter(DonateInboundMessage msg, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.error("【死信】捐赠入库消息进入死信队列: donateRecordId={}, isbn={}, requestId={}",
                    msg.getDonateRecordId(), msg.getIsbn(), msg.getRequestId());

            // 组装上下文快照
            Map<String, Object> context = new HashMap<>();
            context.put("isbn", msg.getIsbn());
            context.put("title", msg.getTitle());
            context.put("userId", msg.getUserId());
            context.put("deadLetterTime", LocalDateTime.now().toString());

            // 持久化到死信记录表
            DeadLetterRecord record = new DeadLetterRecord();
            record.setType("DONATE_INBOUND_FAIL");
            record.setBizType("DONATE");
            record.setBizId(msg.getRequestId());
            record.setUserId(msg.getUserId());
            record.setErrorMsg("捐赠异步入库多次失败，进入死信队列");
            record.setContext(JSON.toJSONString(context));
            record.setStatus(0); // 0=待处理
            deadLetterRecordMapper.insert(record);

            log.info("捐赠死信记录已持久化: id={}", record.getId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("捐赠死信消费处理失败: donateRecordId={}, error={}", msg.getDonateRecordId(), e.getMessage());
            // 死信消费也失败，直接 ACK 避免死信队列堆积
            channel.basicAck(deliveryTag, false);
        }
    }
}