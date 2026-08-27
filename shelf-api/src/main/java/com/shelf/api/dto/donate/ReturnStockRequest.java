package com.shelf.api.dto.donate;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ReturnStockRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long instanceId;
    private String isbn;
}