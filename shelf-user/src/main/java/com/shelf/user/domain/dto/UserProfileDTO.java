package com.shelf.user.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UserProfileDTO {

    private String nickname;

    private String avatar;

    private String college;

    private String major;

    @Min(value = 1, message = "年级只能是 1-4")
    @Max(value = 4, message = "年级只能是 1-4")
    private Integer grade;

    @Min(value = 0, message = "性别只能是 0-2")
    @Max(value = 2, message = "性别只能是 0-2")
    private Integer gender;
}