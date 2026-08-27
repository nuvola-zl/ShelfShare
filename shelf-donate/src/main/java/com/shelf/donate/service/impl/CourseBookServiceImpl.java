package com.shelf.donate.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shelf.donate.entity.CourseBook;
import com.shelf.donate.mapper.CourseBookMapper;
import com.shelf.donate.service.ICourseBookService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseBookServiceImpl extends ServiceImpl<CourseBookMapper, CourseBook> implements ICourseBookService {

    @Override
    public List<String> listMajors(Integer grade) {
        return baseMapper.selectMajors(grade);
    }

    @Override
    public List<String> listCourses(Integer grade, String major) {
        return baseMapper.selectCourses(grade, major);
    }

    // ← 补上这个
    @Override
    public List<CourseBook> listBooks(Integer grade, String major, String courseName) {
        return baseMapper.selectBooks(grade, major, courseName);
    }
}