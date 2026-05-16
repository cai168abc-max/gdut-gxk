package com.gdut.gxk.DTO;

import lombok.Data;

/**
 * AI 对话文本清洗后生成的查询参数
 * 与 `course_base` 字段一一对应，便于对接 DAO 层
 */
@Data
public class AIQueryParamDTO {
    // 对应 course_base.course_name（LIKE）
    private String courseName;

    // 对应 course_base.teacher_name（LIKE）
    private String teacherName;

    // 对应 course_base.campus（EXACT）
    private String campus;

    // 对应 course_base.category（EXACT）
    private String category;

    // 对应 course_base.course_time（LIKE）
    private String courseTime;

    // 对应 course_base.credit（EXACT），以字符串承载以兼容映射（2.0/3.0）
    private String credit;

    // 对应 course_base.score（RANGE 表达，如 ">=4.0"）
    private String scoreExpr;

    // 对应 course_base.tag（LIKE，逗号分隔集合）
    private String tags;

    // 原始输入（脱敏后）及清洗痕迹
    private String cleanedInput;
}


