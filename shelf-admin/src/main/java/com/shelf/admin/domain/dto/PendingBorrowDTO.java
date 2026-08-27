package com.shelf.admin.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingBorrowDTO {
    private Long userId;
    private String isbn;
    private String requestId;
    private LocalDateTime createTime;
}