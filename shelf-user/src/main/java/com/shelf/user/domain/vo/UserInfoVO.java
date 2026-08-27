package com.shelf.user.domain.vo;

import lombok.Data;

@Data
public class UserInfoVO {
    private Long id;
    private String studentNo;
    private String realName;
    private String nickname;
    private String phone;
    private String avatar;
    private String college;
    private String major;
    private Integer grade;
    private Integer gender;
    private Integer role;
}