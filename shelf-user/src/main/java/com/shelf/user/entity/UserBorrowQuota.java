package com.shelf.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_borrow_quota")
public class UserBorrowQuota {

    @TableId  // ← 必须加！告诉 MyBatis-Plus 这是主键
    private Long userId;

    private Integer currentBorrowCount;

    private Integer totalBorrowCount;

    private Integer overdueCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}