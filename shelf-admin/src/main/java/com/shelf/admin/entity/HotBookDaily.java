package com.shelf.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("hot_book_daily")
public class HotBookDaily {

    @TableId
    private LocalDate statDate;

    private String isbn;

    private Integer applyCount;

    private LocalDateTime createTime;
}