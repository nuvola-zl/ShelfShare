package com.shelf.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BorrowDetailVO {
    private String recordNo;        // 凭证号
    private String status;          // 当前状态
    private String bookTitle;       // 书名
    private String isbn;            // ISBN
    private String instanceCode;    // 实体书编码
    private Long userId;            // 申领人ID
    private String userName;        // 申领人姓名
    private String userPhone;       // 手机号（核验身份）
    private LocalDateTime borrowTime;      // 申领时间
    private LocalDateTime pickupDeadline;  // 领取截止（7天内）
    private LocalDateTime dueDate;         // 到期时间（已领取后才有）
}