package com.gdut.gxk.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gdut.gxk.DTO.CourseListQueryDTO;
import com.gdut.gxk.entity.CourseBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
@Mapper
public interface CourseBaseMapper extends BaseMapper<CourseBase> {

    /**
     * 课程列表动态筛选（支持搜索+筛选+分页，第一页用）
     */
    IPage<CourseBase> selectByDynamicTj(
            IPage<CourseBase> page,
            @Param("query") CourseListQueryDTO queryDTO);

    /**
     * 按校区+课程类型查询评论最多的前N门课程（第四页用）
     */
    @Select("SELECT cb.*,comment_count FROM course_base cb LEFT JOIN (select course_id,count(comment_id) AS comment_count from course_comment group by course_id) cc on cb.course_id=cc.course_id WHERE cb.campus = IFNULL(#{campus},cb.campus) AND cb.category = IFNULL(#{category},cb.category) ORDER BY  cc.comment_count DESC LIMIT #{limit}")
    List<CourseBase> selectHotCoursesByComment(
            @Param("campus") String campus,
            @Param("category") String category,
            @Param("limit") int limit);
    /**
     * 按搜索词模糊查询课程（课程名/教师名/标签，第三页AI对话用）
     */
    List<CourseBase> selectByKeyword(@Param("keyword") String keyword);

    /**
     * 根据关键词统计课程数量（AI回复验证用）
     */
    @Select("SELECT COUNT(*) FROM course_base WHERE course_name LIKE #{keyword} OR teacher_name LIKE #{keyword} OR tag LIKE #{keyword}")
    int countByKeyword(@Param("keyword") String keyword);

    /**
     * 根据教师名称统计课程数量（AI回复验证用）
     */
    @Select("SELECT COUNT(*) FROM course_base WHERE teacher_name LIKE #{keyword}")
    int countByTeacher(@Param("keyword") String keyword);
}
