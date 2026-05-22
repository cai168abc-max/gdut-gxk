package com.gdut.gxk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gdut.gxk.DTO.SummaryDataDTO;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.entity.SearchKeyword;
import com.gdut.gxk.mapper.CourseBaseMapper;
import com.gdut.gxk.mapper.CourseCommentMapper;
import com.gdut.gxk.mapper.SearchKeywordMapper;
import com.gdut.gxk.service.SummaryDataService;
import com.gdut.gxk.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 总结数据页服务实现 */
@Service
@Slf4j
public class SummaryDataServiceImpl implements SummaryDataService {

    @Resource
    private CourseBaseMapper courseBaseMapper;

    @Resource
    private CourseCommentMapper commentMapper;

    @Resource
    private SearchKeywordMapper keywordMapper;

    @Resource
    private RedisCacheService redisCacheService;

    // 总结数据缓存Key前缀（Redis，1小时过期）
    private static final String SUMMARY_DATA_CACHE_KEY = "redis:summary:data:";
    private static final long SUMMARY_DATA_EXPIRE = 60 * 60;

    @Override
    public SummaryDataDTO getSummaryData(String campus, String category) {
        // 1. 生成缓存Key
        String cacheKey = generateCacheKey(campus, category);
        log.debug("获取总结数据，category={}, campus={}", category, campus);
        // 2. 查缓存
        SummaryDataDTO cachedSummary = (SummaryDataDTO) redisCacheService.get(cacheKey);
        if (cachedSummary != null) {
            log.debug("总结数据缓存命中，cacheKey={}", cacheKey);
            return cachedSummary;
        }

        // 3. 计算统计数据
        SummaryDataDTO summary = new SummaryDataDTO();
        LambdaQueryWrapper<CourseBase> courseQuery = new LambdaQueryWrapper<>();
        // 筛选：校区+课程类型
        if (campus != null && !campus.trim().isEmpty()) {
            courseQuery.eq(CourseBase::getCampus, campus.trim());
        }
        if (category != null && !category.trim().isEmpty()) {
            courseQuery.eq(CourseBase::getCategory, category.trim());
        }

        // 3.1 总课程数
        long totalCourseCount = courseBaseMapper.selectCount(courseQuery);
        summary.setTotalCourseCount(totalCourseCount);

        // 3.2 总评论数
        long totalCommentCount = 0;
        if (totalCourseCount > 0) {
            List<String> courseIds = courseBaseMapper.selectList(courseQuery).stream()
                    .map(CourseBase::getCourseId).toList();
            LambdaQueryWrapper<CourseComment> commentQuery = new LambdaQueryWrapper<>();
            commentQuery.in(CourseComment::getCourseId, courseIds);
            totalCommentCount = commentMapper.selectCount(commentQuery);
        }
        summary.setTotalCommentCount(totalCommentCount);
        int limit = 10;
        // 3.3 热门课程（评论最多前10）
        List<CourseBase> hotCourses = courseBaseMapper.selectHotCoursesByComment(
                campus, category, limit);
        summary.setHotCoursesByComment(hotCourses);
        // 3.4 人气课程（评分最高前10）
        LambdaQueryWrapper<CourseBase> popularQuery = courseQuery.clone();

        popularQuery
                .eq (campus != null,CourseBase::getCampus, campus)
                .eq (category != null,CourseBase::getCategory, category)
                .orderByDesc(CourseBase::getScore)
                .last("LIMIT 10");
        List<CourseBase> popularCourses = courseBaseMapper.selectList(popularQuery);
        summary.setPopularCoursesByScore(popularCourses);
        // 3.5 高频搜索词（按搜索次数倒序取前10）
        LambdaQueryWrapper<SearchKeyword> keywordQuery = new LambdaQueryWrapper<>();
        keywordQuery.orderByDesc(SearchKeyword::getCount)
                .last("LIMIT 10");
        List<SearchKeyword> topKeywords = keywordMapper.selectList(keywordQuery);
        summary.setTopSearchKeywords(topKeywords);

        // 4. 写入缓存
        redisCacheService.set(cacheKey, summary, SUMMARY_DATA_EXPIRE, TimeUnit.SECONDS);
        log.debug("总结数据计算完成，校区={}，类型={}，总课程数={}",
                campus, category, totalCourseCount);

        return summary;
    }

    /** 生成缓存Key：处理空筛选条件 */
    private String generateCacheKey(String campus, String category) {
        String campusStr = (campus == null || campus.trim().isEmpty()) ? "ALL" : campus.trim();
        String categoryStr = (category == null || category.trim().isEmpty()) ? "ALL" : category.trim();
        return SUMMARY_DATA_CACHE_KEY + "campus=" + campusStr + "&category=" + categoryStr;
    }
}
