package com.shelf.borrow.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyDTO {

    @NotBlank(message = "幂等键不能为空")
    private String requestId;

    @NotBlank(message = "ISBN不能为空")
    private String isbn;
}