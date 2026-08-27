package com.shelf.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.admin.entity.HotBookDaily;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface HotBookDailyMapper extends BaseMapper<HotBookDaily> {

    /**
     * 删除指定日期的聚合记录（用于幂等：先删旧数据再插入新数据）
     */
    @Delete("DELETE FROM hot_book_daily WHERE stat_date = #{date}")
    int deleteByStatDate(@Param("date") LocalDate date);

    /**
     * 删除指定日期之前的所有记录（保留两年清理策略）
     */
    @Delete("DELETE FROM hot_book_daily WHERE stat_date < #{date}")
    int deleteByStatDateBefore(@Param("date") LocalDate date);

    /**
     * 查询近 N 天的热门汇总（用于刷新 Redis 默认榜）
     */
    @Select("SELECT isbn, SUM(apply_count) as total " +
            "FROM hot_book_daily " +
            "WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY isbn " +
            "ORDER BY total DESC")
    List<Map<String, Object>> selectSumLastNDays(@Param("days") int days);

    /**
     * 查询指定年月的热门汇总（用于管理员历史筛选）
     */
    @Select("SELECT isbn, SUM(apply_count) as total " +
            "FROM hot_book_daily " +
            "WHERE stat_date >= #{startDate} AND stat_date <= #{endDate} " +
            "GROUP BY isbn " +
            "ORDER BY total DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectSumByMonth(@Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate,
                                               @Param("limit") int limit);
}