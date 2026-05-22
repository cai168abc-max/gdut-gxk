package com.gdut.gxk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程基础信息实体类（对应course_base表）
 */
@Data
@TableName("course_base")  // 绑定数据库表名
public class CourseBase {

    /**
     * 课程唯一标识
     * 生成规则：课程名MD5 + 教师名MD5 + (课程名长度+教师名长度)
     * 手动生成，非自增，故指定IdType.INPUT
     */
    @TableId(type = IdType.INPUT, value = "course_id")
    private String courseId;

    /**
     * 课程名称（非空，最长100字符）
     */
    @TableField("course_name")
    private String courseName;

    /**
     * 授课教师名（非空，最长50字符）
     */
    @TableField("teacher_name")
    private String teacherName;

    /**
     * 课程类别（预设值：自然科学与工程技术类/人文与社会科学类/体育/十校公选/--）
     * 数据库默认值"--"，后端需校验取值范围
     */
    @TableField("category")
    private String category;

    /**
     * 上课校区（预设值：5个广工校区+十校公选+--）
     * 数据库默认值"--"，后端需校验取值范围
     */
    @TableField("campus")
    private String campus;

    /**
     * 开课学院（预设值：十校公选/体育学院/--）
     * 数据库默认值"--"，后端需校验取值范围
     */
    @TableField("college")
    private String college;

    /**
     * 课程学分（0.5-5.0，保留1位小数）
     */
    @TableField("credit")
    private BigDecimal credit;

    /**
     * 上课时间（格式：周*第*节课，如“周二第3-4节”）
     * 后端需用正则校验格式
     */
    @TableField("course_time")
    private String courseTime;

    /**
     * 课程安排（如"全学期线下（每周2次，机房上课）"）
     * DDL列名：course_schedule
     */
    @TableField("course_schedule")
    private String courseSchedule;

    /**
     * 课程综合评分（AI定时更新，0.0-5.0）
     * DDL列名：score
     */
    @TableField("score")
    private BigDecimal score;

    /**
     * AI生成的课程总结（最长65535字符）
     */
    @TableField("ai_summary")
    private String aiSummary;

    /**
     * 课程标签（逗号分隔，最多4个标签，无标签时为"暂无"）
     * 示例："好评,给分高,人气"
     * DDL列名：tag
     */
    @TableField("tag")
    private String tag;

    /**
     * 记录创建时间（数据库默认CURRENT_TIMESTAMP，后端通过填充器自动赋值）
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 记录更新时间（数据库默认CURRENT_TIMESTAMP ON UPDATE，后端通过填充器自动赋值）
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)  // 关键：标记为非数据库字段
    private Integer commentCount;  // 假设评论数是整数类型
}
