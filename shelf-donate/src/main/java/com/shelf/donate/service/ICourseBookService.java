package com.shelf.donate.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shelf.donate.entity.CourseBook;

import java.util.List;

public interface ICourseBookService extends IService<CourseBook> {

    List<String> listMajors(Integer grade);

    List<String> listCourses(Integer grade, String major);

    List<CourseBook> listBooks(Integer grade, String major, String courseName);
}