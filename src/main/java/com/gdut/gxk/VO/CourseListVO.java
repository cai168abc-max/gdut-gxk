package com.gdut.gxk.VO;

import com.gdut.gxk.entity.CourseBase;
import lombok.Data;
import java.math.BigDecimal;

/** 课程列表页卡片显示（第一页用：课程名+教师+评分+标签+学分） */
@Data
public class CourseListVO {
    private String courseId;       // 课程ID（跳转详情用）
    private String courseName;     // 课程名
    private String teacherName;    // 授课教师
    private BigDecimal score;      // 课程评分（默认0.0）
    private String category;       //课程类型
    private String campus;         //课程校区
    private String tag;            // 课程标签（逗号分隔）
    private BigDecimal credit;     // 新增：学分（如2.0）

    /** 从CourseBase实体转换为VO */
    public static CourseListVO fromEntity(CourseBase course) {
        CourseListVO vo = new CourseListVO();
        vo.setCourseId(course.getCourseId());
        vo.setCourseName(course.getCourseName());
        vo.setTeacherName(course.getTeacherName());
        vo.setCampus(course.getCampus());
        vo.setCategory(course.getCategory());
        vo.setScore(course.getScore() == null ? BigDecimal.ZERO : course.getScore());
        vo.setTag(course.getTag() == null ? "" : course.getTag());
        vo.setCredit(course.getCredit()); // 赋值学分
        // vo信息已通过日志框架处理
        return vo;
    }
}
