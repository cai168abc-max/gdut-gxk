package com.gdut.gxk.service;

import com.gdut.gxk.DTO.AIChatContextDTO;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.entity.SearchKeyword;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 缓存服务接口：支持课程/评论/AI上下文/搜索词缓存 */
public interface RedisCacheService {
    // 通用缓存操作
    void set(String key, Object value, long timeout, TimeUnit unit);
    Object get(String key);
    void delete(String key);
    void deletePattern(String pattern); // 模糊删除
    
    // 原子操作（用于限流等场景）
    long increment(String key);
    long increment(String key, long delta);
    long increment(String key, long delta, long timeout, TimeUnit unit);

    // 课程缓存（第一/二页用）
    void cacheCourseInfo(CourseBase course);
    CourseBase getCachedCourseInfo(String courseId);
    void deleteCourseCache(String courseId);
    void cacheEmptyCourse(String courseId);

    // 评论缓存（第二页用）
    void cacheCommentList(String courseId, List<CourseComment> comments);
    List<CourseComment> getCachedComments(String courseId);
    void deleteCommentCache(String courseId);
    void cacheEmptyComments(String courseId);

    // 搜索词缓存（第四页用）
    void cacheHotKeywords(String type, int limit, List<SearchKeyword> keywords);
    List<SearchKeyword> getCachedHotKeywords(String type, int limit);
    void deleteHotKeywordCache(String type, int limit);

    // AI对话上下文缓存（第三页用，Caﬀeine，30分钟过期）
    void cacheAIChatContext(String sessionId, AIChatContextDTO context);
    AIChatContextDTO getAIChatContext(String sessionId);
    void deleteAIChatContext(String sessionId);
}