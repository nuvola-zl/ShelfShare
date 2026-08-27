// BorrowRecordMapper.java
package com.shelf.admin.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.admin.entity.BorrowRecord;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.shelf.admin.domain.vo.HotBookVO;

@Mapper
public interface BorrowRecordMapper extends BaseMapper<BorrowRecord> {
    @Select("SELECT isbn, MAX(book_title) as bookTitle, COUNT(*) as cnt FROM borrow_record GROUP BY isbn ORDER BY cnt DESC LIMIT #{limit}")
    List<HotBookVO> selectHotBooks(@Param("limit") int limit);

    @Select("SELECT isbn, COUNT(*) as cnt FROM borrow_record " +
            "WHERE borrow_time > DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "GROUP BY isbn ORDER BY cnt DESC LIMIT 50")
    List<Map<String, Object>> selectHotBookStats(@Param("days") int days);

    /**
     * 按日期统计当天各 ISBN 的申领次数
     * 【注意】避免 DATE(create_time) 导致索引失效，改用范围查询
     */
    @Select("SELECT isbn, COUNT(*) as cnt " +
            "FROM borrow_record " +
            "WHERE create_time >= #{startTime} AND create_time < #{endTime} " +
            "GROUP BY isbn")
    List<Map<String, Object>> selectHotBookStatsByDateRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}