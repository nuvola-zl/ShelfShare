// DashboardVO.java
package com.shelf.admin.domain.vo;
import lombok.Data;
import java.util.List;

@Data
public class DashboardVO {
    private Long totalDonate;
    private Long totalBorrowing;
    private Long totalOverdue;
    private Long todayReturn;
    private List<HotBookVO> hotBooks;
}
