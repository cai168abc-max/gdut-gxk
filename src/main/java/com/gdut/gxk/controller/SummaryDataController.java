package com.gdut.gxk.controller;

import com.gdut.gxk.DTO.SummaryDataDTO;
import com.gdut.gxk.common.Result;
import com.gdut.gxk.service.SummaryDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

/**
 * 数据统计页控制器（第四页用）
 * 支持课程统计、热门课程、搜索词统计等功能
 */
@RestController
@RequestMapping("/api/summary")
@Slf4j
public class SummaryDataController {

    @Resource
    private SummaryDataService summaryDataService;

    /**
     * 获取总结统计数据
     * @param campus 校区筛选（可选）
     * @param category 课程类别筛选（可选）
     * @return 统计数据（总课程数、总评论数、热门课程、人气课程、高频搜索词）
     */
    @GetMapping("/data")
    public Result<SummaryDataDTO> getSummaryData(
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String category) {
        try {
            log.info("获取总结统计数据，校区：{}，类别：{}", campus, category);
            
            SummaryDataDTO summaryData = summaryDataService.getSummaryData(campus, category);
            return Result.success("获取统计数据成功", summaryData);
            
        } catch (Exception e) {
            log.error("获取总结统计数据失败", e);
            return Result.error("获取统计数据失败：" + e.getMessage());
        }
    }

    /**
     * 获取基础统计信息（轻量级）
     * @param campus 校区筛选（可选）
     * @param category 课程类别筛选（可选）
     * @return 基础统计信息
     */
    @GetMapping("/basic")
    public Result<Object> getBasicStats(
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String category) {
        try {
            log.info("获取基础统计信息，校区：{}，类别：{}", campus, category);
            
            SummaryDataDTO summaryData = summaryDataService.getSummaryData(campus, category);
            
            // 只返回基础统计信息
            final String finalCampus = campus;
            final String finalCategory = category;
            Object basicStats = new Object() {
                public final long totalCourseCount = summaryData.getTotalCourseCount();
                public final long totalCommentCount = summaryData.getTotalCommentCount();
                public final String campus = finalCampus;
                public final String category = finalCategory;
                public final long timestamp = System.currentTimeMillis();
            };
            
            return Result.success("获取基础统计信息成功", basicStats);
            
        } catch (Exception e) {
            log.error("获取基础统计信息失败", e);
            return Result.error("获取基础统计信息失败：" + e.getMessage());
        }
    }

    /**
     * 获取热门课程（按评论数排序）
     * @param campus 校区筛选（可选）
     * @param category 课程类别筛选（可选）
     * @param limit 返回数量限制（默认10）
     * @return 热门课程列表
     */
    @GetMapping("/hot-courses")
    public Result<Object> getHotCourses(
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            log.info("获取热门课程，校区：{}，类别：{}，限制：{}", campus, category, limit);
            
            SummaryDataDTO summaryData = summaryDataService.getSummaryData(campus, category);
            
            // 限制返回数量
            if (limit > 50) {
                limit = 50;
            }
            
            final int finalLimit = limit;
            Object hotCourses = new Object() {
                public final Object courses = summaryData.getHotCoursesByComment().stream()
                        .limit(finalLimit)
                        .toList();
                public final int count = summaryData.getHotCoursesByComment().size();
                public final int limit = finalLimit;
            };
            
            return Result.success("获取热门课程成功", hotCourses);
            
        } catch (Exception e) {
            log.error("获取热门课程失败", e);
            return Result.error("获取热门课程失败：" + e.getMessage());
        }
    }

    /**
     * 获取人气课程（按评分排序）
     * @param campus 校区筛选（可选）
     * @param category 课程类别筛选（可选）
     * @param limit 返回数量限制（默认10）
     * @return 人气课程列表
     */
    @GetMapping("/popular-courses")
    public Result<Object> getPopularCourses(
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            log.info("获取人气课程，校区：{}，类别：{}，限制：{}", campus, category, limit);
            
            SummaryDataDTO summaryData = summaryDataService.getSummaryData(campus, category);
            
            // 限制返回数量
            if (limit > 50) {
                limit = 50;
            }
            
            final int finalLimit2 = limit;
            Object popularCourses = new Object() {
                public final Object courses = summaryData.getPopularCoursesByScore().stream()
                        .limit(finalLimit2)
                        .toList();
                public final int count = summaryData.getPopularCoursesByScore().size();
                public final int limit = finalLimit2;
            };
            log.debug("人气课程数据：{}", popularCourses.toString());
            return Result.success("获取人气课程成功", popularCourses);
            
        } catch (Exception e) {
            log.error("获取人气课程失败", e);
            return Result.error("获取人气课程失败：" + e.getMessage());
        }
    }

    /**
     * 获取高频搜索词
     * @param limit 返回数量限制（默认10）
     * @return 高频搜索词列表
     */
    @GetMapping("/hot-keywords")
    public Result<Object> getHotKeywords(@RequestParam(defaultValue = "10") Integer limit) {
        try {
            log.info("获取高频搜索词，限制：{}", limit);
            
            SummaryDataDTO summaryData = summaryDataService.getSummaryData(null, null);
            
            // 限制返回数量
            if (limit > 50) {
                limit = 50;
            }
            
            final int finalLimit3 = limit;
            Object hotKeywords = new Object() {
                public final Object keywords = summaryData.getTopSearchKeywords().stream()
                        .limit(finalLimit3)
                        .toList();
                public final int count = summaryData.getTopSearchKeywords().size();
                public final int limit = finalLimit3;
            };
            
            return Result.success("获取高频搜索词成功", hotKeywords);
            
        } catch (Exception e) {
            log.error("获取高频搜索词失败", e);
            return Result.error("获取高频搜索词失败：" + e.getMessage());
        }
    }

    /**
     * 获取统计概览（所有统计数据的概览）
     * @return 统计概览信息
     */
    @GetMapping("/overview")
    public Result<Object> getOverview() {
        try {
            log.info("获取统计概览");
            
            SummaryDataDTO summaryData = summaryDataService.getSummaryData(null, null);
            
            Object overview = new Object() {
                public final long totalCourseCount = summaryData.getTotalCourseCount();
                public final long totalCommentCount = summaryData.getTotalCommentCount();
                public final int hotCourseCount = summaryData.getHotCoursesByComment().size();
                public final int popularCourseCount = summaryData.getPopularCoursesByScore().size();
                public final int hotKeywordCount = summaryData.getTopSearchKeywords().size();
                public final long timestamp = System.currentTimeMillis();
            };
            
            return Result.success("获取统计概览成功", overview);
            
        } catch (Exception e) {
            log.error("获取统计概览失败", e);
            return Result.error("获取统计概览失败：" + e.getMessage());
        }
    }
}
