package com.shelf.donate.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DonateRecordVO {
    private Long id;
    private String requestId;
    private String isbn;
    private String title;
    private String coverImageUrl;
    private String status;
    private LocalDateTime createTime;
}