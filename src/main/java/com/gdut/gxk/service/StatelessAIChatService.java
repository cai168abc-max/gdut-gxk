package com.gdut.gxk.service;

import com.gdut.gxk.DTO.AIChatContextDTO;
import com.gdut.gxk.DTO.AIChatRequestDTO;
import com.gdut.gxk.DTO.AIChatResponseDTO;

/**
 * 无状态AI对话服务接口
 * 不依赖HttpSession，使用contextId进行上下文管理
 */
public interface StatelessAIChatService {
    
    /** 处理AI对话请求（无状态版本） */
    AIChatResponseDTO processAIChatRequest(AIChatRequestDTO request);
    
    /** 获取对话历史（通过contextId） */
    AIChatContextDTO getChatHistory(String contextId);
    
    /** 清除对话历史（通过contextId） */
    boolean clearChatHistory(String contextId);
    
    /** 生成新的上下文ID */
    String generateContextId();
}

