package com.gdut.gxk.service;

/**
 * AI回复验证服务接口
 * 用于验证AI生成的回复内容的真实性
 */
public interface AIResponseValidationService {

    /**
     * 验证AI回复内容
     * @param aiResponse AI回复内容
     * @return 验证结果
     */
    ValidationResult validateResponse(String aiResponse);

    /**
     * 验证单个课程名称是否存在
     * @param courseName 课程名称
     * @return 是否存在
     */
    boolean validateCourseExists(String courseName);

    /**
     * 验证教师名称是否存在
     * @param teacherName 教师名称
     * @return 是否存在
     */
    boolean validateTeacherExists(String teacherName);

    /**
     * 验证评分是否有效（1-5分）
     * @param scoreStr 评分字符串
     * @return 是否有效
     */
    boolean validateScore(String scoreStr);

    /**
     * 验证结果记录
     */
    record ValidationResult(
            boolean isValid,
            String message,
            int validCourses,
            int invalidCourses,
            int validTeachers,
            int invalidTeachers
    ) {
        public boolean isValid() { return isValid; }
        public String getMessage() { return message; }
    }
}