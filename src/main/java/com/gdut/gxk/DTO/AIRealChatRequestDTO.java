package com.gdut.gxk.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 实时AI对话请求：由前端传入会话ID与用户对话 */
@Data
public class AIRealChatRequestDTO {
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotBlank(message = "用户输入不能为空")
    @Size(max = 2000, message = "输入内容不能超过2000字符")
    private String userInput;
}


