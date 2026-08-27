package com.shelf.donate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dead_letter_record")
public class DeadLetterRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String type;

    private String bizType;

    private String bizId;

    private Long userId;

    private String errorMsg;

    private String context;

    private Integer status;

    private String resolveRemark;

    private LocalDateTime resolveTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}