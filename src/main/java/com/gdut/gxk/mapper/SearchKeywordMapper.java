package com.gdut.gxk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gdut.gxk.entity.SearchKeyword;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 搜索词表数据访问
 * 实际数据库列名：keyword_id, type, value, count
 */
@Mapper
public interface SearchKeywordMapper extends BaseMapper<SearchKeyword> {

    /**
     * 按类型查询热门搜索词（按搜索次数倒序）
     */
    @Select("<script>" +
            "SELECT * FROM search_keyword " +
            "WHERE type IN " +
            "<foreach collection='types' item='type' open='(' separator=',' close=')'>#{type}</foreach> " +
            "ORDER BY count DESC LIMIT #{limit}" +
            "</script>")
    List<SearchKeyword> selectHotByTypes(
            @Param("types") List<String> types,
            @Param("limit") int limit);

    /**
     * 搜索词不存在则插入（防重复），初始count=1
     */
    @Insert("INSERT INTO search_keyword (value, type, count) " +
            "SELECT #{value}, #{type}, 1 " +
            "WHERE NOT EXISTS (SELECT 1 FROM search_keyword WHERE value = #{value})")
    int insertIfNotExists(
            @Param("value") String value,
            @Param("type") String type);

    /**
     * 搜索词已存在时递增搜索次数
     */
    @Update("UPDATE search_keyword SET count = count + 1 WHERE value = #{value}")
    int incrementCount(@Param("value") String value);
}
