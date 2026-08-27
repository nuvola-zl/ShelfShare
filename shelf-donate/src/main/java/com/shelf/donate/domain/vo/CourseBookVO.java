package com.shelf.donate.domain.vo;

import lombok.Data;

@Data
public class CourseBookVO {
    private String isbn;
    private String courseName;
    private Integer isRequired;
}