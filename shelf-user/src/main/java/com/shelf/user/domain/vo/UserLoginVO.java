package com.shelf.user.domain.vo;

import lombok.Data;

@Data
public class UserLoginVO {
    private Long id;
    private String studentNo;
    private String realName;
    private String nickname;
    private Integer role;
    private String token;
    private String avatar;
}