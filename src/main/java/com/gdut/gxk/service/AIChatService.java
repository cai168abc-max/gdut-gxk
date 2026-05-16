package com.gdut.gxk.service;

import com.gdut.gxk.DTO.AIChatContextDTO;
import com.gdut.gxk.DTO.AIChatRequestDTO;
import com.gdut.gxk.DTO.AIChatResponseDTO;

import jakarta.servlet.http.HttpSession;

/** AI对话页业务服务（第三页用） */
public interface AIChatService {
    /** 保存AI对话上下文（绑定会话） */
    void saveAIChatContext(HttpSession session, AIChatContextDTO context);

    /** 获取当前会话的对话上下文 */
    AIChatContextDTO getCurrentChatContext(HttpSession session);

    /** 清除当前会话的对话上下文（前端清除按钮触发） */
    void clearAIChatContext(HttpSession session);
    
    /** 处理AI对话请求（含假处理逻辑） */
    AIChatResponseDTO processAIChatRequest(HttpSession session, AIChatRequestDTO request);
    
    /** 获取对话历史 */
    AIChatContextDTO getChatHistory(HttpSession session);

    /** 处理AI对话请求（流式响应） */
    org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamAIChatRequest(HttpSession session, AIChatRequestDTO request);
}
