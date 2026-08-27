package com.shelf.donate.service;


import com.shelf.api.dto.donate.BookInstanceDTO;
import com.shelf.common.result.PageResult;
import com.shelf.donate.entity.BookSku;

public interface IDonateStockService {

    BookInstanceDTO deductStock(String isbn, Long userId);

    void releaseStock(Long instanceId, String isbn);

    void returnStock(Long instanceId, String isbn);

    PageResult<BookSku> searchSku(String keyword, Integer grade, String major, int page, int size);
}