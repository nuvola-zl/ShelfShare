package com.shelf.gateway.result;

import lombok.Data;

@Data
public class Result {
    private Integer code;
    private String message;
    private Object data;

    public static Result fail(int code, String message) {
        Result r = new Result();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}