package com.shelf.donate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("book_instance")
public class BookInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String instanceCode;

    private String isbn;

    private String status;

    private String location;

    private Long reservedBy = 0L;          // ← 加上

    private LocalDateTime reservedTime;  // ← 加上

    private String damagedReason = "";   // ← 加上

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}