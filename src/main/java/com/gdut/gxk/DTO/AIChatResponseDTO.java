package com.gdut.gxk.DTO;

import lombok.Data;
import java.util.List;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.CourseComment;

/**
 * AI对话响应DTO
 */
@Data
public class AIChatResponseDTO {
    
    /**
     * AI回复内容
     */
    private String aiResponse;
    
    /**
     * 对话上下文ID
     */
    private String contextId;
    
    /**
     * 相关的课程数据（如果有）
     */
    private List<CourseBase> relatedCourses;
    
    /**
     * 相关的评论数据（如果有）
     */
    private List<CourseComment> relatedComments;
    
    /**
     * 处理时间（毫秒）
     */
    private Long processingTime;
    
    /**
     * 是否使用了缓存
     */
    private Boolean fromCache = false;
}

