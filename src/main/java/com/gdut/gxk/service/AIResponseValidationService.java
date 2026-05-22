package com.gdut.gxk.service;

import java.util.List;

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
     * 验证AI回复内容并自动修正不可验证的信息
     * @param aiResponse AI回复内容
     * @return 验证结果（包含修正后的回复文本）
     */
    ValidationResult validateAndCorrect(String aiResponse);

    /**
     * 基于工具调用结果的事实审计
     * 核心思路：对比"AI说了什么"和"工具实际返回了什么"
     * - 如果AI给出了具体课程/教师信息，但本轮没有任何工具调用 → 可能幻觉，追加警告
     * - 如果AI给出了具体课程/教师信息，但工具返回为空 → 可能幻觉，追加警告
     * - 如果AI引用了工具结果 → 通过
     * - 闲聊/非事实性回复 → 通过
     *
     * @param aiResponse AI回复内容
     * @param toolResults 本轮工具调用返回的结果列表
     * @return 校验后的回复文本（可能追加了警告）
     */
    String factCheckAgainstToolResults(String aiResponse, List<String> toolResults);

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
            int invalidTeachers,
            String correctedResponse
    ) {
        public boolean isValid() { return isValid; }
        public String getMessage() { return message; }
        public String getCorrectedResponse() { return correctedResponse; }
    }
}