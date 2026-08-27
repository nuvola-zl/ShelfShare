package com.shelf.donate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.donate.entity.DeadLetterRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeadLetterRecordMapper extends BaseMapper<DeadLetterRecord> {
}