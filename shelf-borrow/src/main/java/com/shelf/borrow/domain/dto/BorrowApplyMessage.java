package com.shelf.borrow.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class BorrowApplyMessage  {
//    @Serial
//    // private static final long serialVersionUID = 1L;
    private String recordNo;
    private Long userId;
    private String isbn;
    private String requestId;
}