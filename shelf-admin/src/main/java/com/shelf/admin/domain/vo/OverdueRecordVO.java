// OverdueRecordVO.java
package com.shelf.admin.domain.vo;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OverdueRecordVO {
    private String recordNo;
    private Long userId;
    private String isbn;
    private String bookTitle;
    private LocalDateTime dueDate;
    private Long overdueDays;
}
