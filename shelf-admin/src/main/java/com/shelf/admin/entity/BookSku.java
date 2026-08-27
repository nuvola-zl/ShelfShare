package com.shelf.admin.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("book_sku")
public class BookSku {
    @TableId(type = IdType.AUTO) private Long id;
    private String isbn;
    private String title;
    private String author;           // ← 补上
    private String publisher;        // ← 补上
    private String coverImage;       // ← 补上
    private Integer totalStock;      // ← 补上
    private Integer availableStock;
    private Integer version;
    private String edition;          // ← 补上
    private LocalDateTime createTime;
    private LocalDateTime updateTime; // ← 补上
}