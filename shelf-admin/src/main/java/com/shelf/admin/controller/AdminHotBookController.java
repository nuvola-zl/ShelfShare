package com.shelf.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shelf.admin.domain.vo.HotBookVO;
import com.shelf.admin.entity.BookSku;
import com.shelf.admin.mapper.BookSkuMapper;
import com.shelf.admin.mapper.HotBookDailyMapper;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.exception.BusinessException;
import com.shelf.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class AdminHotBookController {

    private final StringRedisTemplate redisTemplate;
    private final BookSkuMapper bookSkuMapper;
    private final HotBookDailyMapper hotBookDailyMapper;

    /**
     * 手动预热某教材（管理员提前把热门书加载到 Redis）
     */
    @PostMapping("/book/preheat/{isbn}")
    public Result<Void> preheatBook(@PathVariable String isbn) {
        BookSku sku = bookSkuMapper.selectOne(
                new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, isbn));
        if (sku == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "ISBN 不存在");
        }

        redisTemplate.opsForValue().set("stock:" + isbn, String.valueOf(sku.getAvailableStock()));
        redisTemplate.opsForHash().put("book:info:" + isbn, "title", sku.getTitle());
        redisTemplate.opsForSet().add("book:hot:set", isbn);

        return Result.success();
    }

    /**
     * 查询热门教材排行 TOP N（默认近7天，走 Redis 缓存）
     * 【优化点】
     * 1. Redis 查询：由逐条 get 改为 Pipeline 批量读取，N 次 RTT → 1 次 RTT
     * 2. 缓存 miss：由循环单条查库改为批量 IN 查询，N 次单条 SQL → 1 次批量 SQL
     */
    /**
     * 查询热门教材排行 TOP N（默认近7天，走 Redis 缓存）
     */
    @GetMapping("/book/hot")
    public Result<List<HotBookVO>> hotBooks(
            @RequestParam(defaultValue = "10") int limit) {

        Set<ZSetOperations.TypedTuple<String>> set = redisTemplate.opsForZSet()
                .reverseRangeWithScores("book:hot:rank", 0, limit - 1);

        if (set == null || set.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<String> isbnList = new ArrayList<>(set.size());
        Map<String, Double> scoreMap = new HashMap<>(set.size());
        for (ZSetOperations.TypedTuple<String> tuple : set) {
            String isbn = tuple.getValue();
            Double score = tuple.getScore();
            if (isbn != null) {
                isbnList.add(isbn);
                scoreMap.put(isbn, score);
            }
        }

        // ========== 【优化前】逐条查询 Redis，N+1 问题 ==========
        // for (String isbn : isbnList) {
        //     String title = (String) redisTemplate.opsForHash()
        //             .get("book:info:" + isbn, "title");
        //     if (title == null) {
        //         BookSku sku = bookSkuMapper.selectOne(...);
        //     }
        // }

        // 批量查 DB 补全书名和库存（1 次 IN 查询）
        List<BookSku> skus = bookSkuMapper.selectList(
                new LambdaQueryWrapper<BookSku>()
                        .in(BookSku::getIsbn, isbnList));

        Map<String, BookSku> skuMap = new HashMap<>();
        for (BookSku sku : skus) {
            skuMap.put(sku.getIsbn(), sku);
        }

        List<HotBookVO> list = new ArrayList<>();
        for (String isbn : isbnList) {
            BookSku sku = skuMap.get(isbn);
            HotBookVO vo = new HotBookVO();
            vo.setIsbn(isbn);
            vo.setTitle(sku != null ? sku.getTitle() : "未知教材");
            vo.setApplyCount(scoreMap.getOrDefault(isbn, 0.0).intValue());
            vo.setAvailableStock(sku != null ? sku.getAvailableStock() : 0);
            vo.setTotalStock(sku != null ? sku.getTotalStock() : 0);
            list.add(vo);
        }
        return Result.success(list);
    }

    /**
     * 历史月份热门教材排行（管理员按年月筛选，走 MySQL 预聚合表）
     * 不走 Redis，因为管理员选择的历史月份是随机的，缓存命中率极低
     */
    @GetMapping("/book/hot/history")
    public Result<List<HotBookVO>> hotBooksByMonth(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(defaultValue = "10") int limit) {

        if (year < 2020 || year > 2100 || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "年月参数不合法");
        }

        YearMonth ym = YearMonth.of(year, month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        List<Map<String, Object>> stats = hotBookDailyMapper.selectSumByMonth(startDate, endDate, limit);

        List<String> isbnList = new ArrayList<>();
        Map<String, Long> countMap = new HashMap<>();
        for (Map<String, Object> row : stats) {
            String isbn = (String) row.get("isbn");
            long total = ((Number) row.get("total")).longValue();
            isbnList.add(isbn);
            countMap.put(isbn, total);
        }

        // 批量查 DB 补全书名和库存
        Map<String, BookSku> skuMap = new HashMap<>();
        if (!isbnList.isEmpty()) {
            List<BookSku> skus = bookSkuMapper.selectList(
                    new LambdaQueryWrapper<BookSku>()
                            .in(BookSku::getIsbn, isbnList));
            for (BookSku sku : skus) {
                skuMap.put(sku.getIsbn(), sku);
            }
        }

        List<HotBookVO> list = new ArrayList<>();
        for (String isbn : isbnList) {
            BookSku sku = skuMap.get(isbn);
            HotBookVO vo = new HotBookVO();
            vo.setIsbn(isbn);
            vo.setTitle(sku != null ? sku.getTitle() : "未知教材");
            vo.setApplyCount(countMap.getOrDefault(isbn, 0L).intValue());
            vo.setAvailableStock(sku != null ? sku.getAvailableStock() : 0);
            vo.setTotalStock(sku != null ? sku.getTotalStock() : 0);
            list.add(vo);
        }

        return Result.success(list);
    }
}