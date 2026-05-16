package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.QuestionRefinementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能追问服务实现
 */
@Service
@Slf4j
public class QuestionRefinementServiceImpl implements QuestionRefinementService {

    /**
     * 模糊词汇模式
     */
    private static final Pattern AMBIGUOUS_PATTERNS = Pattern.compile(
            "(哪门|哪个|哪位|什么|哪些|哪里|什么时候|怎么|如何|怎样|多少|几)"
    );

    /**
     * 追问模板映射
     */
    private static final Map<String, String> FOLLOW_UP_TEMPLATES = Map.ofEntries(
            Map.entry("课程", "你想了解哪个课程的信息呢？可以告诉我课程名称~"),
            Map.entry("课", "你想了解哪个课程的信息呢？可以告诉我课程名称~"),
            Map.entry("老师", "请问你想了解哪位老师的信息呢？"),
            Map.entry("教授", "请问你想了解哪位教授的信息呢？"),
            Map.entry("教师", "请问你想了解哪位教师的信息呢？"),
            Map.entry("校区", "你希望了解哪个校区的课程？（大学城/龙洞/东风路）"),
            Map.entry("类别", "你对课程类别有什么偏好吗？（如：人文社科、自然科学等）"),
            Map.entry("推荐", "你希望推荐什么类型的课程？有什么具体要求吗？"),
            Map.entry("选课", "你想了解关于选课的什么信息呢？"),
            Map.entry("评分", "你想查询哪个课程的评分呢？"),
            Map.entry("评价", "你想查看哪个课程的评价呢？")
    );

    /**
     * 问题类型关键词
     */
    private static final Map<String, String> QUESTION_TYPES = Map.ofEntries(
            Map.entry("什么", "你可以说得更具体一些吗？"),
            Map.entry("哪个", "你指的是哪一个呢？"),
            Map.entry("哪位", "你想问哪位呢？"),
            Map.entry("哪里", "你想了解哪个地点呢？"),
            Map.entry("什么时候", "你想了解哪个时间呢？"),
            Map.entry("怎么", "你想了解具体哪方面呢？")
    );

    @Override
    public boolean needsClarification(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return true;
        }

        // 检查是否包含模糊词汇
        Matcher matcher = AMBIGUOUS_PATTERNS.matcher(userInput);
        boolean hasAmbiguous = matcher.find();

        // 检查是否包含疑问词但没有具体对象
        if (!hasAmbiguous) {
            // 检查是否是简短的疑问
            String trimmed = userInput.trim();
            if (trimmed.length() <= 10 && trimmed.endsWith("？")) {
                return true;
            }
        }

        return hasAmbiguous;
    }

    @Override
    public String generateFollowUpQuestion(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return "请问你有什么问题呢？我可以帮你查询课程信息~";
        }

        // 首先尝试匹配具体主题
        for (Map.Entry<String, String> entry : FOLLOW_UP_TEMPLATES.entrySet()) {
            if (userInput.contains(entry.getKey())) {
                log.debug("检测到主题关键词: {}", entry.getKey());
                return entry.getValue();
            }
        }

        // 然后尝试匹配问题类型
        for (Map.Entry<String, String> entry : QUESTION_TYPES.entrySet()) {
            if (userInput.contains(entry.getKey())) {
                log.debug("检测到疑问词: {}", entry.getKey());
                return entry.getValue();
            }
        }

        // 默认回复
        return "你的问题有点模糊，可以说得更具体一些吗？比如课程名称、老师姓名或校区等~";
    }

    @Override
    public List<String> extractAmbiguousTerms(String userInput) {
        List<String> terms = new ArrayList<>();
        if (userInput == null || userInput.isEmpty()) {
            return terms;
        }

        Matcher matcher = AMBIGUOUS_PATTERNS.matcher(userInput);
        while (matcher.find()) {
            terms.add(matcher.group());
        }

        return terms;
    }
}