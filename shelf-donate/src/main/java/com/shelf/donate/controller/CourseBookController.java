package com.shelf.donate.controller;

import com.shelf.common.result.Result;
import com.shelf.donate.entity.CourseBook;
import com.shelf.donate.service.ICourseBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/donate/course")
@RequiredArgsConstructor
public class CourseBookController {

    private final ICourseBookService courseBookService;

    // 查专业（入参年级）
    @GetMapping("/majors")
    public Result<List<String>> majors(@RequestParam Integer grade) {
        return Result.success(courseBookService.listMajors(grade));
    }

    // 查课程（入参年级+专业）
    @GetMapping("/courses")
    public Result<List<String>> courses(@RequestParam Integer grade, @RequestParam String major) {
        return Result.success(courseBookService.listCourses(grade, major));
    }

    // 查教材列表（入参年级+专业+课程）
    @GetMapping("/books")
    public Result<List<CourseBook>> books(@RequestParam Integer grade,
                                          @RequestParam String major,
                                          @RequestParam String courseName) {
        return Result.success(courseBookService.listBooks(grade, major, courseName));
    }
}