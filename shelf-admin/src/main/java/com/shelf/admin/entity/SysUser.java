// SysUser.java
package com.shelf.admin.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO) private Long id;
    private String studentNo;
    private String realName;
    private String phone;
    private String college;
    private String major;
    private Integer grade;
    private Integer role;
    private LocalDateTime createTime;
}