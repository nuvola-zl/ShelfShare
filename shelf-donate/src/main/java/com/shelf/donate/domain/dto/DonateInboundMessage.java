package com.shelf.donate.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DonateInboundMessage  {
//    @Serial
//    private static final long serialVersionUID = 1L;

    private Long donateRecordId;  // 捐赠记录ID
    private String requestId;     // 幂等键
    private String isbn;
    private String title;
    private String author;
    private String publisher;
    private String coverImageUrl;
    private Long userId;
    private String edition;      // ← 新增：版次
}