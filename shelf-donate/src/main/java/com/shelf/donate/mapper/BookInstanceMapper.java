package com.shelf.donate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shelf.donate.entity.BookInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BookInstanceMapper extends BaseMapper<BookInstance> {

    /**
     * FIFO 分配实体书，加 FOR UPDATE（必须在 @Transactional 内调用）
     */
    @Select("SELECT * FROM book_instance " +
            "WHERE isbn = #{isbn} AND status = 'AVAILABLE' " +
            "ORDER BY create_time ASC LIMIT 1 FOR UPDATE")
    BookInstance selectAvailableForUpdate(@Param("isbn") String isbn);
    /**
     * 查当月已有编码的最大序号
     * 示例：BOOK-202608-00005 → 返回 5
     */
    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(instance_code, 13) AS UNSIGNED)), 0) " +
            "FROM book_instance " +
            "WHERE instance_code LIKE CONCAT('BOOK-', #{month}, '-%')")
    Long selectMaxSeqByMonth(@Param("month") String month);
}