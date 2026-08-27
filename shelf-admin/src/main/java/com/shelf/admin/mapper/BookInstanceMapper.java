// BookInstanceMapper.java
package com.shelf.admin.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.admin.entity.BookInstance;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookInstanceMapper extends BaseMapper<BookInstance> {}