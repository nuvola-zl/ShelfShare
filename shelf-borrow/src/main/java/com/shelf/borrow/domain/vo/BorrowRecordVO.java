// BorrowRecordVO.java
package com.shelf.borrow.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Base64;

@Data
public class BorrowRecordVO {
    private Long id;
    private String recordNo;
    private String requestId;
    private String isbn;
    private String bookTitle;
    private String instanceCode;
    private String location;
    private String status;
    private LocalDateTime borrowTime;
    private LocalDateTime pickupDeadline;
    private LocalDateTime pickupTime;
    private LocalDateTime dueDate;
    private LocalDateTime returnTime;
    private LocalDateTime remindTime;
    private Integer overdueDays;
    private String cancelReason;
    private String qrCode;

    public String getQrCode() {
        if (recordNo == null) return null;
        return Base64.getEncoder().encodeToString(recordNo.getBytes());
    }
}