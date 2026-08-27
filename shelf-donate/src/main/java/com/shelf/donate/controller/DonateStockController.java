package com.shelf.donate.controller;


import com.shelf.api.dto.donate.BookInstanceDTO;
import com.shelf.api.dto.donate.DeductStockRequest;
import com.shelf.api.dto.donate.ReleaseStockRequest;
import com.shelf.api.dto.donate.ReturnStockRequest;
import com.shelf.common.result.PageResult;
import com.shelf.common.result.Result;
import com.shelf.donate.entity.BookSku;
import com.shelf.donate.service.IDonateStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/donate")
@RequiredArgsConstructor
public class DonateStockController {

    private final IDonateStockService donateStockService;

    /**
     * 申领扣库存（borrow 服务调用）
     */
    @PostMapping("/stock/deduct")
    public Result<BookInstanceDTO> deductStock(@RequestBody DeductStockRequest request) {
        BookInstanceDTO dto = donateStockService.deductStock(request.getIsbn(), request.getUserId());
        return Result.success(dto);
    }

    /**
     * 超时释放库存（borrow 定时任务调用）
     */
    @PostMapping("/stock/release")
    public Result<Void> releaseStock(@RequestBody ReleaseStockRequest request) {
        donateStockService.releaseStock(request.getInstanceId(), request.getIsbn());
        return Result.success();
    }

    /**
     * 归还入库（borrow 归还时调用）
     */
    @PostMapping("/stock/return")
    public Result<Void> returnStock(@RequestBody ReturnStockRequest request) {
        donateStockService.returnStock(request.getInstanceId(), request.getIsbn());
        return Result.success();
    }

    /**
     * 搜索教材（供申领前浏览）
     */
    @GetMapping("/sku/search")
    public Result<PageResult<BookSku>> searchSku(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) String major,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return Result.success(donateStockService.searchSku(keyword, grade, major, page, size));
    }
}