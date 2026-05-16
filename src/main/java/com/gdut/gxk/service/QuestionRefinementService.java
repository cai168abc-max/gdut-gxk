package com.gdut.gxk.service;

import java.util.List;

/**
 * 智能追问服务接口
 * 当用户问题不明确时自动生成追问问题
 */
public interface QuestionRefinementService {

    /**
     * 判断用户输入是否需要进一步澄清
     * @param userInput 用户输入
     * @return 是否需要澄清
     */
    boolean needsClarification(String userInput);

    /**
     * 生成追问问题
     * @param userInput 用户输入
     * @return 追问问题
     */
    String generateFollowUpQuestion(String userInput);

    /**
     * 提取模糊术语
     * @param userInput 用户输入
     * @return 模糊术语列表
     */
    List<String> extractAmbiguousTerms(String userInput);
}