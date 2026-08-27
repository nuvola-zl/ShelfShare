package com.shelf.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("borrow_record")
public class BorrowRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private String recordNo;
    private String requestId;      // ← 新增：幂等键（admin 查询时可能需要展示）
    private Long userId;
    private Long instanceId;
    private String isbn;
    private String bookTitle;
    private String instanceCode;   // ← 新增：实体书编码冗余
    private String location;       // ← 新增：存放位置冗余
    private String status;
    private LocalDateTime borrowTime;
    private LocalDateTime pickupDeadline;
    private LocalDateTime pickupTime;  // ← 新增：实际领取时间（pickupConfirm 用到）
    private LocalDateTime dueDate;
    private LocalDateTime returnTime;
    private LocalDateTime remindTime;  // ← 新增：到期提醒时间（定时任务用到）
    private Integer overdueDays;       // ← 新增：逾期天数
    private String cancelReason;       // ← 新增：取消原因
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}