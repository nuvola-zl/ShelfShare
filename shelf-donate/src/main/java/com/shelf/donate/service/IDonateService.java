package com.shelf.donate.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shelf.donate.domain.dto.DonateSubmitDTO;
import com.shelf.donate.domain.vo.DonateRecordVO;
import com.shelf.donate.entity.DonateRecord;

import java.util.List;

public interface IDonateService extends IService<DonateRecord> {

    void donate(DonateSubmitDTO dto);

    List<DonateRecordVO> listMyDonates();
}