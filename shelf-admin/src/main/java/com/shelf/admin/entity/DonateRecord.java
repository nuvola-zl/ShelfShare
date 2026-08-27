// DonateRecord.java（仪表盘统计用，极简）
package com.shelf.admin.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("donate_record")
public class DonateRecord {
    @TableId(type = IdType.AUTO) private Long id;
}