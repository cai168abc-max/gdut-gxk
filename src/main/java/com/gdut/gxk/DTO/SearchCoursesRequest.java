package com.gdut.gxk.DTO;

import lombok.Data;

/**
 * 课程搜索工具请求DTO
 * 用于接收AI模型生成的工具调用参数
 */
@Data
public class SearchCoursesRequest {
    
    /**
     * 搜索关键词
     * 支持课程名、教师名、校区等关键词
     */
    private String keyword;
}