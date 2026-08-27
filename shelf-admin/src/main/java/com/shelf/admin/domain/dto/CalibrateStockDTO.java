// CalibrateStockDTO.java
package com.shelf.admin.domain.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CalibrateStockDTO {
    @NotBlank(message = "ISBN不能为空")
    private String isbn;
}