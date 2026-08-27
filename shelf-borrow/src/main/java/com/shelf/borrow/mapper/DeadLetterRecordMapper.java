package com.shelf.borrow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.borrow.entity.DeadLetterRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeadLetterRecordMapper extends BaseMapper<DeadLetterRecord> {
}