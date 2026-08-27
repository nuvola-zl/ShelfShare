package com.shelf.donate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("course_book")
public class CourseBook {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer grade;

    private String major;

    private String courseName;

    private String isbn;

    private String author;        // ← 加上

    private String publisher;     // ← 加上

    private Integer isRequired;

    private String edition;      // ← 新增：版次
}