package com.shelf.api.feign.donate;


import com.shelf.api.dto.donate.BookInstanceDTO;
import com.shelf.api.dto.donate.DeductStockRequest;
import com.shelf.api.dto.donate.ReleaseStockRequest;
import com.shelf.api.dto.donate.ReturnStockRequest;
import com.shelf.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "shelf-donate",
        path = "/api/donate",
        fallbackFactory = DonateFeignApiFallbackFactory.class  // 改这里
)
public interface DonateFeignApi {

    @PostMapping("/stock/deduct")
    Result<BookInstanceDTO> deductStock(@RequestBody DeductStockRequest request);

    @PostMapping("/stock/release")
    Result<Void> releaseStock(@RequestBody ReleaseStockRequest request);

    @PostMapping("/stock/return")
    Result<Void> returnStock(@RequestBody ReturnStockRequest request);
}