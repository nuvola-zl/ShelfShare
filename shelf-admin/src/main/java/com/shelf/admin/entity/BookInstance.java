package com.shelf.admin.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("book_instance")
public class BookInstance {
    @TableId(type = IdType.AUTO) private Long id;
    private String instanceCode;
    private String isbn;
    private String status;
    private String location;           // ← 补上
    private Long reservedBy = 0L;    // ← 补上，数据库 NOT NULL DEFAULT 0
    private LocalDateTime reservedTime; // ← 补上
    private String damagedReason = "";  // ← 补上，数据库 NOT NULL DEFAULT ''
    private LocalDateTime createTime;
    private LocalDateTime updateTime;   // ← 补上
}