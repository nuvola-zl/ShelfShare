package com.shelf.api.dto.donate;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class BookInstanceDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long instanceId;
    private String instanceCode;
    private String isbn;
    private String title;
    private String location;
}