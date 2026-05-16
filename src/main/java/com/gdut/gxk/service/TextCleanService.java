package com.gdut.gxk.service;

import com.gdut.gxk.DTO.AIQueryParamDTO;

/**
 * 文本清洗与查询参数生成服务
 * 输入用户自然语言，输出结构化查询参数（对接 DAO）
 */
public interface TextCleanService {

    /**
     * 清洗用户输入并生成查询参数
     * @param rawInput 原始用户输入
     * @return 结构化查询参数
     */
    AIQueryParamDTO cleanAndBuildParams(String rawInput);
}


