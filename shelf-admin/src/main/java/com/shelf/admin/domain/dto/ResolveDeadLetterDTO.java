// ResolveDeadLetterDTO.java
package com.shelf.admin.domain.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveDeadLetterDTO {
    @NotBlank(message = "处理备注不能为空")
    private String remark;
}