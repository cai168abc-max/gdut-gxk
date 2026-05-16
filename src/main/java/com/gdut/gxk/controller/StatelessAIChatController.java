package com.gdut.gxk.controller;

import com.gdut.gxk.DTO.AIChatContextDTO;
import com.gdut.gxk.DTO.AIChatRequestDTO;
import com.gdut.gxk.DTO.AIChatResponseDTO;
import com.gdut.gxk.common.Result;
import com.gdut.gxk.service.StatelessAIChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 无状态AI对话控制器
 * 不依赖HttpSession，使用contextId进行上下文管理
 */
@RestController
@RequestMapping("/api/ai/stateless")
@Slf4j
public class StatelessAIChatController {

    @Resource
    private StatelessAIChatService statelessAIChatService;

    /**
     * 处理AI对话请求（无状态版本）
     * @param request 用户输入和对话参数
     * @return AI回复和相关数据
     */
    @PostMapping("/chat")
    public Result<AIChatResponseDTO> chat(@Valid @RequestBody AIChatRequestDTO request) {
        try {
            log.info("收到无状态AI对话请求，用户输入：{}", request.getUserInput());
            
            AIChatResponseDTO response = statelessAIChatService.processAIChatRequest(request);
            return Result.success("AI回复生成成功", response);
            
        } catch (Exception e) {
            log.error("无状态AI对话处理失败", e);
            return Result.error("AI对话处理失败：" + e.getMessage());
        }
    }

    /**
     * 获取对话历史（通过contextId）
     * @param contextId 对话上下文ID
     * @return 对话上下文和历史记录
     */
    @GetMapping("/history/{contextId}")
    public Result<AIChatContextDTO> getChatHistory(@PathVariable String contextId) {
        try {
            log.info("获取无状态对话历史，contextId：{}", contextId);
            
            AIChatContextDTO history = statelessAIChatService.getChatHistory(contextId);
            return Result.success("获取对话历史成功", history);
            
        } catch (Exception e) {
            log.error("获取无状态对话历史失败", e);
            return Result.error("获取对话历史失败：" + e.getMessage());
        }
    }

    /**
     * 清除对话历史（通过contextId）
     * @param contextId 对话上下文ID
     * @return 清除结果
     */
    @DeleteMapping("/history/{contextId}")
    public Result<Boolean> clearChatHistory(@PathVariable String contextId) {
        try {
            log.info("清除无状态对话历史，contextId：{}", contextId);
            
            boolean success = statelessAIChatService.clearChatHistory(contextId);
            if (success) {
                return Result.success("对话历史清除成功", true);
            } else {
                return Result.error("对话历史清除失败");
            }
            
        } catch (Exception e) {
            log.error("清除无状态对话历史失败", e);
            return Result.error("清除对话历史失败：" + e.getMessage());
        }
    }

    /**
     * 生成新的上下文ID
     * @return 新的上下文ID
     */
    @PostMapping("/context/new")
    public Result<String> generateContextId() {
        try {
            log.info("生成新的上下文ID");
            
            String contextId = statelessAIChatService.generateContextId();
            return Result.success("生成上下文ID成功", contextId);
            
        } catch (Exception e) {
            log.error("生成上下文ID失败", e);
            return Result.error("生成上下文ID失败：" + e.getMessage());
        }
    }

    /**
     * 快速对话接口（简化版，用于简单问答）
     * @param question 用户问题
     * @return AI回复
     */
    @GetMapping("/quick")
    public Result<AIChatResponseDTO> quickChat(@RequestParam String question) {
        try {
            log.info("快速无状态AI对话，问题：{}", question);
            
            AIChatRequestDTO request = new AIChatRequestDTO();
            request.setUserInput(question);
            
            AIChatResponseDTO response = statelessAIChatService.processAIChatRequest(request);
            return Result.success("快速回复生成成功", response);
            
        } catch (Exception e) {
            log.error("快速无状态AI对话失败", e);
            return Result.error("快速AI对话失败：" + e.getMessage());
        }
    }

    /**
     * 获取AI服务状态
     * @return 服务状态信息
     */
    @GetMapping("/status")
    public Result<Object> getServiceStatus() {
        try {
            // 返回AI服务状态信息
            return Result.success("AI服务运行正常", new Object() {
                public final String status = "running";
                public final String version = "1.0.0";
                public final String mode = "stateless"; // 无状态模式
                public final long timestamp = System.currentTimeMillis();
            });
            
        } catch (Exception e) {
            log.error("获取AI服务状态失败", e);
            return Result.error("获取AI服务状态失败：" + e.getMessage());
        }
    }
}

