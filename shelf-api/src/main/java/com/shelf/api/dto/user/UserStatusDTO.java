package com.shelf.api.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserStatusDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private Integer role;
    private Boolean frozen;           // 用 Boolean 包装类，序列化更稳
    private Integer currentBorrowCount;
    private Integer overdueCount;
}