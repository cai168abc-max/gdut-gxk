package com.gdut.gxk.VO;

import com.gdut.gxk.entity.CourseBase;
import lombok.Data;
import java.math.BigDecimal;

/** 课程详情页显示（第二页用：基础信息+时间+安排+总评论数+学分） */
@Data
public class CourseDetailVO {
    // 原有基础信息
    private String courseId;       // 课程ID
    private String courseName;     // 课程名
    private String teacherName;    // 授课教师
    private BigDecimal score;      // 课程评分
    private String campus;         // 校区
    private String college;        // 学院
    private String aiSummary;      // AI总结
    private String tag;            // 课程标签
    // 新增字段
    private BigDecimal credit;     // 学分
    private String courseTime;     // 课程时间（如“周三7-9节”）
    private String courseSchedule; // 课程安排（如“第一周到第八周线下”）
    private Integer totalCommentCount; // 总评论数（统计量）

    /** 从CourseBase实体+总评论数转换为VO */
    public static CourseDetailVO fromEntity(CourseBase course, Integer totalCommentCount) {
        CourseDetailVO vo = new CourseDetailVO();
        vo.setCourseId(course.getCourseId());
        vo.setCourseName(course.getCourseName());
        vo.setTeacherName(course.getTeacherName());
        vo.setScore(course.getScore() == null ? BigDecimal.ZERO : course.getScore());
        vo.setCampus(course.getCampus());
        vo.setCollege(course.getCollege());
        vo.setAiSummary(course.getAiSummary() == null ? "暂无AI总结" : course.getAiSummary());
        vo.setTag(course.getTag() == null ? "" : course.getTag());
        // 新增字段赋值
        vo.setCredit(course.getCredit());
        vo.setCourseTime(course.getCourseTime() == null ? "暂无课程时间" : course.getCourseTime());
        vo.setCourseSchedule(course.getCourseSchedule() == null ? "暂无课程安排" : course.getCourseSchedule());
        vo.setTotalCommentCount(totalCommentCount == null ? 0 : totalCommentCount);
        return vo;
    }
}
