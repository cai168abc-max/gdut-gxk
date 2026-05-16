package com.gdut.gxk.DTO;

import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.SearchKeyword;
import lombok.Data;

import java.util.List;

/** 总结数据页返回（第四页用） */
@Data
public class SummaryDataDTO {
    // 基础统计（校区+课程类型筛选）
    private long totalCourseCount;  // 总课程数
    private long totalCommentCount; // 总评论人数
    // 热门课程（评论最多前10）
    private List<CourseBase> hotCoursesByComment;
    // 人气课程（评分最高前10）
    private List<CourseBase> popularCoursesByScore;
    // 高频搜索词（前10，按查询次数降序）
    private List<SearchKeyword> topSearchKeywords;

}
