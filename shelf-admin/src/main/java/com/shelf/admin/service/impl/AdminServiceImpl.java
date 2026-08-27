package com.shelf.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shelf.admin.domain.dto.CalibrateStockDTO;
import com.shelf.admin.domain.dto.MarkDamagedDTO;
import com.shelf.admin.domain.dto.PendingBorrowDTO;
import com.shelf.admin.domain.dto.ResolveDeadLetterDTO;
import com.shelf.admin.domain.vo.*;
import com.shelf.admin.entity.*;
import com.shelf.admin.mapper.*;
import com.shelf.admin.service.IAdminService;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.exception.BusinessException;

import com.shelf.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements IAdminService {

    private final SysUserMapper sysUserMapper;
    private final UserBorrowQuotaMapper userBorrowQuotaMapper;
    private final BorrowRecordMapper borrowRecordMapper;
    private final DeadLetterRecordMapper deadLetterRecordMapper;
    private final BookInstanceMapper bookInstanceMapper;
    private final BookSkuMapper bookSkuMapper;
    private final DonateRecordMapper donateRecordMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public DashboardVO dashboard() {
        DashboardVO vo = new DashboardVO();
        vo.setTotalDonate(donateRecordMapper.selectCount(null));
        vo.setTotalBorrowing(borrowRecordMapper.selectCount(
                new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "BORROWED")));
        vo.setTotalOverdue(borrowRecordMapper.selectCount(
                new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getStatus, "OVERDUE")));
        vo.setTodayReturn(borrowRecordMapper.selectCount(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getStatus, "RETURNED")
                        .apply("DATE(return_time) = CURDATE()")));
        vo.setHotBooks(borrowRecordMapper.selectHotBooks(5));
        return vo;
    }

    @Override
    public PageResult<OverdueRecordVO> overdueList(int page, int size) {
        Page<BorrowRecord> pageParam = new Page<>(page, size);
        Page<BorrowRecord> recordPage = borrowRecordMapper.selectPage(pageParam,
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getStatus, "OVERDUE")
                        .orderByDesc(BorrowRecord::getDueDate));

        List<OverdueRecordVO> list = recordPage.getRecords().stream().map(r -> {
            OverdueRecordVO vo = new OverdueRecordVO();
            BeanUtil.copyProperties(r, vo);
            vo.setOverdueDays(ChronoUnit.DAYS.between(r.getDueDate(), LocalDateTime.now()));
            return vo;
        }).collect(Collectors.toList());

        return PageResult.build(list, recordPage.getTotal(), (int) recordPage.getCurrent(), (int) recordPage.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void forceReturn(String recordNo) {
        BorrowRecord record = borrowRecordMapper.selectOne(
                new LambdaQueryWrapper<BorrowRecord>().eq(BorrowRecord::getRecordNo, recordNo));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "记录不存在");
        }
        if (!"OVERDUE".equals(record.getStatus()) && !"BORROWED".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该记录不可归还");
        }

        // 1. 实体书回库
        BookInstance instance = bookInstanceMapper.selectById(record.getInstanceId());
        if (instance != null) {
            instance.setStatus("AVAILABLE");
            bookInstanceMapper.updateById(instance);
        }

        // 2. SKU 可用库存 +1（使用乐观锁，与 donate 服务保持一致）
        BookSku sku = bookSkuMapper.selectOne(
                new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, record.getIsbn()));
        if (sku != null) {
            bookSkuMapper.update(null,
                    new UpdateWrapper<BookSku>()
                            .eq("id", sku.getId())
                            .eq("version", sku.getVersion())
                            .setSql("available_stock = available_stock + 1")
                            .setSql("version = version + 1"));
            // 同步 Redis（如果该 ISBN 还在热门池中）
            if (Boolean.TRUE.equals(redisTemplate.hasKey("stock:" + record.getIsbn()))) {
                redisTemplate.opsForValue().increment("stock:" + record.getIsbn());
            }
        }

        // 3. 更新借阅记录
        record.setStatus("RETURNED");
        record.setReturnTime(LocalDateTime.now());
        borrowRecordMapper.updateById(record);

        // 4. 用户额度 -1
        UserBorrowQuota quota = userBorrowQuotaMapper.selectById(record.getUserId());
        if (quota != null && quota.getCurrentBorrowCount() > 0) {
            quota.setCurrentBorrowCount(quota.getCurrentBorrowCount() - 1);
            userBorrowQuotaMapper.updateById(quota);
        }

        log.info("管理员强制归还: recordNo={}", recordNo);
    }

    @Override
    public PageResult<DeadLetterListVO> deadLetterList(String type, Integer status, int page, int size) {
        Page<DeadLetterRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<DeadLetterRecord> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(type)) wrapper.eq(DeadLetterRecord::getType, type);
        if (status != null) wrapper.eq(DeadLetterRecord::getStatus, status);
        wrapper.orderByDesc(DeadLetterRecord::getCreateTime);
        Page<DeadLetterRecord> recordPage = deadLetterRecordMapper.selectPage(pageParam, wrapper);

        List<DeadLetterListVO> list = recordPage.getRecords().stream().map(r -> {
            DeadLetterListVO vo = new DeadLetterListVO();
            BeanUtil.copyProperties(r, vo);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.build(list, recordPage.getTotal(), (int) recordPage.getCurrent(), (int) recordPage.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolveDeadLetter(Long id, ResolveDeadLetterDTO dto) {
        DeadLetterRecord record = deadLetterRecordMapper.selectById(id);
        if (record == null || record.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "死信记录不存在或已处理");
        }
        record.setStatus(1);
        record.setResolveRemark(dto.getRemark());
        record.setResolveTime(LocalDateTime.now());
        deadLetterRecordMapper.updateById(record);
        log.info("死信已解决: id={}", id);
    }

    @Override
    public PageResult<UserListVO> userList(String keyword, int page, int size) {
        Page<SysUser> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SysUser::getStudentNo, keyword)
                    .or().like(SysUser::getRealName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        Page<SysUser> userPage = sysUserMapper.selectPage(pageParam, wrapper);

        List<UserListVO> list = userPage.getRecords().stream().map(u -> {
            UserListVO vo = new UserListVO();
            BeanUtil.copyProperties(u, vo);

            UserBorrowQuota quota = userBorrowQuotaMapper.selectById(u.getId());
            if (quota != null) {
                vo.setCurrentBorrowCount(quota.getCurrentBorrowCount());
                vo.setOverdueCount(quota.getOverdueCount());
            }

            String frozenFlag = redisTemplate.opsForValue().get("user:frozen:" + u.getId());
            boolean autoFrozen = quota != null && quota.getOverdueCount() >= 3;
            vo.setFrozen("1".equals(frozenFlag) || autoFrozen);

            return vo;
        }).collect(Collectors.toList());

        return PageResult.build(list, userPage.getTotal(), (int) userPage.getCurrent(), (int) userPage.getSize());
    }

    @Override
    public void freezeUser(Long userId) {
        redisTemplate.opsForValue().set("user:frozen:" + userId, "1");
        log.info("管理员冻结用户: userId={}", userId);
    }

    @Override
    public void unfreezeUser(Long userId) {
        redisTemplate.delete("user:frozen:" + userId);
        log.info("管理员解冻用户: userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calibrateStock(CalibrateStockDTO dto) {
        Long actual = bookInstanceMapper.selectCount(
                new LambdaQueryWrapper<BookInstance>()
                        .eq(BookInstance::getIsbn, dto.getIsbn())
                        .eq(BookInstance::getStatus, "AVAILABLE"));

        BookSku sku = bookSkuMapper.selectOne(
                new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, dto.getIsbn()));
        if (sku == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "ISBN 不存在");
        }

        // 使用乐观锁校准库存
        int updated = bookSkuMapper.update(null,
                new UpdateWrapper<BookSku>()
                        .eq("id", sku.getId())
                        .eq("version", sku.getVersion())
                        .set("available_stock", actual.intValue())
                        .setSql("version = version + 1"));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "库存校准冲突，请重试");
        }

        redisTemplate.opsForValue().set("stock:" + dto.getIsbn(), String.valueOf(actual));

        log.info("库存校准完成: isbn={}, actual={}", dto.getIsbn(), actual);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDamaged(MarkDamagedDTO dto) {
        BookInstance instance = bookInstanceMapper.selectOne(
                new LambdaQueryWrapper<BookInstance>()
                        .eq(BookInstance::getInstanceCode, dto.getInstanceCode()));
        if (instance == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "实体书不存在");
        }
        if ("BORROWED".equals(instance.getStatus()) || "RESERVED".equals(instance.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该书正在流转中，请先归还/释放");
        }

        // 【修正】先判断原状态，再改状态
        boolean needDeductStock = "AVAILABLE".equals(instance.getStatus());

        instance.setStatus("DAMAGED");
        bookInstanceMapper.updateById(instance);

        // 如果原来是 AVAILABLE，扣减 SKU 可用库存
        if (needDeductStock) {
            BookSku sku = bookSkuMapper.selectOne(
                    new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, instance.getIsbn()));
            if (sku != null && sku.getAvailableStock() > 0) {
                bookSkuMapper.update(null,
                        new UpdateWrapper<BookSku>()
                                .eq("id", sku.getId())
                                .eq("version", sku.getVersion())
                                .setSql("available_stock = available_stock - 1")
                                .setSql("version = version + 1"));
                // 同步 Redis
                if (Boolean.TRUE.equals(redisTemplate.hasKey("stock:" + instance.getIsbn()))) {
                    redisTemplate.opsForValue().decrement("stock:" + instance.getIsbn());
                }
            }
        }

        log.info("标记损坏: instanceCode={}", dto.getInstanceCode());
    }

    @Override
    public PageResult<BorrowListVO> borrowList(String recordNo, String status, int page, int size) {
        Page<BorrowRecord> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BorrowRecord> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(recordNo)) wrapper.eq(BorrowRecord::getRecordNo, recordNo);
        if (StrUtil.isNotBlank(status)) wrapper.eq(BorrowRecord::getStatus, status);
        wrapper.orderByDesc(BorrowRecord::getCreateTime);
        Page<BorrowRecord> recordPage = borrowRecordMapper.selectPage(pageParam, wrapper);

        List<BorrowListVO> list = recordPage.getRecords().stream().map(r -> {
            BorrowListVO vo = new BorrowListVO();
            BeanUtil.copyProperties(r, vo);
            return vo;
        }).collect(Collectors.toList());

        return PageResult.build(list, recordPage.getTotal(), (int) recordPage.getCurrent(), (int) recordPage.getSize());
    }

    @Override
    public BorrowDetailVO getBorrowDetail(String recordNo) {
        BorrowRecord record = borrowRecordMapper.selectOne(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getRecordNo, recordNo));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "凭证不存在，请检查输入");
        }

        BorrowDetailVO vo = new BorrowDetailVO();
        BeanUtil.copyProperties(record, vo);

        // 查实体书编码
        BookInstance instance = bookInstanceMapper.selectById(record.getInstanceId());
        if (instance != null) {
            vo.setInstanceCode(instance.getInstanceCode());
        }

        // 查用户信息
        SysUser user = sysUserMapper.selectById(record.getUserId());
        if (user != null) {
            vo.setUserName(user.getRealName());
            vo.setUserPhone(user.getPhone());
        }

        return vo;
    }

    /**
     * 扫码领取确认（管理员/自助机）
     * 状态：PENDING_PICKUP → BORROWED
     * 【注意】额度在 borrow.apply() 同步阶段已占用，此处不再重复增加
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pickupConfirm(String recordNo) {
        BorrowRecord record = borrowRecordMapper.selectOne(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getRecordNo, recordNo));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "凭证不存在");
        }
        if (!"PENDING_PICKUP".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该记录不可领取，当前状态：" + record.getStatus());
        }

        // 1. 更新借阅记录
        record.setStatus("BORROWED");
        record.setPickupTime(LocalDateTime.now());
        record.setDueDate(LocalDateTime.now().plusMonths(4));
        borrowRecordMapper.updateById(record);

        // 2. 实体书状态改为 BORROWED（被领走了）
        BookInstance instance = bookInstanceMapper.selectById(record.getInstanceId());
        if (instance != null) {
            instance.setStatus("BORROWED");
            bookInstanceMapper.updateById(instance);
        }

        // 3. 【已移除】额度在 apply() 时已通过 Feign 增加，此处不再重复操作
        log.info("扫码领取确认成功: recordNo={}, userId={}", recordNo, record.getUserId());
    }

    /**
     * 扫码归还确认（管理员/自助机）
     * 状态：BORROWED/OVERDUE → RETURNED
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnBook(String recordNo) {
        BorrowRecord record = borrowRecordMapper.selectOne(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getRecordNo, recordNo));
        if (record == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "凭证不存在");
        }
        if (!"BORROWED".equals(record.getStatus()) && !"OVERDUE".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该记录不可归还，当前状态：" + record.getStatus());
        }

        // 1. 实体书回库：BORROWED → AVAILABLE
        BookInstance instance = bookInstanceMapper.selectById(record.getInstanceId());
        if (instance != null) {
            instance.setStatus("AVAILABLE");
            bookInstanceMapper.updateById(instance);
        }

        // 2. SKU 可用库存 +1（使用乐观锁）
        BookSku sku = bookSkuMapper.selectOne(
                new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, record.getIsbn()));
        if (sku != null) {
            bookSkuMapper.update(null,
                    new UpdateWrapper<BookSku>()
                            .eq("id", sku.getId())
                            .eq("version", sku.getVersion())
                            .setSql("available_stock = available_stock + 1")
                            .setSql("version = version + 1"));
            // 同步 Redis（如果该 ISBN 还在热门池中）
            if (Boolean.TRUE.equals(redisTemplate.hasKey("stock:" + record.getIsbn()))) {
                redisTemplate.opsForValue().increment("stock:" + record.getIsbn());
            }
        }

        // 3. 更新借阅记录
        record.setStatus("RETURNED");
        record.setReturnTime(LocalDateTime.now());
        borrowRecordMapper.updateById(record);

        // 4. 用户额度 -1
        UserBorrowQuota quota = userBorrowQuotaMapper.selectById(record.getUserId());
        if (quota != null && quota.getCurrentBorrowCount() > 0) {
            quota.setCurrentBorrowCount(quota.getCurrentBorrowCount() - 1);
            userBorrowQuotaMapper.updateById(quota);
        }

        log.info("扫码归还确认成功: recordNo={}, userId={}", recordNo, record.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryDeadLetter(Long id) {
        DeadLetterRecord record = deadLetterRecordMapper.selectById(id);
        if (record == null || record.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "死信记录不存在或已处理");
        }

        // 解析死信上下文
        Map<String, Object> context = JSON.parseObject(record.getContext());
        String recordNo = record.getBizId();
        String isbn = (String) context.get("isbn");
        Long userId = ((Number) context.get("userId")).longValue();
        String requestId = (String) context.get("requestId");

        // 1. 幂等：检查是否已生成借阅记录
        BorrowRecord exist = borrowRecordMapper.selectOne(
                new LambdaQueryWrapper<BorrowRecord>()
                        .eq(BorrowRecord::getRequestId, requestId));
        if (exist != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该申领已处理，请勿重复重试");
        }

        // 2. 检查 pending 是否还在（判断补偿任务是否已清理）
        String pendingKey = "borrow:pending:" + recordNo;
        Boolean hasPending = redisTemplate.hasKey(pendingKey);

        if (!Boolean.TRUE.equals(hasPending)) {
            // 补偿任务已清理，需要重新初始化：占额度 + 扣 Redis 库存
            log.warn("死信重试时 pending 已过期，重新初始化资源: recordNo={}", recordNo);

            // 2.1 重新占额度（直接操作表，MVP 简化）
            UserBorrowQuota quota = userBorrowQuotaMapper.selectById(userId);
            if (quota != null) {
                quota.setCurrentBorrowCount(quota.getCurrentBorrowCount() + 1);
                userBorrowQuotaMapper.updateById(quota);
            }

            // 2.2 重新扣 Redis 库存
            String stockKey = "stock:" + isbn;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                redisTemplate.opsForValue().decrement(stockKey);
            }

            // 2.3 重新写 pending
            PendingBorrowDTO pending = new PendingBorrowDTO(userId, isbn, requestId, LocalDateTime.now());
            redisTemplate.opsForValue().set(pendingKey, JSON.toJSONString(pending), 10, TimeUnit.MINUTES);
        }

        // 3. 扣 DB 库存（乐观锁）
        BookSku sku = bookSkuMapper.selectOne(
                new LambdaQueryWrapper<BookSku>().eq(BookSku::getIsbn, isbn));
        if (sku == null || sku.getAvailableStock() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "库存不足，无法重试");
        }

        int updated = bookSkuMapper.update(null,
                new UpdateWrapper<BookSku>()
                        .eq("id", sku.getId())
                        .eq("version", sku.getVersion())
                        .setSql("available_stock = available_stock - 1")
                        .setSql("version = version + 1"));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "库存扣减冲突，请重试");
        }

        // 4. FIFO 分配实体书
        BookInstance instance = bookInstanceMapper.selectOne(
                new LambdaQueryWrapper<BookInstance>()
                        .eq(BookInstance::getIsbn, isbn)
                        .eq(BookInstance::getStatus, "AVAILABLE")
                        .orderByAsc(BookInstance::getCreateTime)
                        .last("LIMIT 1 FOR UPDATE"));
        if (instance == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "实体书分配失败，库存数据异常");
        }
        instance.setStatus("RESERVED");
        instance.setReservedBy(userId);
        bookInstanceMapper.updateById(instance);

        // 5. 生成本地借阅记录
        BorrowRecord borrowRecord = new BorrowRecord();
        borrowRecord.setRecordNo(recordNo);
        borrowRecord.setRequestId(requestId);
        borrowRecord.setUserId(userId);
        borrowRecord.setInstanceId(instance.getId());
        borrowRecord.setIsbn(isbn);
        borrowRecord.setBookTitle(sku.getTitle());
        borrowRecord.setInstanceCode(instance.getInstanceCode());
        borrowRecord.setLocation(instance.getLocation());
        borrowRecord.setStatus("PENDING_PICKUP");
        borrowRecord.setBorrowTime(LocalDateTime.now());
        borrowRecord.setPickupDeadline(LocalDateTime.now().plusDays(7));
        borrowRecordMapper.insert(borrowRecord);

        // 6. 清理 pending
        redisTemplate.delete(pendingKey);

        // 7. 更新死信状态为"已解决"
        record.setStatus(1);
        record.setResolveRemark("管理员重试成功，已分配实体书并生成借阅记录");
        record.setResolveTime(LocalDateTime.now());
        deadLetterRecordMapper.updateById(record);

        log.info("死信重试成功: recordNo={}, instanceCode={}", recordNo, instance.getInstanceCode());
    }
}