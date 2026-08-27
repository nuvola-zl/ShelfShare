package com.shelf.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("idempotent_record")
public class IdempotentRecord {
    private Long id;
    private String requestId;
    private String bizType;
    private LocalDateTime createTime;
}