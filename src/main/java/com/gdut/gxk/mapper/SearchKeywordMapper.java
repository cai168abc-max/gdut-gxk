package com.gdut.gxk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdut.gxk.entity.SearchKeyword;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 搜索词表数据访问 */
@Mapper
public interface SearchKeywordMapper extends BaseMapper<SearchKeyword> {

    /**
     * 搜索词计数原子累加（用户搜索时调用）
     */
    int incrementCount(@Param("type") String type, @Param("value") String value);

    /**
     * 批量查询多类型热门搜索词（第四页用）
     */
    List<SearchKeyword> selectHotByTypes(
            @Param("types") List<String> types,
            @Param("limit") int limit);

    /**
     * 搜索词不存在则插入（防重复）
     */
    int insertIfNotExists(@Param("keyword") SearchKeyword keyword);
}
