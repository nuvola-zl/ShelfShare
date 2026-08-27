package com.shelf.admin.task;

import com.shelf.admin.entity.HotBookDaily;
import com.shelf.admin.mapper.BorrowRecordMapper;
import com.shelf.admin.mapper.HotBookDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotBookStatsTask {

    private final BorrowRecordMapper borrowRecordMapper;
    private final HotBookDailyMapper hotBookDailyMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 默认展示近7天热门数据
     */
    private static final int DEFAULT_HOT_DAYS = 7;

    /**
     * 历史数据保留时长（天）：支持管理员回溯最近2年的同期数据
     */
    private static final int RETAIN_DAYS = 730;

    /**
     * 每天凌晨 2 点，刷新热门教材排行
     * 【优化前】
     *   - 每天全量扫描 borrow_record 近30天数据，SQL 扫描行数随数据量线性增长
     *   - 直接 DEL 整个 Redis ZSet 再重建，瞬时排行榜为空
     * 【优化后】
     *   - 每天只聚合"昨天"的数据写入 hot_book_daily（增量日聚合）
     *   - 从预聚合表 SUM 近7天数据刷新 Redis（只需扫描几百行聚合记录）
     *   - 使用 ZADD 覆盖旧分数，避免 DEL 导致的瞬时空白
     *   - 清理2年前的旧聚合数据，控制表大小
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void refreshHotRank() {
        log.info("开始刷新热门教材排行");

        // 1. 聚合昨天的数据（T-1）
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();      // 2026-08-25 00:00:00
        LocalDateTime end = yesterday.plusDays(1).atStartOfDay(); // 2026-08-26 00:00:00
        List<Map<String, Object>> dailyStats = borrowRecordMapper.selectHotBookStatsByDateRange(start, end);

        // 2. 写入/更新预聚合表（先删昨天旧数据，再插入新数据，保证幂等）
        hotBookDailyMapper.deleteByStatDate(yesterday);
        for (Map<String, Object> row : dailyStats) {
            String isbn = (String) row.get("isbn");
            long count = ((Number) row.get("cnt")).longValue();

            HotBookDaily daily = new HotBookDaily();
            daily.setStatDate(yesterday);
            daily.setIsbn(isbn);
            daily.setApplyCount((int) count);
            hotBookDailyMapper.insert(daily);
        }
        log.info("日聚合完成: date={}, books={}", yesterday, dailyStats.size());

        // 3. 从预聚合表读取近7天汇总，刷新 Redis（不 DEL，直接覆盖分数）
        List<Map<String, Object>> weekStats = hotBookDailyMapper.selectSumLastNDays(DEFAULT_HOT_DAYS);
        for (Map<String, Object> row : weekStats) {
            String isbn = (String) row.get("isbn");
            long totalCount = ((Number) row.get("total")).longValue();
            // 直接覆盖分数，避免 DEL 导致的空窗期
            redisTemplate.opsForZSet().add("book:hot:rank", isbn, totalCount);
        }
        log.info("Redis 近7天热门榜刷新完成，共 {} 条", weekStats.size());

        // 4. 【保留两年清理策略】删除2年前的旧聚合数据
        LocalDate expireDate = yesterday.minusDays(RETAIN_DAYS);
        int deleted = hotBookDailyMapper.deleteByStatDateBefore(expireDate);
        if (deleted > 0) {
            log.info("清理过期聚合数据: {} 条 (stat_date < {})", deleted, expireDate);
        }

        log.info("热门教材排行定时任务执行完毕");
    }
}