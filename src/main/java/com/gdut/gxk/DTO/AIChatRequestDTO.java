package com.gdut.gxk.DTO;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI对话请求DTO
 */
@Data
public class AIChatRequestDTO {
    
    /**
     * 用户输入的问题或查询
     */
    @NotBlank(message = "用户输入不能为空")
    @Size(max = 1000, message = "输入内容不能超过1000字符")
    private String userInput;
    
    /**
     * 对话上下文ID（可选，用于多轮对话）
     * 如果为空，系统会生成新的上下文ID
     */
    private String contextId;
    
    /**
     * 是否清除历史对话（可选）
     */
    private Boolean clearHistory = false;
    
    /**
     * 用户标识（可选，用于区分不同用户）
     */
    private String userId;
    
    /**
     * 会话ID（可选，前端传入的会话标识）
     */
    private String sessionId;
}