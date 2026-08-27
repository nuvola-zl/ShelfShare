package com.shelf.donate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("book_sku")
public class BookSku {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String isbn;

    private String title;

    private String author;

    private String publisher;

    private String coverImage;

    private Integer totalStock;

    private Integer availableStock;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String edition;      // ← 新增：版次
}