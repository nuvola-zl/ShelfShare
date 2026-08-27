package com.shelf.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReturnDTO {
    @NotBlank(message = "凭证号不能为空")
    private String recordNo;
}