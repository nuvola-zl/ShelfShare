package com.shelf.donate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("donate_record")
public class DonateRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private Long userId;

    private Long instanceId = 0L;      // 异步入库后回填

    private String isbn;

    private String title;

    private String coverImageUrl = "";

    private String innerImageUrl = "";

    private String status;        // PENDING 已提交待入库 / ACCEPTED 已入库 / REJECTED 已拒绝（预留）

    private String remark = "";        // 拒绝原因、撤销备注

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}