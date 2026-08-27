package com.shelf.borrow.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PickupConfirmDTO {
    @NotBlank(message = "凭证号不能为空")
    private String recordNo;
}