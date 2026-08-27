package com.shelf.donate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.donate.entity.CourseBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseBookMapper extends BaseMapper<CourseBook> {

    // 根据年级查专业列表
    @Select("SELECT DISTINCT major FROM course_book WHERE grade = #{grade} ORDER BY major")
    List<String> selectMajors(@Param("grade") Integer grade);

    // 根据年级+专业查课程列表
    @Select("SELECT DISTINCT course_name FROM course_book WHERE grade = #{grade} AND major = #{major} ORDER BY course_name")
    List<String> selectCourses(@Param("grade") Integer grade, @Param("major") String major);

    // 根据年级+专业+课程查教材列表（含ISBN、出版社等）
    @Select("SELECT * FROM course_book WHERE grade = #{grade} AND major = #{major} AND course_name = #{courseName}")
    List<CourseBook> selectBooks(@Param("grade") Integer grade,
                                 @Param("major") String major,
                                 @Param("courseName") String courseName);

    // 根据年级/专业查去重的 ISBN 列表（用于 book_sku 库存检索的年级/专业过滤）
    @Select("<script>" +
            "SELECT DISTINCT isbn FROM course_book WHERE 1=1" +
            "<if test='grade != null'> AND grade = #{grade}</if>" +
            "<if test='major != null and major != \"\"'> AND major = #{major}</if>" +
            "</script>")
    List<String> selectIsbns(@Param("grade") Integer grade, @Param("major") String major);
}