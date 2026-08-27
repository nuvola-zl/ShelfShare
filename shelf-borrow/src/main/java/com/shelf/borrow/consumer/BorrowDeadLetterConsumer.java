package com.shelf.borrow.consumer;

import com.alibaba.fastjson2.JSON;
import com.rabbitmq.client.Channel;
import com.shelf.borrow.domain.dto.BorrowApplyMessage;
import com.shelf.borrow.entity.DeadLetterRecord;
import com.shelf.borrow.mapper.DeadLetterRecordMapper;
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
 * 申领死信消费者
 * 当 borrow.apply.queue 中的消息消费失败进入死信队列后，
 * 将失败信息持久化到 dead_letter_record 表，供管理员人工兜底处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowDeadLetterConsumer {

    private final DeadLetterRecordMapper deadLetterRecordMapper;

    @RabbitListener(queues = "borrow.apply.dlx.queue")
    public void handleDeadLetter(BorrowApplyMessage msg, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.error("【死信】申领消息进入死信队列: recordNo={}, isbn={}, requestId={}, userId={}",
                    msg.getRecordNo(), msg.getIsbn(), msg.getRequestId(), msg.getUserId());

            // 组装上下文快照
            Map<String, Object> context = new HashMap<>();
            context.put("isbn", msg.getIsbn());
            context.put("userId", msg.getUserId());
            context.put("requestId", msg.getRequestId());
            context.put("deadLetterTime", LocalDateTime.now().toString());

            // 持久化到死信记录表
            DeadLetterRecord record = new DeadLetterRecord();
            record.setType("INVENTORY_MISMATCH");
            record.setBizType("BORROW");
            record.setBizId(msg.getRecordNo());
            record.setUserId(msg.getUserId());
            record.setErrorMsg("申领 MQ 消费多次失败，进入死信队列");
            record.setContext(JSON.toJSONString(context));
            record.setStatus(0); // 0=待处理
            deadLetterRecordMapper.insert(record);

            log.info("死信记录已持久化: id={}", record.getId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("死信消费处理失败: recordNo={}, error={}", msg.getRecordNo(), e.getMessage());
            // 如果死信消费也失败，不要无限重试，直接 ACK 掉避免死信队列堆积
            channel.basicAck(deliveryTag, false);
        }
    }
}