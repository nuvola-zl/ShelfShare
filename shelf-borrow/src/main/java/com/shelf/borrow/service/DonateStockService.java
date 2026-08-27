package com.shelf.borrow.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.shelf.api.dto.donate.BookInstanceDTO;
import com.shelf.api.dto.donate.DeductStockRequest;
import com.shelf.api.feign.donate.DonateFeignApi;
import com.shelf.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DonateStockService {

    private final DonateFeignApi donateFeignApi;

    /**
     * Sentinel 熔断保护的方法
     * value = "deductStock" 就是资源名，后面配规则要用这个名
     */
    @SentinelResource(
            value = "deductStock",
            blockHandler = "deductStockBlockHandler",
            fallback = "deductStockFallback"
    )
    public BookInstanceDTO deductStock(DeductStockRequest req) {
        Result<BookInstanceDTO> result = donateFeignApi.deductStock(req);
        if (!result.isSuccess() || result.getData() == null) {
            // 抛异常会被 Sentinel 统计为「异常」，用于计算熔断比例
            throw new RuntimeException("DB 扣库存失败: " + result.getMsg());
        }
        return result.getData();
    }

    /**
     * 熔断触发时调用：Sentinel 发现异常比例过高，直接短路，不走 Feign
     */
    public BookInstanceDTO deductStockBlockHandler(DeductStockRequest req, BlockException ex) {
        log.error("【熔断】donate 服务已熔断，拒绝扣库存: isbn={}", req.getIsbn());
        throw new RuntimeException("捐赠服务暂不可用，触发熔断保护");
    }

    /**
     * 业务异常时调用：Feign 调用抛了其他异常
     */
    public BookInstanceDTO deductStockFallback(DeductStockRequest req, Throwable ex) {
        log.error("【降级】donate 服务调用异常: isbn={}, error={}", req.getIsbn(), ex.getMessage());
        throw new RuntimeException("捐赠服务调用失败: " + ex.getMessage());
    }
}