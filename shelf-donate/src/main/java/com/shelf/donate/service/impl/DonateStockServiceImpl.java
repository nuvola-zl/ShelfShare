package com.shelf.donate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.shelf.api.dto.donate.BookInstanceDTO;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.exception.BusinessException;
import com.shelf.common.result.PageResult;
import com.shelf.donate.entity.BookInstance;
import com.shelf.donate.entity.BookSku;
import com.shelf.donate.mapper.BookInstanceMapper;
import com.shelf.donate.mapper.BookSkuMapper;
import com.shelf.donate.mapper.CourseBookMapper;
import com.shelf.donate.service.IDonateStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DonateStockServiceImpl implements IDonateStockService {

    private final BookSkuMapper bookSkuMapper;
    private final BookInstanceMapper bookInstanceMapper;
    private final CourseBookMapper courseBookMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookInstanceDTO deductStock(String isbn, Long userId) {
        // 1. 查 SKU 库存
        BookSku sku = bookSkuMapper.selectOne(
                new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, isbn));
        if (sku == null || sku.getAvailableStock() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "库存不足");
        }

        // 2. 乐观锁扣减可用库存
        int updated = bookSkuMapper.update(null,
                new UpdateWrapper<BookSku>()
                        .eq("id", sku.getId())
                        .eq("version", sku.getVersion())
                        .setSql("available_stock = available_stock - 1")
                        .setSql("version = version + 1"));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "库存扣减冲突，请重试");
        }

        // 3. FIFO 分配最早入库的实体书（改用 Mapper 自定义 SQL，避免 LIMIT 语法问题）
        BookInstance instance = bookInstanceMapper.selectAvailableForUpdate(isbn);

        if (instance == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "实体书分配失败，库存数据异常");
        }

        // 4. 改实体书状态为 RESERVED
        instance.setStatus("RESERVED");
        instance.setReservedBy(userId);
        bookInstanceMapper.updateById(instance);

        log.info("库存扣减成功: isbn={}, instanceCode={}", isbn, instance.getInstanceCode());

        // 5. 组装返回
        BookInstanceDTO dto = new BookInstanceDTO();
        dto.setInstanceId(instance.getId());
        dto.setInstanceCode(instance.getInstanceCode());
        dto.setIsbn(isbn);
        dto.setTitle(sku.getTitle());
        dto.setLocation(instance.getLocation());
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseStock(Long instanceId, String isbn) {
        BookInstance instance = bookInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("释放库存失败，实体书不存在: instanceId={}", instanceId);
            return;
        }
        if (!"RESERVED".equals(instance.getStatus())) {
            log.warn("释放库存幂等跳过，状态已为: {}", instance.getStatus());
            return;
        }

        instance.setStatus("AVAILABLE");
        bookInstanceMapper.updateById(instance);

        BookSku sku = bookSkuMapper.selectOne(
                new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, isbn));
        if (sku != null) {
            bookSkuMapper.update(null,
                    new UpdateWrapper<BookSku>()
                            .eq("id", sku.getId())
                            .setSql("available_stock = available_stock + 1")
                            .setSql("version = version + 1"));
        }

        log.info("库存释放成功: instanceId={}, isbn={}", instanceId, isbn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnStock(Long instanceId, String isbn) {
        BookInstance instance = bookInstanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("归还入库失败，实体书不存在: instanceId={}", instanceId);
            return;
        }

        if (!"BORROWED".equals(instance.getStatus()) && !"OVERDUE".equals(instance.getStatus())) {
            log.warn("归还入库幂等跳过，状态已为: {}", instance.getStatus());
            return;
        }

        instance.setStatus("AVAILABLE");
        bookInstanceMapper.updateById(instance);

        BookSku sku = bookSkuMapper.selectOne(
                new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, isbn));
        if (sku != null) {
            bookSkuMapper.update(null,
                    new UpdateWrapper<BookSku>()
                            .eq("id", sku.getId())
                            .setSql("available_stock = available_stock + 1")
                            .setSql("version = version + 1"));
        }

        log.info("归还入库成功: instanceId={}, isbn={}", instanceId, isbn);
    }

    @Override
    public PageResult<BookSku> searchSku(String keyword, Integer grade, String major, int page, int size) {
        LambdaQueryWrapper<BookSku> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(BookSku::getTitle, keyword)
                    .or()
                    .like(BookSku::getIsbn, keyword)
                    .or()
                    .like(BookSku::getAuthor, keyword)
                    .or()
                    .like(BookSku::getPublisher, keyword));
        }

        // 年级/专业过滤：通过 course_book 反查该年级/专业下的 ISBN 集合
        if (grade != null || (major != null && !major.isBlank())) {
            List<String> isbns = courseBookMapper.selectIsbns(grade, major);
            if (isbns.isEmpty()) {
                return PageResult.build(Collections.emptyList(), 0L, page, size);
            }
            wrapper.in(BookSku::getIsbn, isbns);
        }

        wrapper.orderByDesc(BookSku::getAvailableStock);

        Page<BookSku> pageParam = new Page<>(page, size);
        Page<BookSku> result = bookSkuMapper.selectPage(pageParam, wrapper);
        return PageResult.build(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }
}