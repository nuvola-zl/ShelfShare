// DeadLetterRecordMapper.java
package com.shelf.admin.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.admin.entity.DeadLetterRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeadLetterRecordMapper extends BaseMapper<DeadLetterRecord> {}