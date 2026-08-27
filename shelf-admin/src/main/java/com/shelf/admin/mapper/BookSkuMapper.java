// BookSkuMapper.java
package com.shelf.admin.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.admin.entity.BookSku;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookSkuMapper extends BaseMapper<BookSku> {}