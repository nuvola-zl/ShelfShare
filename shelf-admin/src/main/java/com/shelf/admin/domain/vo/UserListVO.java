// UserListVO.java
package com.shelf.admin.domain.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserListVO {
    private Long id;
    private String studentNo;
    private String realName;
    private String phone;
    private String college;
    private String major;
    private Integer grade;
    private Integer role;
    private Integer currentBorrowCount;
    private Integer overdueCount;
    private Boolean frozen;
    private LocalDateTime createTime;
}
