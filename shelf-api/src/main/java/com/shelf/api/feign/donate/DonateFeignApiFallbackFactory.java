package com.shelf.api.feign.donate;

import com.shelf.api.dto.donate.BookInstanceDTO;
import com.shelf.api.dto.donate.DeductStockRequest;
import com.shelf.api.dto.donate.ReleaseStockRequest;
import com.shelf.api.dto.donate.ReturnStockRequest;
import com.shelf.common.code.ErrorCode;
import com.shelf.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DonateFeignApiFallbackFactory implements FallbackFactory<DonateFeignApi> {

    @Override
    public DonateFeignApi create(Throwable cause) {
        log.error("【熔断降级】donate 服务调用失败: {}", cause.getMessage());

        return new DonateFeignApi() {
            @Override
            public Result<BookInstanceDTO> deductStock(DeductStockRequest request) {
                log.error("【熔断降级】扣减库存失败，isbn={}", request.getIsbn());
                return Result.error(ErrorCode.REMOTE_CALL_ERROR, "图书服务暂不可用，扣减库存失败，请稍后重试");
            }

            @Override
            public Result<Void> releaseStock(ReleaseStockRequest request) {
                log.error("【熔断降级】释放库存失败，instanceId={}", request.getInstanceId());
                return Result.error(ErrorCode.REMOTE_CALL_ERROR, "图书服务暂不可用，库存释放失败，请稍后重试");
            }

            @Override
            public Result<Void> returnStock(ReturnStockRequest request) {
                log.error("【熔断降级】归还库存失败，instanceId={}", request.getInstanceId());
                return Result.error(ErrorCode.REMOTE_CALL_ERROR, "图书服务暂不可用，归还处理失败，请稍后重试");
            }
        };
    }
}