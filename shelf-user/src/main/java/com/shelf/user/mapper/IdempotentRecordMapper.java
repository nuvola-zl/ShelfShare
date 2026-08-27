package com.shelf.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import com.shelf.user.entity.IdempotentRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IdempotentRecordMapper extends BaseMapper<IdempotentRecord> {
}