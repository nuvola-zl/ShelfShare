package com.shelf.borrow.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;

import com.shelf.api.dto.donate.BookInstanceDTO;
import com.shelf.api.dto.donate.DeductStockRequest;
import com.shelf.api.dto.donate.ReleaseStockRequest;
import com.shelf.api.feign.donate.DonateFeignApi;
import com.shelf.borrow.domain.dto.BorrowApplyMessage;
import com.shelf.borrow.entity.BorrowRecord;
import com.shelf.borrow.mapper.BorrowRecordMapper;

import com.shelf.borrow.service.DonateStockService;
import com.shelf.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowApplyConsumer {

    private final BorrowRecordMapper borrowRecordMapper;
    private final DonateFeignApi donateFeignApi;
    private final DonateStockService donateStockService;  // 【新增】

    private final StringRedisTemplate redisTemplate;

    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = "borrow.apply.queue")
    public void handle(BorrowApplyMessage msg, Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("收到申领处理消息: recordNo={}, isbn={}", msg.getRecordNo(), msg.getIsbn());

            // 幂等：DB 是否已有
            BorrowRecord exist = borrowRecordMapper.selectOne(
                    new LambdaQueryWrapper<BorrowRecord>()
                            .eq(BorrowRecord::getRequestId, msg.getRequestId()));
            if (exist != null) {
                log.info("申领已处理，幂等跳过: requestId={}", msg.getRequestId());
                redisTemplate.delete("borrow:pending:" + msg.getRecordNo());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 1. 调 donate 扣 DB 库存（分配实体书）
            // 原来直接调 donateFeignApi，现在走熔断保护
            DeductStockRequest deductReq = new DeductStockRequest();
            deductReq.setIsbn(msg.getIsbn());
            deductReq.setUserId(msg.getUserId());

            BookInstanceDTO instance = donateStockService.deductStock(deductReq);
            // 如果熔断或调用失败，上面会直接抛异常，走到下面的 catch

            try {
                // 2. 生成本地借阅记录
                BorrowRecord record = new BorrowRecord();
                record.setRecordNo(msg.getRecordNo());
                record.setRequestId(msg.getRequestId());
                record.setUserId(msg.getUserId());
                record.setInstanceId(instance.getInstanceId());
                record.setIsbn(msg.getIsbn());
                record.setBookTitle(instance.getTitle());
                record.setInstanceCode(instance.getInstanceCode());
                record.setLocation(instance.getLocation());
                record.setStatus("PENDING_PICKUP");
                record.setBorrowTime(LocalDateTime.now());
                record.setPickupDeadline(LocalDateTime.now().plusDays(7));
                //record.setQrCode(Base64.getEncoder().encodeToString(msg.getRecordNo().getBytes()));////todo目前还不需要编码
                borrowRecordMapper.insert(record);

                // 3. 【注意】额度在 apply() 同步阶段已占用，此处不再重复增加

                // 4. 删 Redis 临时记录
                redisTemplate.delete("borrow:pending:" + msg.getRecordNo());

                // 5. 热门排行 +1
                redisTemplate.opsForZSet().incrementScore("book:hot:rank", msg.getIsbn(), 1);

                log.info("申领异步处理完成: recordNo={}, instanceCode={}", msg.getRecordNo(), instance.getInstanceCode());
                channel.basicAck(deliveryTag, false);

            } catch (Exception e) {
                // 本地失败，删除已插入的 borrow_record 并释放 DB 库存
                log.error("生成借阅记录失败，补偿释放库存: instanceId={}", instance.getInstanceId());

                // 删除可能已插入的记录（按 recordNo 删，避免 insert 失败时 id 为 null）
                borrowRecordMapper.delete(
                        new LambdaQueryWrapper<BorrowRecord>()
                                .eq(BorrowRecord::getRecordNo, msg.getRecordNo()));

                ReleaseStockRequest releaseReq = new ReleaseStockRequest();
                releaseReq.setInstanceId(instance.getInstanceId());
                releaseReq.setIsbn(msg.getIsbn());
                Result<Void> releaseResult = donateFeignApi.releaseStock(releaseReq);
                if (!releaseResult.isSuccess()) {
                    log.error("MQ补偿释放库存失败: instanceId={}, isbn={}, code={}, msg={}",
                            instance.getInstanceId(), msg.getIsbn(), releaseResult.getCode(), releaseResult.getMsg());
                }
                // 不管补偿成功还是失败，都要抛异常让 MQ 知道这条消息处理失败了
                throw e; // 抛出去让 MQ 重试（或进死信）
            }

        } catch (Exception e) {
            log.error("申领处理失败: recordNo={}, error={}", msg.getRecordNo(), e.getMessage());
            // 业务异常（如库存不足）不要无限重试，直接进死信
            // 系统异常（如网络抖动）可以重试，由 RabbitMQ 配置决定
            channel.basicNack(deliveryTag, false, false);
        }
    }
}