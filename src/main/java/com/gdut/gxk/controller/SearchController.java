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
 * 支持搜索词记录、热门搜索词查询等功能
 *
 * search_keyword表实际列名：keyword_id, type, value, count
 */
@RestController
@RequestMapping("/api/search")
@Slf4j
public class SearchController {

    @Resource
    private SearchKeywordMapper searchKeywordMapper;

    /**
     * 记录搜索行为（插入搜索词，已存在则递增计数）
     * @param keyword 搜索关键词
     * @param type 搜索词类型（general/campus/category/tag）
     * @return 记录结果
     */
    @PostMapping("/record")
    public Result<Boolean> recordSearch(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "general") String type) {
        try {
            log.info("记录搜索行为，关键词：{}，类型：{}", keyword, type);

            int inserted = searchKeywordMapper.insertIfNotExists(keyword, type);
            if (inserted == 0) {
                // 已存在，递增计数
                searchKeywordMapper.incrementCount(keyword);
            }
            return Result.success("搜索记录成功", true);
        } catch (Exception e) {
            log.error("记录搜索行为失败", e);
            return Result.error("记录搜索行为失败，请稍后重试");
        }
    }

    /**
     * 获取热门搜索词（按类型）
     * @param type 搜索词类型（general/campus/category/tag）
     * @param limit 返回数量限制（默认10）
     * @return 热门搜索词列表
     */
    @GetMapping("/hot/{type}")
    public Result<List<SearchKeyword>> getHotKeywordsByType(
            @PathVariable String type,
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            log.info("获取热门搜索词，类型：{}，限制：{}", type, limit);

            if (limit > 50) limit = 50;

            List<SearchKeyword> keywords = searchKeywordMapper.selectHotByTypes(List.of(type), limit);
            return Result.success("获取热门搜索词成功", keywords);
        } catch (Exception e) {
            log.error("获取热门搜索词失败", e);
            return Result.error("获取热门搜索词失败，请稍后重试");
        }
    }

    /**
     * 获取所有类型的热门搜索词
     * @param limit 每种类型返回数量限制（默认5）
     * @return 所有类型的热门搜索词
     */
    @GetMapping("/hot/all")
    public Result<List<SearchKeyword>> getAllHotKeywords(@RequestParam(defaultValue = "5") Integer limit) {
        try {
            log.info("获取所有类型热门搜索词，限制：{}", limit);

            if (limit > 20) limit = 20;

            List<String> allTypes = List.of("general", "campus", "category", "tag");
            List<SearchKeyword> allKeywords = searchKeywordMapper.selectHotByTypes(allTypes, limit);
            return Result.success("获取所有类型热门搜索词成功", allKeywords);
        } catch (Exception e) {
            log.error("获取所有类型热门搜索词失败", e);
            return Result.error("获取所有类型热门搜索词失败，请稍后重试");
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

            List<String> allTypes = List.of("general", "campus", "category", "tag");
            List<SearchKeyword> allKeywords = searchKeywordMapper.selectHotByTypes(allTypes, 1000);

            Object stats = new Object() {
                public final long totalKeywords = allKeywords.size();
                public final long generalCount = allKeywords.stream().filter(k -> "general".equals(k.getType())).count();
                public final long campusCount = allKeywords.stream().filter(k -> "campus".equals(k.getType())).count();
                public final long categoryCount = allKeywords.stream().filter(k -> "category".equals(k.getType())).count();
                public final long tagCount = allKeywords.stream().filter(k -> "tag".equals(k.getType())).count();
                public final long timestamp = System.currentTimeMillis();
            };

            return Result.success("获取搜索统计信息成功", stats);
        } catch (Exception e) {
            log.error("获取搜索统计信息失败", e);
            return Result.error("获取搜索统计信息失败，请稍后重试");
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
                    String type = search.type != null ? search.type : "general";
                    int inserted = searchKeywordMapper.insertIfNotExists(search.keyword, type);
                    if (inserted == 0) {
                        searchKeywordMapper.incrementCount(search.keyword);
                    }
                    successCount[0]++;
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
            return Result.error("批量记录搜索行为失败，请稍后重试");
        }
    }

    /**
     * 搜索记录内部类
     */
    public static class SearchRecord {
        public String type;
        public String keyword;
    }
}
