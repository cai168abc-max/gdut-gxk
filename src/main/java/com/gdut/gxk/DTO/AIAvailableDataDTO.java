package com.gdut.gxk.DTO;

import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.SearchKeyword;
import lombok.Data;

import java.util.List;

/**
 * AI可用数据聚合：包含清洗后的查询条件 + 课程候选 + 热度候选
 */
@Data
public class AIAvailableDataDTO {
    /** 清洗后的结构化查询条件 */
    private AIQueryParamDTO cleanedParams;

    /** 按条件查询到的课程（已限条） */
    private List<CourseBase> matchedCourses;

    /** 热门搜索词（当识别到热度意图时返回，否则为空或空列表） */
    private List<SearchKeyword> hotKeywords;

    /** 是否识别到热度意图 */
    private Boolean hotIntent = false;
}


