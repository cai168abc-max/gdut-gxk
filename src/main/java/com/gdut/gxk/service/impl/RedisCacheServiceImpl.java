package com.gdut.gxk.service.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.gdut.gxk.DTO.AIChatContextDTO;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.entity.SearchKeyword;
import com.gdut.gxk.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** 缓存服务实现：Redis（二级缓存）+ Caffeine（AI上下文一级缓存） */
@Service
@Slf4j
public class RedisCacheServiceImpl implements RedisCacheService {
    // 缓存常量：Key前缀 + 过期时间（秒）
    private static final String COURSE_INFO_KEY = "redis:course:info:";
    private static final long COURSE_EXPIRE = 35 * 60; // 35分钟
    private static final String COMMENT_LIST_KEY = "redis:comment:list:";
    private static final long COMMENT_EXPIRE = 10 * 60; // 10分钟
    private static final String HOT_KEYWORD_KEY = "redis:keyword:hot:";
    private static final long KEYWORD_EXPIRE = 70 * 60; // 70分钟
    private static final String EMPTY_MARKER = "EMPTY";
    private static final long EMPTY_EXPIRE = 60; // 空结果1分钟（防穿透）

    // AI上下文Caffeine缓存（30分钟过期，最大1000个会话）
    private final LoadingCache<String, AIChatContextDTO> aiContextCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build(key -> {
                AIChatContextDTO emptyContext = new AIChatContextDTO();
                emptyContext.setLastUpdateTime(System.currentTimeMillis());
                return emptyContext;
            }); // 未命中时返回初始化时间的空上下文

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // ==================== 通用缓存操作 ====================
    @Override
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        if (key == null || value == null) {
            log.warn("通用缓存设置失败：key或value为空（key={}, value={}", key, value);
            return;
        }
        try {
            getValueOps().set(key, value, timeout, unit);
            log.debug("通用缓存设置成功：key={}, 过期时间={}{}", key, timeout, unit);
        } catch (Exception e) {
            log.error("通用缓存设置异常：key={}", key, e);
        }
    }

    @Override
    public Object get(String key) {
        if (key == null) {
            log.warn("获取缓存失败：key为空");
            return null;
        }
        try {
            Object value = getValueOps().get(key);
            log.trace("获取缓存：key={}, 是否命中={}", key, value != null);
            return value;
        } catch (Exception e) {
            log.error("获取缓存异常：key={}", key, e);
            return null;
        }
    }

    @Override
    public void delete(String key) {
        if (key == null) {
            log.warn("删除缓存失败：key为空");
            return;
        }
        try {
            boolean isDeleted = redisTemplate.delete(key);
            log.debug("删除缓存：key={}, 结果={}", key, isDeleted);
        } catch (Exception e) {
            log.error("删除缓存异常：key={}", key, e);
        }
    }

    @Override
    public void deletePattern(String pattern) {
        if (pattern == null) {
            log.warn("模糊删除缓存失败：pattern为空");
            return;
        }
        try {
            // 使用SCAN替代KEYS，避免阻塞Redis
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(1000).build();
            Long deletedCount = redisTemplate.execute((RedisCallback<Long>) connection -> {
                long count = 0;
                try (var cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        count += connection.del(cursor.next());
                    }
                }
                return count;
            });
            log.debug("模糊删除缓存完成：pattern={}, 删除键数量={}", pattern, deletedCount);
        } catch (Exception e) {
            log.error("模糊删除缓存异常：pattern={}", pattern, e);
        }
    }

    // ==================== 课程缓存 ====================
    @Override
    public void cacheCourseInfo(CourseBase course) {
        if (course == null || course.getCourseId() == null) {
            log.warn("缓存课程失败：课程或courseId为空（course={}", course);
            return;
        }
        String key = COURSE_INFO_KEY + course.getCourseId();
        try {
            getValueOps().set(key, course, COURSE_EXPIRE, TimeUnit.SECONDS);
            log.debug("缓存课程成功：key={}, courseId={}", key, course.getCourseId());
        } catch (Exception e) {
            log.error("缓存课程异常：courseId={}", course.getCourseId(), e);
        }
    }

    @Override
    public CourseBase getCachedCourseInfo(String courseId) {
        if (courseId == null) {
            log.warn("获取课程缓存失败：courseId为空");
            return null;
        }
        return getSingleCache(COURSE_INFO_KEY + courseId, CourseBase.class);
    }

    @Override
    public void deleteCourseCache(String courseId) {
        if (courseId == null) {
            log.warn("删除课程缓存失败：courseId为空");
            return;
        }
        deleteCache(COURSE_INFO_KEY + courseId, "课程");
    }

    @Override
    public void cacheEmptyCourse(String courseId) {
        if (courseId == null) {
            log.warn("缓存空课程失败：courseId为空");
            return;
        }
        cacheEmptyValue(COURSE_INFO_KEY + courseId, "课程");
    }

    // ==================== 评论缓存 ====================
    @Override
    public void cacheCommentList(String courseId, List<CourseComment> comments) {
        if (courseId == null || comments == null) {
            log.warn("缓存评论失败：courseId或评论列表为空（courseId={}, comments={}", courseId, comments);
            return;
        }
        String key = COMMENT_LIST_KEY + courseId;
        try {
            getValueOps().set(key, comments, COMMENT_EXPIRE, TimeUnit.SECONDS);
            log.debug("缓存评论成功：key={}, courseId={}, 评论数={}", key, courseId, comments.size());
        } catch (Exception e) {
            log.error("缓存评论异常：courseId={}", courseId, e);
        }
    }

    @Override
    public List<CourseComment> getCachedComments(String courseId) {
        if (courseId == null) {
            log.warn("获取评论缓存失败：courseId为空");
            return null;
        }
        return getListCache(COMMENT_LIST_KEY + courseId, CourseComment.class);
    }

    @Override
    public void deleteCommentCache(String courseId) {
        if (courseId == null) {
            log.warn("删除评论缓存失败：courseId为空");
            return;
        }
        deleteCache(COMMENT_LIST_KEY + courseId, "评论");
    }

    @Override
    public void cacheEmptyComments(String courseId) {
        if (courseId == null) {
            log.warn("缓存空评论失败：courseId为空");
            return;
        }
        cacheEmptyValue(COMMENT_LIST_KEY + courseId, "评论");
    }

    // ==================== 搜索词缓存 ====================
    @Override
    public void cacheHotKeywords(String type, int limit, List<SearchKeyword> keywords) {
        if (type == null || keywords == null) {
            log.warn("缓存热门搜索词失败：type或关键词为空（type={}, keywords={}", type, keywords);
            return;
        }
        String key = HOT_KEYWORD_KEY + type + ":" + limit;
        try {
            getValueOps().set(key, keywords, KEYWORD_EXPIRE, TimeUnit.SECONDS);
            log.debug("缓存热门搜索词成功：key={}, 数量={}", key, keywords.size());
        } catch (Exception e) {
            log.error("缓存热门搜索词异常：type={}, limit={}", type, limit, e);
        }
    }

    @Override
    public List<SearchKeyword> getCachedHotKeywords(String type, int limit) {
        if (type == null) {
            log.warn("获取热门搜索词缓存失败：type为空");
            return null;
        }
        String key = HOT_KEYWORD_KEY + type + ":" + limit;
        return getListCache(key, SearchKeyword.class);
    }

    @Override
    public void deleteHotKeywordCache(String type, int limit) {
        if (type == null) {
            log.warn("删除热门搜索词缓存失败：type为空");
            return;
        }
        String key = HOT_KEYWORD_KEY + type + ":" + limit;
        deleteCache(key, "热门搜索词");
    }

    // ==================== AI对话上下文缓存（Caffeine实现） ====================
    @Override
    public void cacheAIChatContext(String sessionId, AIChatContextDTO context) {
        if (sessionId == null || context == null) {
            log.warn("缓存AI上下文失败：sessionId或context为空（sessionId={}, context={}", sessionId, context);
            return;
        }
        try {
            context.setLastUpdateTime(System.currentTimeMillis());
            aiContextCache.put(sessionId, context);
            log.debug("缓存AI上下文成功：sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("缓存AI上下文异常：sessionId={}", sessionId, e);
        }
    }

    @Override
    public AIChatContextDTO getAIChatContext(String sessionId) {
        if (sessionId == null) {
            log.warn("获取AI上下文失败：sessionId为空");
            return new AIChatContextDTO();
        }
        try {
            return aiContextCache.get(sessionId);
        } catch (Exception e) {
            log.error("获取AI上下文执行异常：sessionId={}", sessionId, e);
            return new AIChatContextDTO();
        }
    }

    @Override
    public void deleteAIChatContext(String sessionId) {
        if (sessionId == null) {
            log.warn("删除AI上下文失败：sessionId为空");
            return;
        }
        try {
            aiContextCache.invalidate(sessionId);
            log.debug("删除AI上下文成功：sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("删除AI上下文异常：sessionId={}", sessionId, e);
        }
    }

    // ==================== 原子操作 ====================
    @Override
    public long increment(String key) {
        return increment(key, 1);
    }

    @Override
    public long increment(String key, long delta) {
        if (key == null) {
            log.warn("原子递增失败：key为空");
            return -1;
        }
        try {
            Long result = getValueOps().increment(key, delta);
            log.debug("原子递增成功：key={}, delta={}, result={}", key, delta, result);
            return result != null ? result : -1;
        } catch (Exception e) {
            log.error("原子递增异常：key={}", key, e);
            return -1;
        }
    }

    @Override
    public long increment(String key, long delta, long timeout, TimeUnit unit) {
        if (key == null) {
            log.warn("原子递增失败：key为空");
            return -1;
        }
        try {
            Long result = getValueOps().increment(key, delta);
            if (result != null && result == delta) {
                // 首次设置，设置过期时间
                redisTemplate.expire(key, timeout, unit);
                log.debug("原子递增并设置过期时间：key={}, delta={}, timeout={}{}", key, delta, timeout, unit);
            }
            log.debug("原子递增成功：key={}, delta={}, result={}", key, delta, result);
            return result != null ? result : -1;
        } catch (Exception e) {
            log.error("原子递增异常：key={}", key, e);
            return -1;
        }
    }

    // ==================== 工具方法 ====================
    private ValueOperations<String, Object> getValueOps() {
        return redisTemplate.opsForValue();
    }

    private <T> T getSingleCache(String key, Class<T> clazz) {
        if (key == null || clazz == null) {
            log.warn("单值缓存获取失败：key或类型为空（key={}, clazz={}", key, clazz);
            return null;
        }
        Object value = get(key);
        if (value == null || EMPTY_MARKER.equals(value)) {
            log.trace("单值缓存未命中或为空标记：key={}", key);
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        } else {
            log.error("单值缓存类型不匹配：key={}, 预期类型={}, 实际类型={}",
                    key, clazz.getName(), value.getClass().getName());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getListCache(String key, Class<T> elementType) {
        if (key == null || elementType == null) {
            log.warn("列表缓存获取失败：key或元素类型为空（key={}, elementType={}", key, elementType);
            return null;
        }
        Object value = get(key);
        if (value == null || EMPTY_MARKER.equals(value)) {
            log.trace("列表缓存未命中或为空标记：key={}", key);
            return null;
        }
        if (!(value instanceof List)) {
            log.error("列表缓存类型不匹配：key={}, 实际类型={}（预期List）", key, value.getClass().getName());
            return null;
        }
        List<?> rawList = (List<?>) value;
        if (!rawList.isEmpty()) {
            Object firstElement = rawList.get(0);
            if (!elementType.isInstance(firstElement)) {
                log.error("列表元素类型不匹配：key={}, 预期元素类型={}, 实际首个元素类型={}",
                        key, elementType.getName(), firstElement.getClass().getName());
                return null;
            }
        }
        return (List<T>) rawList;
    }

    private void deleteCache(String key, String cacheType) {
        if (key == null || cacheType == null) {
            log.warn("删除缓存失败：key或缓存类型为空（key={}, cacheType={}", key, cacheType);
            return;
        }
        try {
            boolean isDeleted = redisTemplate.delete(key);
            log.debug("删除{}缓存：key={}, 结果={}", cacheType, key, isDeleted);
        } catch (Exception e) {
            log.error("删除{}缓存异常：key={}", cacheType, key, e);
        }
    }

    private void cacheEmptyValue(String key, String cacheType) {
        if (key == null || cacheType == null) {
            log.warn("缓存空值失败：key或缓存类型为空（key={}, cacheType={}", key, cacheType);
            return;
        }
        try {
            getValueOps().set(key, EMPTY_MARKER, EMPTY_EXPIRE, TimeUnit.SECONDS);
            log.debug("缓存{}空结果成功：key={}, 过期时间={}秒", cacheType, key, EMPTY_EXPIRE);
        } catch (Exception e) {
            log.error("缓存{}空值异常：key={}", cacheType, key, e);
        }
    }
}