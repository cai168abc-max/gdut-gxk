package com.gdut.gxk.service;

/**
 * 敏感词过滤服务接口
 */
public interface SensitiveWordFilterService {

    /**
     * 检测文本中是否包含敏感词
     * @param text 待检测文本
     * @return 是否包含敏感词
     */
    boolean containsSensitiveWord(String text);

    /**
     * 过滤敏感词，将敏感词替换为指定字符
     * @param text 待过滤文本
     * @return 过滤后的文本
     */
    String filter(String text);

    /**
     * 过滤敏感词，自定义替换字符
     * @param text 待过滤文本
     * @param replacement 替换字符
     * @return 过滤后的文本
     */
    String filter(String text, String replacement);
}