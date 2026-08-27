package com.shelf.borrow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shelf.borrow.domain.dto.ApplyDTO;
import com.shelf.borrow.domain.vo.BorrowRecordVO;
import com.shelf.borrow.entity.BorrowRecord;

import java.util.List;

public interface IBorrowService extends IService<BorrowRecord> {

    BorrowRecordVO apply(ApplyDTO dto);

    void pickupConfirm(String recordNo);

    void returnBook(String recordNo);

    List<BorrowRecordVO> listMyBorrows();

    BorrowRecordVO getRecordDetail(String recordNo);  // ← 新增：管理员/用户查凭证详情
}