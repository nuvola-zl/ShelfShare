// DeadLetterListVO.java
package com.shelf.admin.domain.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeadLetterListVO {
    private Long id;
    private String type;
    private String bizType;
    private String bizId;
    private String errorMsg;
    private Integer status;
    private String resolveRemark;
    private LocalDateTime createTime;
}