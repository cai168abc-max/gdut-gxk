package com.gdut.gxk.tool;

import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.mapper.CourseBaseMapper;
import com.gdut.gxk.mapper.CourseCommentMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 课程查询工具类
 * 使用Spring AI 1.1.6的@Tool注解方式定义工具
 */
@Component
@Slf4j
public class CourseQueryTool {

    private final CourseBaseMapper courseBaseMapper;
    private final CourseCommentMapper courseCommentMapper;
    private final ObjectMapper objectMapper;

    public CourseQueryTool(CourseBaseMapper courseBaseMapper,
                          CourseCommentMapper courseCommentMapper,
                          ObjectMapper objectMapper) {
        this.courseBaseMapper = courseBaseMapper;
        this.courseCommentMapper = courseCommentMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 搜索课程工具
     * 根据关键词搜索课程信息
     */
    @Tool(description = "根据关键词搜索课程信息，支持课程名、教师名、校区等关键词")
    public String searchCourses(@ToolParam(description = "搜索关键词") String keyword) {
        log.info("调用工具查询课程，关键词：{}", keyword);
        try {
            List<CourseBase> courses = courseBaseMapper.selectByKeyword("%" + keyword + "%");
            
            CourseResult result = new CourseResult();
            if (courses.isEmpty()) {
                result.setCourses(List.of());
                result.setMessage("未找到相关课程");
            } else {
                result.setCourses(courses.stream()
                        .map(this::convertToCourseDTO)
                        .collect(Collectors.toList()));
                result.setMessage("查询成功");
            }
            
            return objectMapper.writeValueAsString(result);
            
        } catch (JsonProcessingException e) {
            log.error("序列化课程结果失败", e);
            return buildErrorResponse("序列化失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("查询课程失败", e);
            return buildErrorResponse("查询失败：" + e.getMessage());
        }
    }

    /**
     * 查询课程评价工具
     * 根据课程ID查询课程评价
     */
    @Tool(description = "根据课程ID查询课程评价信息")
    public String getCourseComments(@ToolParam(description = "课程ID") String courseId) {
        log.info("调用工具查询课程评价，课程ID：{}", courseId);
        try {
            List<CourseComment> comments = courseCommentMapper.selectByCourseId(courseId);
            
            CommentResult result = new CommentResult();
            if (comments.isEmpty()) {
                result.setComments(List.of());
                result.setMessage("该课程暂无评价");
            } else {
                result.setComments(comments.stream()
                        .map(this::convertToCommentDTO)
                        .collect(Collectors.toList()));
                result.setMessage("查询成功");
            }
            
            return objectMapper.writeValueAsString(result);
            
        } catch (JsonProcessingException e) {
            log.error("序列化评价结果失败", e);
            return buildCommentErrorResponse("序列化失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("查询课程评价失败", e);
            return buildCommentErrorResponse("查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取所有课程工具
     */
    @Tool(description = "获取所有课程列表，返回课程的基本信息")
    public String getAllCourses() {
        log.info("调用工具获取所有课程");
        try {
            List<CourseBase> courses = courseBaseMapper.selectList(null);
            
            CourseResult result = new CourseResult();
            if (courses.isEmpty()) {
                result.setCourses(List.of());
                result.setMessage("暂无课程数据");
            } else {
                result.setCourses(courses.stream()
                        .map(this::convertToCourseDTO)
                        .collect(Collectors.toList()));
                result.setMessage("查询成功");
            }
            
            return objectMapper.writeValueAsString(result);
            
        } catch (JsonProcessingException e) {
            log.error("序列化课程结果失败", e);
            return buildErrorResponse("序列化失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("获取所有课程失败", e);
            return buildErrorResponse("查询失败：" + e.getMessage());
        }
    }

    /**
     * 转换CourseBase为DTO
     */
    private CourseDTO convertToCourseDTO(CourseBase course) {
        CourseDTO dto = new CourseDTO();
        dto.setCourseId(course.getCourseId());
        dto.setCourseName(course.getCourseName());
        dto.setTeacherName(course.getTeacherName());
        dto.setCampus(course.getCampus());
        dto.setCategory(course.getCategory());
        return dto;
    }

    /**
     * 转换CourseComment为DTO
     */
    private CommentDTO convertToCommentDTO(CourseComment comment) {
        CommentDTO dto = new CommentDTO();
        dto.setCommentId(comment.getCommentId());
        dto.setCourseId(comment.getCourseId());
        dto.setScore(comment.getScore());
        dto.setContent(comment.getContent());
        dto.setAttendanceFrequency(comment.getAttendanceFrequency());
        dto.setExamType(comment.getExamType());
        dto.setCreateTime(comment.getCreateTime() != null ? comment.getCreateTime().toString() : null);
        return dto;
    }

    /**
     * 构建课程错误响应
     */
    private String buildErrorResponse(String message) {
        try {
            CourseResult result = new CourseResult();
            result.setCourses(List.of());
            result.setMessage(message);
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"courses\": [], \"message\": \"内部错误\"}";
        }
    }

    /**
     * 构建评论错误响应
     */
    private String buildCommentErrorResponse(String message) {
        try {
            CommentResult result = new CommentResult();
            result.setComments(List.of());
            result.setMessage(message);
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"comments\": [], \"message\": \"内部错误\"}";
        }
    }

    // ==================== 内部数据类 ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseResult {
        private List<CourseDTO> courses;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseDTO {
        private String courseId;
        private String courseName;
        private String teacherName;
        private String campus;
        private String category;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentResult {
        private List<CommentDTO> comments;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentDTO {
        private Long commentId;
        private String courseId;
        private Integer score;
        private String content;
        private String attendanceFrequency;
        private String examType;
        private String createTime;
    }
}