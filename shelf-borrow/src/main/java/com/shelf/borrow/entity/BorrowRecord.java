package com.shelf.borrow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("borrow_record")
public class BorrowRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String recordNo;

    private String requestId;

    private Long userId;

    private Long instanceId;

    private String isbn;

    private String bookTitle;

    private String instanceCode;   // ← 新增：实体书编码冗余

    private String location;       // ← 新增：存放位置冗余

    private String status;

    private LocalDateTime borrowTime;

    private LocalDateTime pickupDeadline;

    private LocalDateTime pickupTime;

    private LocalDateTime dueDate;

    private LocalDateTime returnTime;

    private Integer overdueDays;

    private String cancelReason;

    private LocalDateTime remindTime;

//    private String qrCode;//todo目前还不需要编码

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}