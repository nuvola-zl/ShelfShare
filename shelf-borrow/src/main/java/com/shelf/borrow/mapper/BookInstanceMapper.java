package com.shelf.borrow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.borrow.entity.BookInstance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookInstanceMapper extends BaseMapper<BookInstance> {
}