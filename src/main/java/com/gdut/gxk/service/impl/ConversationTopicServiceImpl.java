package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.ConversationTopicService;
import com.gdut.gxk.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 对话主题追踪服务实现
 * 使用简单的关键词匹配和相似度算法实现主题追踪
 */
@Service
@Slf4j
public class ConversationTopicServiceImpl implements ConversationTopicService {

    @Resource
    private RedisCacheService redisCacheService;

    private static final String TOPIC_KEY_PREFIX = "ai:chat:topic:";
    private static final long TOPIC_EXPIRE_SECONDS = 3600;

    /**
     * 课程相关关键词字典
     */
    private static final Set<String> COURSE_KEYWORDS = Set.of(
            "课程", "选课", "公选", "必修", "选修", "上课", "老师", "教师",
            "校区", "时间", "评分", "评价", "作业", "考试", "考勤",
            "推荐", "难度", "学分", "绩点", "期末", "答辩", "项目"
    );

    /**
     * 主题变化相似度阈值
     */
    private static final double TOPIC_CHANGE_THRESHOLD = 0.5;

    @Override
    public List<String> analyzeTopic(String conversationId, String message) {
        if (message == null || message.isEmpty()) {
            return List.of();
        }

        List<String> topics = new ArrayList<>();
        
        // 使用简单的关键词匹配
        for (String keyword : COURSE_KEYWORDS) {
            if (message.contains(keyword)) {
                topics.add(keyword);
            }
        }

        // 提取课程名（假设课程名是2-8个汉字的连续字符串）
        Pattern courseNamePattern = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}");
        var matcher = courseNamePattern.matcher(message);
        while (matcher.find()) {
            String candidate = matcher.group();
            // 过滤常见词
            if (!isCommonWord(candidate) && !topics.contains(candidate)) {
                topics.add(candidate);
            }
        }

        log.debug("分析对话主题 - conversationId: {}, topics: {}", conversationId, topics);
        return topics;
    }

    @Override
    public boolean isTopicChanged(String conversationId, List<String> newTopics) {
        List<String> currentTopics = getCurrentTopic(conversationId);
        
        if (currentTopics.isEmpty()) {
            // 没有历史主题，不算变化
            return false;
        }

        if (newTopics.isEmpty()) {
            // 新消息没有主题，可能是继续之前的对话
            return false;
        }

        // 计算主题相似度
        double similarity = calculateTopicSimilarity(currentTopics, newTopics);
        boolean changed = similarity < TOPIC_CHANGE_THRESHOLD;
        
        log.debug("主题变化检测 - conversationId: {}, 相似度: {}, 是否变化: {}", 
                conversationId, similarity, changed);
        return changed;
    }

    @Override
    public void updateTopic(String conversationId, List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return;
        }

        String key = TOPIC_KEY_PREFIX + conversationId;
        String topicsJson = String.join(",", topics);
        redisCacheService.set(key, topicsJson, TOPIC_EXPIRE_SECONDS, TimeUnit.SECONDS);
        
        log.debug("更新对话主题 - conversationId: {}, topics: {}", conversationId, topics);
    }

    @Override
    public List<String> getCurrentTopic(String conversationId) {
        String key = TOPIC_KEY_PREFIX + conversationId;
        Object result = redisCacheService.get(key);
        String topicsJson = result != null ? result.toString() : null;
        
        if (topicsJson == null || topicsJson.isEmpty()) {
            return List.of();
        }

        return Arrays.asList(topicsJson.split(","));
    }

    @Override
    public int cleanIrrelevantHistory(String conversationId, List<String> relevantTopics) {
        // 简化实现：不实际清理历史，仅记录日志
        // 实际项目中可以根据主题相关性过滤历史消息
        log.debug("清理无关历史 - conversationId: {}, 相关主题: {}", conversationId, relevantTopics);
        return 0;
    }

    @Override
    public void resetTopic(String conversationId) {
        String key = TOPIC_KEY_PREFIX + conversationId;
        redisCacheService.delete(key);
        log.debug("重置对话主题 - conversationId: {}", conversationId);
    }

    /**
     * 计算两个主题列表的相似度
     */
    private double calculateTopicSimilarity(List<String> topics1, List<String> topics2) {
        if (topics1.isEmpty() && topics2.isEmpty()) {
            return 1.0;
        }

        if (topics1.isEmpty() || topics2.isEmpty()) {
            return 0.0;
        }

        Set<String> set1 = new HashSet<>(topics1);
        Set<String> set2 = new HashSet<>(topics2);

        // 计算交集
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        // 计算并集
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    /**
     * 判断是否为常见词
     */
    private boolean isCommonWord(String word) {
        Set<String> commonWords = Set.of(
                "我", "你", "他", "她", "它", "这", "那", "什么", "怎么",
                "为什么", "因为", "所以", "但是", "如果", "可以", "需要",
                "想", "要", "会", "能", "知道", "了解", "觉得", "认为"
        );
        return commonWords.contains(word);
    }
}