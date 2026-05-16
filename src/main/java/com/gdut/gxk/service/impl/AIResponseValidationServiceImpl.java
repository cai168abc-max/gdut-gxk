package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.AIResponseValidationService;
import com.gdut.gxk.mapper.CourseBaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI回复验证服务实现
 * 验证AI生成的回复内容的真实性，防止模型幻觉
 */
@Service
@Slf4j
public class AIResponseValidationServiceImpl implements AIResponseValidationService {

    @Resource
    private CourseBaseMapper courseBaseMapper;

    /**
     * 课程名称匹配模式（2-10个汉字）
     */
    private static final Pattern COURSE_NAME_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,10}课程|[\\u4e00-\\u9fa5]{2,10}课");
    
    /**
     * 教师名称匹配模式（2-4个汉字 + 老师/教授）
     */
    private static final Pattern TEACHER_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}[老师教授]");
    
    /**
     * 评分匹配模式（1-5分）
     */
    private static final Pattern SCORE_PATTERN = Pattern.compile("[1-5]\\.?\\d*[分]?");

    @Override
    public ValidationResult validateResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return new ValidationResult(true, "空响应无需验证", 0, 0, 0, 0);
        }

        List<String> mentionedCourses = extractCourseNames(aiResponse);
        List<String> mentionedTeachers = extractTeacherNames(aiResponse);
        
        int validCourses = 0;
        int invalidCourses = 0;
        int validTeachers = 0;
        int invalidTeachers = 0;

        // 验证课程名称
        for (String course : mentionedCourses) {
            if (validateCourseExists(course)) {
                validCourses++;
            } else {
                invalidCourses++;
                log.warn("AI回复中提到的课程不存在: {}", course);
            }
        }

        // 验证教师名称
        for (String teacher : mentionedTeachers) {
            if (validateTeacherExists(teacher)) {
                validTeachers++;
            } else {
                invalidTeachers++;
                log.warn("AI回复中提到的教师不存在: {}", teacher);
            }
        }

        // 验证评分
        List<String> scores = extractScores(aiResponse);
        for (String score : scores) {
            if (!validateScore(score)) {
                log.warn("AI回复中包含无效评分: {}", score);
            }
        }

        // 判断整体有效性
        boolean isValid = invalidCourses == 0 && invalidTeachers == 0;
        String message = buildValidationMessage(validCourses, invalidCourses, validTeachers, invalidTeachers);

        log.debug("AI回复验证完成 - 有效课程:{}, 无效课程:{}, 有效教师:{}, 无效教师:{}", 
                validCourses, invalidCourses, validTeachers, invalidTeachers);

        return new ValidationResult(isValid, message, validCourses, invalidCourses, validTeachers, invalidTeachers);
    }

    @Override
    public boolean validateCourseExists(String courseName) {
        if (courseName == null || courseName.isEmpty()) {
            return false;
        }
        
        // 去除"课程"、"课"后缀进行模糊查询
        String keyword = courseName.replaceAll("[课程课]$", "");
        return courseBaseMapper.countByKeyword("%" + keyword + "%") > 0;
    }

    @Override
    public boolean validateTeacherExists(String teacherName) {
        if (teacherName == null || teacherName.isEmpty()) {
            return false;
        }
        
        // 去除"老师"、"教授"后缀进行模糊查询
        String keyword = teacherName.replaceAll("[老师教授]$", "");
        return courseBaseMapper.countByTeacher("%" + keyword + "%") > 0;
    }

    @Override
    public boolean validateScore(String scoreStr) {
        if (scoreStr == null || scoreStr.isEmpty()) {
            return false;
        }
        
        try {
            // 提取数字部分
            String numStr = scoreStr.replaceAll("[^0-9.]", "");
            double score = Double.parseDouble(numStr);
            return score >= 1 && score <= 5;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 从文本中提取课程名称
     */
    private List<String> extractCourseNames(String text) {
        List<String> courses = new ArrayList<>();
        Matcher matcher = COURSE_NAME_PATTERN.matcher(text);
        while (matcher.find()) {
            courses.add(matcher.group());
        }
        return courses;
    }

    /**
     * 从文本中提取教师名称
     */
    private List<String> extractTeacherNames(String text) {
        List<String> teachers = new ArrayList<>();
        Matcher matcher = TEACHER_PATTERN.matcher(text);
        while (matcher.find()) {
            teachers.add(matcher.group());
        }
        return teachers;
    }

    /**
     * 从文本中提取评分
     */
    private List<String> extractScores(String text) {
        List<String> scores = new ArrayList<>();
        Matcher matcher = SCORE_PATTERN.matcher(text);
        while (matcher.find()) {
            scores.add(matcher.group());
        }
        return scores;
    }

    /**
     * 构建验证结果消息
     */
    private String buildValidationMessage(int validCourses, int invalidCourses, 
                                          int validTeachers, int invalidTeachers) {
        StringBuilder sb = new StringBuilder();
        
        if (invalidCourses > 0) {
            sb.append("发现").append(invalidCourses).append("个不存在的课程");
        }
        if (invalidTeachers > 0) {
            if (sb.length() > 0) sb.append("，");
            sb.append("发现").append(invalidTeachers).append("个不存在的教师");
        }
        
        if (sb.length() == 0) {
            sb.append("验证通过");
        }
        
        return sb.toString();
    }
}