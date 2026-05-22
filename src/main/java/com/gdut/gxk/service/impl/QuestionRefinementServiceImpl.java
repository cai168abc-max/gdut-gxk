package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.QuestionRefinementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 智能追问服务实现
 */
@Service
@Slf4j
public class QuestionRefinementServiceImpl implements QuestionRefinementService {

    @Override
    public boolean needsClarification(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return true;
        }
        // 去除所有中英文标点、空格后为空则返回true
        String cleaned = userInput.replaceAll("[\\s\\p{Punct}。，！？、；：\u201c\u201d\u2018\u2019\\【\\】（）《》—…·～「」『』〈〉]", "");
        return cleaned.isEmpty();
    }

    @Override
    public String generateFollowUpQuestion(String userInput) {
        return "请告诉我你想了解的课程或老师信息，我来帮你查询~";
    }

    @Override
    public List<String> extractAmbiguousTerms(String userInput) {
        return Collections.emptyList();
    }
}
