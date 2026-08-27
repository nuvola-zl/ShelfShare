package com.shelf.borrow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.borrow.entity.BookSku;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookSkuMapper extends BaseMapper<BookSku> {}