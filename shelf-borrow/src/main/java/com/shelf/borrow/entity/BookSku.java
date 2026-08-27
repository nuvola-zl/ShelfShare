package com.shelf.borrow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("book_sku")
public class BookSku {
    private Long id;
    private String isbn;
    private Integer availableStock;
    private String edition;      // ← 新增
}