package com.gdut.gxk.service.impl;

import com.gdut.gxk.DTO.AIChatContextDTO;
import com.gdut.gxk.DTO.AIChatRequestDTO;
import com.gdut.gxk.DTO.AIChatResponseDTO;
import com.gdut.gxk.DTO.AIQueryParamDTO;
import com.gdut.gxk.config.AIRetryConfig;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.exception.AIException;
import com.gdut.gxk.mapper.CourseBaseMapper;
import com.gdut.gxk.mapper.CourseCommentMapper;
import com.gdut.gxk.service.RedisCacheService;
import com.gdut.gxk.service.TextCleanService;
import com.gdut.gxk.service.StatelessAIChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 无状态AI对话服务实现
 * 不依赖HttpSession，使用contextId进行上下文管理
 * 使用Spring AI Alibaba的ChatClient API，支持：
 * 1. 基于contextId的对话历史管理（通过MessageChatMemoryAdvisor）
 * 2. 自动工具调用（通过CourseQueryTool）
 */
@Service
@Slf4j
public class StatelessAIChatServiceImpl implements StatelessAIChatService {

    @Resource
    private RedisCacheService redisCacheService;
    
    @Resource
    private CourseBaseMapper courseBaseMapper;
    
    @Resource
    private CourseCommentMapper courseCommentMapper;

    @Resource
    private TextCleanService textCleanService;

    @Resource
    private ChatClient chatClient;

    @Resource
    private ChatMemory chatMemory;

    @Resource
    private AIRetryConfig.AIRetryExecutor aiRetryExecutor;
    
    /**
     * ChatMemory参数名：用于传递conversationId
     * 使用ChatMemory接口定义的常量
     */
    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = ChatMemory.CONVERSATION_ID;
    
    /** AI调用操作名称 */
    private static final String AI_OPERATION_NAME = "无状态AI对话生成";

    @Override
    public AIChatResponseDTO processAIChatRequest(AIChatRequestDTO request) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("处理无状态AI对话请求，用户输入：{}", request.getUserInput());
            
            // 1. 获取或生成上下文ID（conversationId）
            String contextId = request.getContextId();
            if (contextId == null || contextId.trim().isEmpty()) {
                contextId = generateContextId();
                log.info("生成新的上下文ID：{}", contextId);
            }
            
            // 2. 获取或创建对话上下文（用于业务数据存储）
            AIChatContextDTO context = redisCacheService.getAIChatContext(contextId);
            if (context == null) {
                context = new AIChatContextDTO();
                log.info("创建新的对话上下文：{}", contextId);
            }
            
            // 3. 如果要求清除历史，则清空上下文和ChatMemory
            if (Boolean.TRUE.equals(request.getClearHistory())) {
                context = new AIChatContextDTO();
                chatMemory.clear(contextId);
                log.info("清除对话历史：{}", contextId);
            }
            
            // 4. 清洗用户输入，生成查询参数
            AIQueryParamDTO params = textCleanService.cleanAndBuildParams(request.getUserInput());
            context.getCleanedQueryConditions().add(params.getCleanedInput());

            // 5. 根据清洗参数查询相关课程和评论数据（用于响应展示）
            List<CourseBase> relatedCourses = queryRelatedCoursesByParams(params);
            List<CourseComment> relatedComments = queryRelatedCommentsByParams(params);
            
            // 6. 更新上下文中的相关数据
            context.getUsedComments().addAll(relatedComments);
            
            // 7. 使用Spring AI ChatClient进行AI调用（自动管理对话历史和工具调用）
            String aiResponse = generateAIResponse(contextId, request.getUserInput());
            context.getAiResponses().add(aiResponse);
            
            // 8. 保存更新后的上下文
            redisCacheService.cacheAIChatContext(contextId, context);
            
            // 9. 构建响应
            AIChatResponseDTO response = new AIChatResponseDTO();
            response.setAiResponse(aiResponse);
            response.setContextId(contextId);
            response.setRelatedCourses(new ArrayList<>(relatedCourses));
            response.setRelatedComments(new ArrayList<>(relatedComments));
            response.setProcessingTime(System.currentTimeMillis() - startTime);
            response.setFromCache(false);
            
            log.info("无状态AI对话处理完成，contextId：{}，耗时：{}ms", contextId, response.getProcessingTime());
            return response;
            
        } catch (Exception e) {
            log.error("处理无状态AI对话请求失败", e);
            
            AIChatResponseDTO errorResponse = new AIChatResponseDTO();
            errorResponse.setAiResponse("抱歉，我暂时无法处理您的请求，请稍后再试。");
            errorResponse.setContextId(request.getContextId());
            errorResponse.setProcessingTime(System.currentTimeMillis() - startTime);
            errorResponse.setFromCache(false);
            
            return errorResponse;
        }
    }
    
    @Override
    public AIChatContextDTO getChatHistory(String contextId) {
        if (contextId == null || contextId.trim().isEmpty()) {
            log.warn("获取对话历史失败：contextId为空");
            return new AIChatContextDTO();
        }
        return redisCacheService.getAIChatContext(contextId);
    }
    
    @Override
    public boolean clearChatHistory(String contextId) {
        if (contextId == null || contextId.trim().isEmpty()) {
            log.warn("清除对话历史失败：contextId为空");
            return false;
        }
        try {
            redisCacheService.deleteAIChatContext(contextId);
            chatMemory.clear(contextId);
            log.info("清除对话历史成功：{}", contextId);
            return true;
        } catch (Exception e) {
            log.error("清除对话历史失败：{}", contextId, e);
            return false;
        }
    }
    
    @Override
    public String generateContextId() {
        return "ctx_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 使用Spring AI ChatClient生成AI回复
     * 
     * 关键特性：
     * 1. 通过MessageChatMemoryAdvisor自动管理对话历史（基于contextId）
     * 2. 通过defaultFunctions自动调用CourseQueryTool工具
     * 3. 支持多轮对话上下文保持
     * 4. 集成重试机制和降级响应策略
     * 
     * @param contextId 上下文ID（用于ChatMemory管理对话历史）
     * @param userInput 用户输入
     * @return AI回复内容
     */
    private String generateAIResponse(String contextId, String userInput) {
        log.debug("调用ChatClient，contextId: {}, userInput: {}", contextId, userInput);
        
        // 使用重试执行器调用AI
        return aiRetryExecutor.executeWithRetry(
                () -> callAIService(contextId, userInput),
                contextId,
                AI_OPERATION_NAME,
                () -> buildFallbackResponse(contextId)  // 降级响应
        );
    }

    /**
     * 调用AI服务（实际执行方法）
     * 
     * @param contextId 上下文ID
     * @param userInput 用户输入
     * @return AI回复内容
     */
    private String callAIService(String contextId, String userInput) {
        try {
            // 使用ChatClient API进行调用
            // 通过advisors参数传递conversationId，让MessageChatMemoryAdvisor自动管理对话历史
            ChatResponse response = chatClient.prompt()
                    .user(userInput)
                    // 设置ChatMemory的conversationId，实现基于contextId的上下文管理
                    .advisors(advisorSpec -> advisorSpec
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, contextId))
                    .call()
                    .chatResponse();

            if (response == null || response.getResult() == null) {
                log.warn("ChatClient返回空响应");
                throw AIException.modelError("AI服务返回空响应");
            }

            String aiReply = response.getResult().getOutput().getText();
            log.debug("AI回复成功，长度: {}", aiReply != null ? aiReply.length() : 0);
            
            return aiReply;
            
        } catch (AIException e) {
            // 已经是AIException，直接抛出
            throw e;
        } catch (Exception e) {
            log.error("调用AI服务失败，contextId: {}", contextId, e);
            // 转换为AIException并抛出，让重试机制处理
            throw AIException.fromException(e);
        }
    }

    /**
     * 构建降级响应
     * 当AI服务不可用或重试失败时返回
     * 
     * @param contextId 上下文ID
     * @return 降级响应消息
     */
    private String buildFallbackResponse(String contextId) {
        log.info("[{}] 使用降级响应", contextId);
        return aiRetryExecutor.getFallbackMessage();
    }

    private List<CourseBase> queryRelatedCoursesByParams(AIQueryParamDTO p) {
        try {
            if (p.getCourseName() != null && !p.getCourseName().isEmpty()) {
                return courseBaseMapper.selectByKeyword(p.getCourseName());
            }
            StringBuilder kw = new StringBuilder();
            if (p.getCampus() != null) kw.append(p.getCampus()).append(" ");
            if (p.getCategory() != null) kw.append(p.getCategory()).append(" ");
            if (p.getTags() != null) kw.append(p.getTags());
            if (kw.length() > 0) {
                return courseBaseMapper.selectByKeyword("%" + kw.toString().trim() + "%");
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("按清洗参数查询课程失败", e);
            return new ArrayList<>();
        }
    }

    private List<CourseComment> queryRelatedCommentsByParams(AIQueryParamDTO p) {
        try {
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("按清洗参数查询评论失败", e);
            return new ArrayList<>();
        }
    }
}