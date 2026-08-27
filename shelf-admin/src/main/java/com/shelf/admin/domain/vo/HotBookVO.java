// HotBookVO.java
package com.shelf.admin.domain.vo;

import lombok.Data;

@Data
public class HotBookVO {
    private String isbn;
    private String title;
    private Integer applyCount;
    private Integer availableStock;  // 当前可申领
    private Integer totalStock;      // 总入库（累计捐赠）
}