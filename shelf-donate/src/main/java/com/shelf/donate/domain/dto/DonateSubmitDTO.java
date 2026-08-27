package com.shelf.donate.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DonateSubmitDTO {

    @NotBlank(message = "幂等请求ID不能为空")
    private String requestId;

    @NotBlank(message = "ISBN不能为空")
    private String isbn;

    @NotBlank(message = "书名不能为空")
    private String title;

    private String author;

    private String publisher;

    private String coverImageUrl;

    private String innerImageUrl;
}