package com.gdut.gxk.DTO;

import lombok.Data;

/**
 * 获取课程评价工具请求DTO
 * 用于接收AI模型生成的工具调用参数
 */
@Data
public class GetCourseCommentsRequest {
    
    /**
     * 课程ID
     */
    private String courseId;
}