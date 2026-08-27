// BorrowListVO.java
package com.shelf.admin.domain.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BorrowListVO {
    private String recordNo;
    private Long userId;
    private String isbn;
    private String bookTitle;
    private String status;
    private LocalDateTime borrowTime;
    private LocalDateTime pickupDeadline;
    private LocalDateTime dueDate;
    private LocalDateTime returnTime;
}