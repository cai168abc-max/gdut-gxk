package com.gdut.gxk.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gdut.gxk.entity.CourseComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/** 评论表数据访问 */
@Mapper
public interface CourseCommentMapper extends BaseMapper<CourseComment> {

    /**
     * 评论分页查询（支持按评分/时间筛选，第二页用）
     */
    @Select("<script>" +
            "SELECT * FROM course_comment WHERE course_id = #{courseId} " +
            "<if test='minScore != null'> AND score &gt;= #{minScore} </if>" +
            "ORDER BY " +
            "<choose>" +
            "<when test='sortBy == \"score\"'>score</when>" +
            "<otherwise>create_time</otherwise>" +
            "</choose>" +
            "<if test='isAsc'> ASC </if>" +
            "<if test='!isAsc'> DESC </if>" +
            "</script>")
    IPage<CourseComment> selectByCourseIdWithFilter(
            @Param("page") Page<CourseComment> page,
            @Param("courseId") String courseId,
            @Param("minScore") Integer minScore,
            @Param("sortBy") String sortBy,
            @Param("isAsc") boolean isAsc);

    /**
     * 统计课程总评论数（第二页详情用）
     */
    @Select("SELECT COUNT(*) FROM course_comment WHERE course_id = #{courseId}")
    int countByCourseId(@Param("courseId") String courseId);

    /**
     * 计算课程评论平均分（备用：如AI更新评分用）
     */
    @Select("SELECT AVG(score) FROM course_comment WHERE course_id = #{courseId}")
    BigDecimal avgScoreByCourseId(@Param("courseId") String courseId);

    /**
     * 根据课程ID查询所有评论
     */
    @Select("SELECT * FROM course_comment WHERE course_id = #{courseId} ORDER BY create_time DESC")
    java.util.List<CourseComment> selectByCourseId(@Param("courseId") String courseId);
}
