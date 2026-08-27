package com.shelf.admin.service;

import com.shelf.admin.domain.dto.CalibrateStockDTO;
import com.shelf.admin.domain.dto.MarkDamagedDTO;
import com.shelf.admin.domain.dto.ResolveDeadLetterDTO;
import com.shelf.admin.domain.vo.*;
import com.shelf.common.result.PageResult;

public interface IAdminService {
    DashboardVO dashboard();
    PageResult<OverdueRecordVO> overdueList(int page, int size);
    void forceReturn(String recordNo);
    PageResult<DeadLetterListVO> deadLetterList(String type, Integer status, int page, int size);
    void resolveDeadLetter(Long id, ResolveDeadLetterDTO dto);
    PageResult<UserListVO> userList(String keyword, int page, int size);
    void freezeUser(Long userId);
    void unfreezeUser(Long userId);
    void calibrateStock(CalibrateStockDTO dto);
    void markDamaged(MarkDamagedDTO dto);
    PageResult<BorrowListVO> borrowList(String recordNo, String status, int page, int size);

    BorrowDetailVO getBorrowDetail(String recordNo);
    void pickupConfirm(String recordNo);
    void returnBook(String recordNo);

    void retryDeadLetter(Long id);
}