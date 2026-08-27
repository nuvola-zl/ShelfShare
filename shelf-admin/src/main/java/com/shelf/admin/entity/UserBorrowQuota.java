// UserBorrowQuota.java
package com.shelf.admin.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_borrow_quota")
public class UserBorrowQuota {

    @TableId
    private Long userId;
    private Integer currentBorrowCount;
    private Integer totalBorrowCount;
    private Integer overdueCount;
    private LocalDateTime createTime;
}