package com.shelf.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.shelf.admin.domain.dto.*;
import com.shelf.admin.domain.vo.BorrowDetailVO;
import com.shelf.admin.service.IAdminService;
import com.shelf.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class AdminController {

    private final IAdminService adminService;

    @GetMapping("/dashboard")
    public Result<?> dashboard() {
        return Result.success(adminService.dashboard());
    }

    @GetMapping("/overdue/list")
    public Result<?> overdueList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminService.overdueList(page, size));
    }

    @PostMapping("/overdue/{recordNo}/return")
    public Result<Void> forceReturn(@PathVariable String recordNo) {
        adminService.forceReturn(recordNo);
        return Result.success();
    }

    @GetMapping("/dead-letter/list")
    public Result<?> deadLetterList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminService.deadLetterList(type, status, page, size));
    }

    @PostMapping("/dead-letter/{id}/resolve")
    public Result<Void> resolveDeadLetter(@PathVariable Long id,
                                          @Valid @RequestBody ResolveDeadLetterDTO dto) {
        adminService.resolveDeadLetter(id, dto);
        return Result.success();
    }

    @GetMapping("/user/list")
    public Result<?> userList(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminService.userList(keyword, page, size));
    }

    @PostMapping("/user/{userId}/freeze")
    public Result<Void> freezeUser(@PathVariable Long userId) {
        adminService.freezeUser(userId);
        return Result.success();
    }

    @PostMapping("/user/{userId}/unfreeze")
    public Result<Void> unfreezeUser(@PathVariable Long userId) {
        adminService.unfreezeUser(userId);
        return Result.success();
    }

    @PostMapping("/stock/calibrate")
    public Result<Void> calibrateStock(@Valid @RequestBody CalibrateStockDTO dto) {
        adminService.calibrateStock(dto);
        return Result.success();
    }

    @PostMapping("/book/damage")
    public Result<Void> markDamaged(@Valid @RequestBody MarkDamagedDTO dto) {
        adminService.markDamaged(dto);
        return Result.success();
    }

    @GetMapping("/borrow/list")
    public Result<?> borrowList(
            @RequestParam(required = false) String recordNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(adminService.borrowList(recordNo, status, page, size));
    }

    /**
     * 凭证详情查询（管理员输入凭证号/扫码后查信息）
     */
    @GetMapping("/borrow/{recordNo}")
    public Result<BorrowDetailVO> getBorrowDetail(@PathVariable String recordNo) {
        return Result.success(adminService.getBorrowDetail(recordNo));
    }

    /**
     * 扫码领取确认
     */
    @PostMapping("/pickup")
    public Result<Void> pickupConfirm(@Valid @RequestBody PickupConfirmDTO dto) {
        adminService.pickupConfirm(dto.getRecordNo());
        return Result.success();
    }

    /**
     * 扫码归还确认
     */
    @PostMapping("/return")
    public Result<Void> returnBook(@Valid @RequestBody ReturnDTO dto) {
        adminService.returnBook(dto.getRecordNo());
        return Result.success();
    }

    /**
     * 死信重试：重新分配实体书、写借阅记录，让用户申领成功
     */
    @PostMapping("/dead-letter/{id}/retry")
    public Result<Void> retryDeadLetter(@PathVariable Long id) {
        adminService.retryDeadLetter(id);
        return Result.success();
    }


}