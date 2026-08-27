// MarkDamagedDTO.java
package com.shelf.admin.domain.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MarkDamagedDTO {
    @NotBlank(message = "实体书编码不能为空")
    private String instanceCode;
}