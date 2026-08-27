package com.shelf.donate.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.context.UserContext;
import com.shelf.common.exception.BusinessException;

import com.shelf.donate.config.RabbitMqConfig;
import com.shelf.donate.domain.dto.DonateInboundMessage;
import com.shelf.donate.domain.dto.DonateSubmitDTO;
import com.shelf.donate.domain.vo.DonateRecordVO;
import com.shelf.donate.entity.CourseBook;
import com.shelf.donate.entity.DonateRecord;
import com.shelf.donate.mapper.CourseBookMapper;
import com.shelf.donate.mapper.DonateRecordMapper;

import com.shelf.donate.service.IDonateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DonateServiceImpl extends ServiceImpl<DonateRecordMapper, DonateRecord> implements IDonateService {

    private final CourseBookMapper courseBookMapper;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public void donate(DonateSubmitDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }

        // 1. 幂等：同一 requestId 直接返回
        DonateRecord exist = baseMapper.selectOne(
                new LambdaQueryWrapper<DonateRecord>()
                        .eq(DonateRecord::getRequestId, dto.getRequestId()));
        if (exist != null) {
            log.info("捐赠幂等命中: requestId={}", dto.getRequestId());
            return;
        }

        // 2. 校验 ISBN 在预置目录中
        CourseBook cb = courseBookMapper.selectOne(
                new LambdaQueryWrapper<CourseBook>()
                        .eq(CourseBook::getIsbn, dto.getIsbn()));
        if (cb == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该教材不在预置目录中");
        }

        // 3. 保存捐赠记录（状态 SUBMITTED，还没真正入库）
        DonateRecord record = new DonateRecord();
        record.setRequestId(dto.getRequestId());
        record.setUserId(userId);
        record.setIsbn(dto.getIsbn());
        record.setTitle(dto.getTitle());
        record.setCoverImageUrl(dto.getCoverImageUrl());
        record.setInnerImageUrl(dto.getInnerImageUrl());
        record.setStatus("PENDING");
        baseMapper.insert(record);

        // 4. 发送 MQ，异步完成真正的入库（生成实体书、更新库存）
        DonateInboundMessage msg = new DonateInboundMessage();
        msg.setDonateRecordId(record.getId());
        msg.setRequestId(dto.getRequestId());
        msg.setIsbn(dto.getIsbn());
        msg.setTitle(dto.getTitle());
        msg.setAuthor(cb.getAuthor());      // 从目录反填
        msg.setPublisher(cb.getPublisher()); // 从目录反填
        msg.setEdition(cb.getEdition());    // ← 新增：从目录反填版次
        msg.setCoverImageUrl(dto.getCoverImageUrl());
        msg.setUserId(userId);

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.DONATE_EXCHANGE,
                RabbitMqConfig.DONATE_INBOUND_KEY,
                msg
        );

        log.info("捐赠提交成功，等待异步入库: donateRecordId={}, isbn={}", record.getId(), dto.getIsbn());
    }

    @Override
    public List<DonateRecordVO> listMyDonates() {
        Long userId = UserContext.getUserId();
        List<DonateRecord> list = baseMapper.selectList(
                new LambdaQueryWrapper<DonateRecord>()
                        .eq(DonateRecord::getUserId, userId)
                        .orderByDesc(DonateRecord::getCreateTime));
        return list.stream().map(r -> {
            DonateRecordVO vo = new DonateRecordVO();
            BeanUtil.copyProperties(r, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}