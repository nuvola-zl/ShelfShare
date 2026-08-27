package com.shelf.admin.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dead_letter_record")
public class DeadLetterRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private String type;
    private String bizType;
    private String bizId;
    private Long userId;
    private String errorMsg;
    private String context;           // ← 补上（JSON字段）
    private Integer status;
    private String resolveRemark;
    private LocalDateTime resolveTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime; // ← 补上
}