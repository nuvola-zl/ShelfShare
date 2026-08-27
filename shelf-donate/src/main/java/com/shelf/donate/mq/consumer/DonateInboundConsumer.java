package com.shelf.donate.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import com.shelf.donate.config.RabbitMqConfig;
import com.shelf.donate.domain.dto.DonateInboundMessage;
import com.shelf.donate.entity.BookInstance;
import com.shelf.donate.entity.BookSku;
import com.shelf.donate.entity.DonateRecord;
import com.shelf.donate.mapper.BookInstanceMapper;
import com.shelf.donate.mapper.BookSkuMapper;
import com.shelf.donate.mapper.DonateRecordMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DonateInboundConsumer {

    private final DonateRecordMapper donateRecordMapper;
    private final BookInstanceMapper bookInstanceMapper;
    private final BookSkuMapper bookSkuMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = "donate.inbound.queue")
    public void handle(DonateInboundMessage msg, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("收到异步入库消息: donateRecordId={}, isbn={}", msg.getDonateRecordId(), msg.getIsbn());

            // 幂等：已处理过（ACCEPTED 或 REJECTED）直接 ACK
            DonateRecord record = donateRecordMapper.selectById(msg.getDonateRecordId());
            if (record == null || "ACCEPTED".equals(record.getStatus()) || "REJECTED".equals(record.getStatus())) {
                log.info("捐赠入库幂等跳过: donateRecordId={}, status={}", msg.getDonateRecordId(),
                        record != null ? record.getStatus() : "null");
                channel.basicAck(deliveryTag, false);
                return;
            }

            String instanceCode = null;
            BookInstance instance = null;

            // ← 加上这一行
            String month = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));

            // 按月份加分布式锁...
            org.redisson.api.RLock lock = redissonClient.getLock("donate:code:" + month);
            lock.lock();
            try {
                // 1. 生成实体书编码
                instanceCode = generateInstanceCode();

                // 2. 创建实体书
                instance = new BookInstance();
                instance.setInstanceCode(instanceCode);

                instance.setIsbn(msg.getIsbn());
                instance.setStatus("AVAILABLE");
                instance.setLocation("主书库");
                instance.setReservedBy(0L);        // ← 补全，数据库 NOT NULL DEFAULT 0
                instance.setDamagedReason("");     // ← 补全，数据库 NOT NULL DEFAULT ''
                bookInstanceMapper.insert(instance);

            // 3. 创建或更新 SKU（已有 SKU 走乐观锁）
            BookSku sku = bookSkuMapper.selectOne(
                    new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, msg.getIsbn()));
            if (sku == null) {
                sku = new BookSku();
                sku.setIsbn(msg.getIsbn());
                sku.setTitle(msg.getTitle());
                sku.setAuthor(msg.getAuthor());
                sku.setPublisher(msg.getPublisher());
                sku.setEdition(msg.getEdition());
                sku.setCoverImage(msg.getCoverImageUrl());
                sku.setTotalStock(1);
                sku.setAvailableStock(1);
                sku.setVersion(0);
                bookSkuMapper.insert(sku);
            } else {
                int updated = bookSkuMapper.update(null,
                        new UpdateWrapper<BookSku>()
                                .eq("id", sku.getId())
                                .eq("version", sku.getVersion())
                                .setSql("total_stock = total_stock + 1")
                                .setSql("available_stock = available_stock + 1")
                                .setSql("version = version + 1"));
                if (updated == 0) {
                    throw new RuntimeException("SKU库存更新冲突，isbn=" + msg.getIsbn());
                }
            }

            // 4. 回填捐赠记录
            record.setInstanceId(instance.getId());
            record.setStatus("ACCEPTED");
            donateRecordMapper.updateById(record);

            } finally {
                lock.unlock();
            }

            // 5. ACK 确认（此时 Spring 事务已提交）
            channel.basicAck(deliveryTag, false);

            // 6. 【事务外】Redis 预热（失败不影响主流程）
            try {
                redisTemplate.opsForValue().increment("stock:" + msg.getIsbn());
                redisTemplate.opsForHash().put("book:info:" + msg.getIsbn(), "title", msg.getTitle());
                redisTemplate.opsForHash().put("book:info:" + msg.getIsbn(), "author", msg.getAuthor());

                // 热门标记检查
                Double applyCount = redisTemplate.opsForZSet().score("book:hot:rank", msg.getIsbn());
                if (applyCount != null && applyCount >= 10) {
                    redisTemplate.opsForSet().add("book:hot:set", msg.getIsbn());
                }
            } catch (Exception redisEx) {
                log.error("捐赠 Redis 预热失败（不影响主流程）: isbn={}, error={}",
                        msg.getIsbn(), redisEx.getMessage());
                // 管理员可手动预热补偿
            }

            log.info("异步入库完成: instanceCode={}, isbn={}", instanceCode, msg.getIsbn());

        } catch (Exception e) {
            log.error("异步入库失败: donateRecordId={}, isbn={}, error={}",
                    msg.getDonateRecordId(), msg.getIsbn(), e.getMessage(), e);
            // 业务异常直接进死信，不再重试（避免无限重试堵死队列）
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 从数据库查当月最大序号生成编码，重启/关机不丢
     */
    private String generateInstanceCode() {
        String month = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        Long maxSeq = bookInstanceMapper.selectMaxSeqByMonth(month);
        long nextSeq = (maxSeq == null ? 0 : maxSeq) + 1;
        return String.format("BOOK-%s-%05d", month, nextSeq);
    }
}