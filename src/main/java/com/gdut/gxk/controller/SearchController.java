package com.gdut.gxk.controller;

import com.gdut.gxk.common.Result;
import com.gdut.gxk.entity.SearchKeyword;
import com.gdut.gxk.mapper.SearchKeywordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

import java.util.List;

/**
 * 搜索功能控制器
 * 支持搜索词统计、热门搜索词查询等功能
 */
@RestController
@RequestMapping("/api/search")
@Slf4j
public class SearchController {

    @Resource
    private SearchKeywordMapper searchKeywordMapper;

    /**
     * 记录搜索行为（增加搜索词计数）
     * @param type 搜索词类型（course_name/teacher_name/campus/college/tag/category）
     * @param value 搜索词值
     * @return 记录结果
     */
    @PostMapping("/record")
    public Result<Boolean> recordSearch(
            @RequestParam String type,
            @RequestParam String value) {
        try {
            log.info("记录搜索行为，类型：{}，值：{}", type, value);
            
            // 验证搜索词类型
            List<String> validTypes = List.of("course_name", "teacher_name", "campus", "college", "tag", "category");
            if (!validTypes.contains(type)) {
                return Result.badRequest("无效的搜索词类型");
            }
            
            // 增加搜索计数
            int updated = searchKeywordMapper.incrementCount(type, value);
            
            if (updated > 0) {
                return Result.success("搜索记录成功", true);
            } else {
                // 如果不存在，则插入新记录
                SearchKeyword keyword = new SearchKeyword();
                keyword.setType(type);
                keyword.setValue(value);
                keyword.setCount(1);
                
                int inserted = searchKeywordMapper.insertIfNotExists(keyword);
                return Result.success("搜索记录创建成功", inserted > 0);
            }
            
        } catch (Exception e) {
            log.error("记录搜索行为失败", e);
            return Result.error("记录搜索行为失败：" + e.getMessage());
        }
    }

    /**
     * 获取热门搜索词（按类型）
     * @param type 搜索词类型
     * @param limit 返回数量限制（默认10）
     * @return 热门搜索词列表
     */
    @GetMapping("/hot/{type}")
    public Result<List<SearchKeyword>> getHotKeywordsByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            log.info("获取热门搜索词，类型：{}，限制：{}", type, limit);
            
            // 验证搜索词类型
            List<String> validTypes = List.of("course_name", "teacher_name", "campus", "college", "tag", "category");
            if (!validTypes.contains(type)) {
                return Result.badRequest("无效的搜索词类型");
            }
            
            // 限制返回数量
            if (limit > 50) {
                limit = 50;
            }
            
            List<SearchKeyword> keywords = searchKeywordMapper.selectHotByTypes(List.of(type), limit);
            return Result.success("获取热门搜索词成功", keywords);
            
        } catch (Exception e) {
            log.error("获取热门搜索词失败", e);
            return Result.error("获取热门搜索词失败：" + e.getMessage());
        }
    }

    /**
     * 获取所有类型的热门搜索词
     * @param limit 每种类型返回数量限制（默认5）
     * @return 所有类型的热门搜索词
     */
    @GetMapping("/hot/all")
    public Result<Object> getAllHotKeywords(@RequestParam(defaultValue = "5") Integer limit) {
        try {
            log.info("获取所有类型热门搜索词，限制：{}", limit);
            
            // 限制返回数量
            if (limit > 20) {
                limit = 20;
            }
            
            List<String> allTypes = List.of("course_name", "teacher_name", "campus", "college", "tag", "category");
            List<SearchKeyword> allKeywords = searchKeywordMapper.selectHotByTypes(allTypes, limit);
            
            // 按类型分组
            Object groupedKeywords = new Object() {
                public final Object courseName = allKeywords.stream()
                        .filter(k -> "course_name".equals(k.getType()))
                        .toList();
                public final Object teacherName = allKeywords.stream()
                        .filter(k -> "teacher_name".equals(k.getType()))
                        .toList();
                public final Object campus = allKeywords.stream()
                        .filter(k -> "campus".equals(k.getType()))
                        .toList();
                public final Object college = allKeywords.stream()
                        .filter(k -> "college".equals(k.getType()))
                        .toList();
                public final Object tag = allKeywords.stream()
                        .filter(k -> "tag".equals(k.getType()))
                        .toList();
                public final Object category = allKeywords.stream()
                        .filter(k -> "category".equals(k.getType()))
                        .toList();
            };
            
            return Result.success("获取所有类型热门搜索词成功", groupedKeywords);
            
        } catch (Exception e) {
            log.error("获取所有类型热门搜索词失败", e);
            return Result.error("获取所有类型热门搜索词失败：" + e.getMessage());
        }
    }

    /**
     * 获取搜索统计信息
     * @return 搜索统计信息
     */
    @GetMapping("/stats")
    public Result<Object> getSearchStats() {
        try {
            log.info("获取搜索统计信息");
            
            // 获取各类型的搜索词总数
            List<String> allTypes = List.of("course_name", "teacher_name", "campus", "college", "tag", "category");
            List<SearchKeyword> allKeywords = searchKeywordMapper.selectHotByTypes(allTypes, 1000);
            
            Object stats = new Object() {
                public final long totalKeywords = allKeywords.size();
                public final long totalSearches = allKeywords.stream()
                        .mapToLong(SearchKeyword::getCount)
                        .sum();
                public final Object byType = new Object() {
                    public final long courseName = allKeywords.stream()
                            .filter(k -> "course_name".equals(k.getType()))
                            .count();
                    public final long teacherName = allKeywords.stream()
                            .filter(k -> "teacher_name".equals(k.getType()))
                            .count();
                    public final long campus = allKeywords.stream()
                            .filter(k -> "campus".equals(k.getType()))
                            .count();
                    public final long college = allKeywords.stream()
                            .filter(k -> "college".equals(k.getType()))
                            .count();
                    public final long tag = allKeywords.stream()
                            .filter(k -> "tag".equals(k.getType()))
                            .count();
                    public final long category = allKeywords.stream()
                            .filter(k -> "category".equals(k.getType()))
                            .count();
                };
                public final long timestamp = System.currentTimeMillis();
            };
            
            return Result.success("获取搜索统计信息成功", stats);
            
        } catch (Exception e) {
            log.error("获取搜索统计信息失败", e);
            return Result.error("获取搜索统计信息失败：" + e.getMessage());
        }
    }

    /**
     * 批量记录搜索行为
     * @param searches 搜索记录列表
     * @return 记录结果
     */
    @PostMapping("/record/batch")
    public Result<Object> recordBatchSearch(@RequestBody List<SearchRecord> searches) {
        try {
            log.info("批量记录搜索行为，数量：{}", searches.size());
            
            final int[] successCount = {0};
            final int[] failCount = {0};
            
            for (SearchRecord search : searches) {
                try {
                    // 验证搜索词类型
                    List<String> validTypes = List.of("course_name", "teacher_name", "campus", "college", "tag", "category");
                    if (!validTypes.contains(search.type)) {
                        failCount[0]++;
                        continue;
                    }
                    
                    // 增加搜索计数
                    int updated = searchKeywordMapper.incrementCount(search.type, search.value);
                    
                    if (updated > 0) {
                        successCount[0]++;
                    } else {
                        // 如果不存在，则插入新记录
                        SearchKeyword keyword = new SearchKeyword();
                        keyword.setType(search.type);
                        keyword.setValue(search.value);
                        keyword.setCount(1);
                        
                        int inserted = searchKeywordMapper.insertIfNotExists(keyword);
                        if (inserted > 0) {
                            successCount[0]++;
                        } else {
                            failCount[0]++;
                        }
                    }
                } catch (Exception e) {
                    log.warn("记录单个搜索失败：{}", e.getMessage());
                    failCount[0]++;
                }
            }
            
            Object result = new Object() {
                public final int total = searches.size();
                public final int success = successCount[0];
                public final int failed = failCount[0];
            };
            
            return Result.success("批量记录搜索行为完成", result);
            
        } catch (Exception e) {
            log.error("批量记录搜索行为失败", e);
            return Result.error("批量记录搜索行为失败：" + e.getMessage());
        }
    }

    /**
     * 搜索记录内部类
     */
    public static class SearchRecord {
        public String type;
        public String value;
    }
}
