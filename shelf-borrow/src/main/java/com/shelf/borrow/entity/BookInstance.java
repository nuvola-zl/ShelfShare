package com.shelf.borrow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("book_instance")
public class BookInstance {
    private Long id;
    private String instanceCode;
    private String isbn;
    private String status;
    private String location;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}