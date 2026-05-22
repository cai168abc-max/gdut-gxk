package com.gdut.gxk.tool;

import com.gdut.gxk.DTO.GetCourseCommentsRequest;
import com.gdut.gxk.DTO.SearchCoursesRequest;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.mapper.CourseBaseMapper;
import com.gdut.gxk.mapper.CourseCommentMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程查询工具类
 * 使用Spring AI 1.1.x的@Tool注解方式定义工具
 * Spring AI 1.1.x支持直接返回对象，自动进行JSON序列化
 */
@Component
@Slf4j
public class CourseQueryTool {

    private final CourseBaseMapper courseBaseMapper;
    private final CourseCommentMapper courseCommentMapper;

    /**
     * 广工校区关键词 → 标准校区名映射
     */
    private static final Set<String> CAMPUS_KEYWORDS = Set.of(
            "大学城", "东风路", "龙洞", "揭阳", "番禺",
            "大学城校区", "东风路校区", "龙洞校区", "揭阳校区", "番禺校区"
    );

    public CourseQueryTool(CourseBaseMapper courseBaseMapper,
                          CourseCommentMapper courseCommentMapper) {
        this.courseBaseMapper = courseBaseMapper;
        this.courseCommentMapper = courseCommentMapper;
    }

    /**
     * 搜索课程工具
     * 根据关键词搜索课程信息
     *
     * @param request 搜索请求对象
     * @return 课程查询结果对象（Spring AI自动序列化）
     */
    @Tool(description = "根据关键词搜索课程信息，支持课程名、教师名、校区等关键词")
    public CourseResult searchCourses(SearchCoursesRequest request) {
        String keyword = request != null ? request.getKeyword() : null;
        log.info("调用工具查询课程，关键词：{}", keyword);
        try {
            if (keyword == null || keyword.trim().isEmpty()) {
                return new CourseResult(Collections.emptyList(), "关键词不能为空", false, null, null);
            }

            String trimmed = keyword.trim();

            // 1. 先按课程名/教师名/标签模糊搜索
            List<CourseBase> courses = courseBaseMapper.selectByKeyword("%" + trimmed + "%");

            // 2. 如果模糊搜索无结果，且关键词是校区名，则按校区精确查询
            if (courses.isEmpty() && CAMPUS_KEYWORDS.contains(trimmed)) {
                String campusName = resolveCampusName(trimmed);
                courses = courseBaseMapper.selectByCampus(campusName);
                if (!courses.isEmpty()) {
                    List<CourseDTO> courseDTOs = courses.stream()
                            .map(this::convertToCourseDTO)
                            .collect(Collectors.toList());
                    StringBuilder formattedMessage = new StringBuilder("[工具搜索结果]\n");
                    formattedMessage.append("课程列表（共").append(courses.size()).append("门）：\n");
                    for (int i = 0; i < courses.size(); i++) {
                        CourseDTO dto = courseDTOs.get(i);
                        formattedMessage.append(i + 1).append(". 《")
                                .append(dto.getCourseName()).append("》 - ")
                                .append(dto.getTeacherName()).append(" - ")
                                .append(dto.getCategory()).append(" - ")
                                .append(dto.getCampus()).append("\n");
                    }
                    String hint = courses.size() <= 5
                            ? "课程数量不多，请主动对比课程特色并推荐 1-2 门，给出具体理由。"
                            : "课程较多，请挑选最热门或评分最高的 2-3 门推荐。";
                    return new CourseResult(courseDTOs, formattedMessage.toString(), false, "CAMPUS_QUERY", hint);
                }
                return new CourseResult(Collections.emptyList(),
                        "[工具搜索结果]\n关键词'" + trimmed + "'是校区名（" + campusName + "），但该校区暂无课程数据。请引导用户提供具体课程名称。",
                        true, "CAMPUS_EMPTY", "请引导用户说出具体想查的课程名或教师名。");
            }

            // 3. 模糊搜索有结果
            if (!courses.isEmpty()) {
                List<CourseDTO> courseDTOs = courses.stream()
                        .map(this::convertToCourseDTO)
                        .collect(Collectors.toList());
                StringBuilder formattedMessage = new StringBuilder("[工具搜索结果]\n");
                formattedMessage.append("课程列表（共").append(courses.size()).append("门）：\n");
                for (int i = 0; i < courses.size(); i++) {
                    CourseDTO dto = courseDTOs.get(i);
                    formattedMessage.append(i + 1).append(". 《")
                            .append(dto.getCourseName()).append("》 - ")
                            .append(dto.getTeacherName()).append(" - ")
                            .append(dto.getCategory()).append(" - ")
                            .append(dto.getCampus()).append("\n");
                }
                String hint;
                if (courses.size() <= 3) {
                    hint = "课程数量很少，请直接列出并对比特色，主动推荐其中 1-2 门，给出理由。如果用户后续说'不知道'或'推荐'，基于这些课程推荐。";
                } else if (courses.size() <= 10) {
                    hint = "课程数量适中，请列出主要课程，推荐 2-3 门热门课程。";
                } else {
                    hint = "课程数量较多，请挑选最相关或评分最高的 3-5 门推荐，不要全部列出。";
                }
                return new CourseResult(courseDTOs, formattedMessage.toString(), false, null, hint);
            }

            // 4. 模糊搜索无结果，且不是校区名
            return new CourseResult(Collections.emptyList(),
                    "[工具搜索结果]\n未找到与'" + trimmed + "'相关的课程。可能原因：1) 课程名称略有不同 2) 该课程本学期暂未开课。请引导用户尝试更具体的关键词、教师姓名或所属学院。",
                    true, "NO_RESULT", "请引导用户提供更具体的关键词，如课程全名、教师姓名、所属学院等。");

        } catch (Exception e) {
            log.error("查询课程失败", e);
            return new CourseResult(Collections.emptyList(), "查询失败：" + e.getMessage(), true, "ERROR", null);
        }
    }

    /**
     * 查询课程评价工具
     * 根据课程ID查询课程评价
     *
     * @param request 查询请求对象
     * @return 评价查询结果对象
     */
    @Tool(description = "根据课程ID查询课程评价信息")
    public CommentResult getCourseComments(GetCourseCommentsRequest request) {
        String courseId = request != null ? request.getCourseId() : null;
        log.info("调用工具查询课程评价，课程ID：{}", courseId);
        try {
            if (courseId == null || courseId.trim().isEmpty()) {
                return new CommentResult(Collections.emptyList(), "课程ID不能为空");
            }
            List<CourseComment> comments = courseCommentMapper.selectByCourseId(courseId);

            if (comments.isEmpty()) {
                return new CommentResult(Collections.emptyList(), "该课程暂无评价");
            }

            List<CommentDTO> commentDTOs = comments.stream()
                    .map(this::convertToCommentDTO)
                    .collect(Collectors.toList());

            return new CommentResult(commentDTOs, "查询成功");

        } catch (Exception e) {
            log.error("查询课程评价失败", e);
            return new CommentResult(Collections.emptyList(), "查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取所有课程工具
     *
     * @return 所有课程列表
     */
    @Tool(description = "获取所有课程列表，返回课程的基本信息")
    public CourseResult getAllCourses() {
        log.info("调用工具获取所有课程");
        try {
            List<CourseBase> courses = courseBaseMapper.selectList(null);

            if (courses.isEmpty()) {
                return new CourseResult(Collections.emptyList(), "暂无课程数据", false, null, null);
            }

            List<CourseDTO> courseDTOs = courses.stream()
                    .map(this::convertToCourseDTO)
                    .collect(Collectors.toList());

            return new CourseResult(courseDTOs, "查询成功", false, null,
                    "课程数量较多，请挑选最相关或评分最高的3-5门推荐，不要全部列出。");

        } catch (Exception e) {
            log.error("获取所有课程失败", e);
            return new CourseResult(Collections.emptyList(), "查询失败：" + e.getMessage(), true, "ERROR", null);
        }
    }

    /**
     * 解析校区关键词为标准校区名
     */
    private String resolveCampusName(String keyword) {
        return switch (keyword) {
            case "大学城", "大学城校区" -> "广东工业大学大学城校区";
            case "东风路", "东风路校区" -> "广东工业大学东风路校区";
            case "龙洞", "龙洞校区" -> "广东工业大学龙洞校区";
            case "揭阳", "揭阳校区" -> "广东工业大学揭阳校区";
            case "番禺", "番禺校区" -> "广东工业大学番禺校区";
            default -> keyword;
        };
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

    // ==================== 内部数据类 ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseResult {
        private List<CourseDTO> courses;
        private String message;
        /** 是否需要Agent进一步引导用户（空结果/校区名等场景） */
        private boolean needClarification;
        /** 结果类型标记：CAMPUS_QUERY=校区查询, CAMPUS_EMPTY=校区无数据, NO_RESULT=无结果, ERROR=错误 */
        private String resultType;
        /** 给Agent的推荐提示，引导Agent基于结果做出更智能的回复 */
        private String suggestionHint;
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