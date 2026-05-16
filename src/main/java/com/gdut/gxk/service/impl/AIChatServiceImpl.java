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
import com.gdut.gxk.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * AI对话服务实现
 * 使用Spring AI Alibaba的ChatClient API实现对话功能
 * 集成ChatMemory实现上下文管理，支持工具调用
 */
@Service
@Slf4j
public class AIChatServiceImpl implements AIChatService {

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

    @Resource
    private ConversationTopicService conversationTopicService;

    @Resource
    private MessageSummaryService messageSummaryService;

    @Resource
    private ApiRateLimitService apiRateLimitService;

    @Resource
    private AIResponseValidationService aiResponseValidationService;

    @Resource
    private QuestionRefinementService questionRefinementService;

    private static final String CHAT_CONTEXT_KEY_PREFIX = "ai:chat:context:";
    
    /** ChatMemory会话ID参数名 - 使用ChatMemory接口定义的常量 */
    private static final String CHAT_MEMORY_CONVERSATION_ID = ChatMemory.CONVERSATION_ID;
    
    /** AI调用操作名称 */
    private static final String AI_OPERATION_NAME = "AI对话生成";
    
    /** SSE心跳间隔（毫秒） */
    private static final long SSE_HEARTBEAT_INTERVAL = 15000L;
    
    /** SSE连接超时时间（毫秒） */
    private static final long SSE_TIMEOUT = 300000L;
    
    /** 心跳调度器 - 用于保持SSE连接活跃 */
    private final ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread thread = new Thread(r, "sse-heartbeat");
                thread.setDaemon(true);
                return thread;
            }
    );

    @Override
    public void saveAIChatContext(HttpSession session, AIChatContextDTO context) {
        if (session == null || context == null) {
            log.warn("保存AI上下文失败：session或context为空");
            return;
        }
        String sessionId = session.getId();
        redisCacheService.cacheAIChatContext(sessionId, context);
        log.debug("保存AI上下文，sessionId={}，条件数={}",
                sessionId, context.getCleanedQueryConditions().size());
    }

    @Override
    public AIChatContextDTO getCurrentChatContext(HttpSession session) {
        if (session == null) {
            log.warn("获取AI上下文失败：session为空");
            return new AIChatContextDTO();
        }
        String sessionId = session.getId();
        return redisCacheService.getAIChatContext(sessionId);
    }

    @Override
    public void clearAIChatContext(HttpSession session) {
        if (session == null) {
            log.warn("清除AI上下文失败：session为空");
            return;
        }
        String sessionId = session.getId();
        redisCacheService.deleteAIChatContext(sessionId);
        // 清除ChatMemory中的对话历史
        chatMemory.clear(sessionId);
        log.info("清除AI上下文和对话历史，sessionId={}", sessionId);
    }
    
    @Override
    public AIChatResponseDTO processAIChatRequest(HttpSession session, AIChatRequestDTO request) {
        long startTime = System.currentTimeMillis();
        String sessionId = session != null ? session.getId() : "default";
        
        try {
            log.info("[{}] 处理AI对话请求，用户输入：{}", sessionId, request.getUserInput());
            
            // 0. API限流检查
            if (!apiRateLimitService.tryAcquire(sessionId)) {
                log.warn("[{}] API调用超过配额限制", sessionId);
                AIChatResponseDTO response = buildErrorResponse(sessionId, startTime);
                response.setAiResponse("当前AI服务调用过于频繁，请稍后再试。");
                return response;
            }
            
            // 1. 获取或创建对话上下文
            AIChatContextDTO context = getCurrentChatContext(session);
            if (context == null) {
                context = new AIChatContextDTO();
            }
            
            // 2. 如果要求清除历史，则清空上下文和ChatMemory
            if (Boolean.TRUE.equals(request.getClearHistory())) {
                context = new AIChatContextDTO();
                chatMemory.clear(sessionId);
                log.info("[{}] 清除对话历史", sessionId);
            }
            
            // 3. 智能追问检查 - 如果用户问题不明确，生成追问
            if (questionRefinementService.needsClarification(request.getUserInput())) {
                // 检查追问次数限制（最多3次连续追问）
                int maxFollowUpCount = 3;
                if (context.getFollowUpCount() >= maxFollowUpCount) {
                    String message = "为了更好地帮你解答，麻烦你提供更具体的信息，比如课程名称、老师姓名或校区等~";
                    log.info("[{}] 追问次数已达上限({}次)，返回友好提示", sessionId, maxFollowUpCount);
                    
                    AIChatResponseDTO response = new AIChatResponseDTO();
                    response.setAiResponse(message);
                    response.setContextId(sessionId);
                    response.setProcessingTime(System.currentTimeMillis() - startTime);
                    response.setFromCache(false);
                    return response;
                }
                
                String followUp = questionRefinementService.generateFollowUpQuestion(request.getUserInput());
                log.info("[{}] 用户问题需要澄清，生成追问(第{}次): {}", sessionId, context.getFollowUpCount() + 1, followUp);
                
                // 增加追问次数
                context.setFollowUpCount(context.getFollowUpCount() + 1);
                saveAIChatContext(session, context);
                
                AIChatResponseDTO response = new AIChatResponseDTO();
                response.setAiResponse(followUp);
                response.setContextId(sessionId);
                response.setProcessingTime(System.currentTimeMillis() - startTime);
                response.setFromCache(false);
                return response;
            }
            
            // 如果用户输入不需要追问，重置追问计数器
            context.setFollowUpCount(0);
            
            // 3. 清洗用户输入，生成查询参数
            AIQueryParamDTO params = textCleanService.cleanAndBuildParams(request.getUserInput());
            context.getCleanedQueryConditions().add(params.getCleanedInput());

            // 3.5 对话主题追踪优化
            List<String> currentTopics = conversationTopicService.analyzeTopic(sessionId, request.getUserInput());
            boolean topicChanged = conversationTopicService.isTopicChanged(sessionId, currentTopics);
            
            if (topicChanged) {
                log.info("[{}] 检测到对话主题变化，清理无关历史", sessionId);
                conversationTopicService.cleanIrrelevantHistory(sessionId, currentTopics);
            }
            conversationTopicService.updateTopic(sessionId, currentTopics);

            // 4. 根据清洗参数查询相关课程和评论数据（作为上下文补充）
            // 优化策略：采用置信度分级的条件查询，平衡数据库查询成本和AI回复质量
            List<CourseBase> relatedCourses = List.of();
            List<CourseComment> relatedComments = List.of();
            
            // 获取查询意图置信度
            double intentConfidence = calculateIntentConfidence(request.getUserInput());
            
            // 根据置信度级别采取不同策略：
            // - 高置信度(>=60%)：预查询完整数据，减少AI工具调用
            // - 中等置信度(30%-60%)：预查询精简数据，兼顾成本和效果
            // - 低置信度(<30%)：不预查询，让AI自主决定是否调用工具
            if (intentConfidence >= 0.6) {
                log.info("[{}] 高置信度课程查询意图 ({}%)，预查询完整数据", sessionId, 
                        String.format("%.1f", intentConfidence * 100));
                relatedCourses = queryRelatedCoursesByParams(params);
                relatedComments = queryRelatedCommentsByParams(params);
            } else if (intentConfidence >= 0.3) {
                log.info("[{}] 中等置信度课程查询意图 ({}%)，预查询精简数据", sessionId, 
                        String.format("%.1f", intentConfidence * 100));
                relatedCourses = queryRelatedCoursesByParams(params, /* limit */ 5);
                // 中等置信度时不预查询评论，减少数据量
            } else {
                log.info("[{}] 低置信度课程查询意图 ({}%)，跳过预查询", sessionId, 
                        String.format("%.1f", intentConfidence * 100));
                // 不预查询，AI将通过工具调用获取必要数据
            }
            
            // 5. 更新上下文中的相关数据
            context.getUsedComments().addAll(relatedComments);
            
            // 6. 使用Spring AI ChatClient进行AI调用（集成ChatMemory和工具调用）
            String aiResponse = generateAIResponse(sessionId, request.getUserInput(), relatedCourses, relatedComments);
            
            // 6.1 AI回复验证 - 防止模型幻觉
            AIResponseValidationService.ValidationResult validationResult = aiResponseValidationService.validateResponse(aiResponse);
            if (!validationResult.isValid()) {
                log.warn("[{}] AI回复验证失败: {}", sessionId, validationResult.getMessage());
                // 可以选择替换为警告消息或降级响应
                aiResponse = aiResponse + "\n\n⚠️ 注：以上信息仅供参考，请以实际课程信息为准。";
            }
            
            context.getAiResponses().add(aiResponse);
            
            // 6.5 消息摘要优化 - 检查是否需要生成摘要
            List<org.springframework.ai.chat.messages.Message> history = chatMemory.get(sessionId);
            if (messageSummaryService.needsSummary(history)) {
                String summary = messageSummaryService.generateSummary(history);
                log.info("[{}] 生成消息摘要，长度: {}", sessionId, summary != null ? summary.length() : 0);
                // 可以选择将摘要保存或用于后续对话
            }
            
            // 7. 保存更新后的上下文
            saveAIChatContext(session, context);
            
            // 8. 构建响应
            AIChatResponseDTO response = new AIChatResponseDTO();
            response.setAiResponse(aiResponse);
            response.setContextId(sessionId);
            response.setRelatedCourses(new ArrayList<>(relatedCourses));
            response.setRelatedComments(new ArrayList<>(relatedComments));
            response.setProcessingTime(System.currentTimeMillis() - startTime);
            response.setFromCache(false);
            
            log.info("[{}] AI对话处理完成，耗时：{}ms", sessionId, response.getProcessingTime());
            return response;
            
        } catch (Exception e) {
            log.error("[{}] 处理AI对话请求失败", sessionId, e);
            return buildErrorResponse(sessionId, startTime);
        }
    }
    
    @Override
    public AIChatContextDTO getChatHistory(HttpSession session) {
        return getCurrentChatContext(session);
    }
    
    /**
     * 使用Spring AI ChatClient生成AI回复
     * 正确使用prompt().user().call()模式，集成ChatMemory和工具调用
     * 包含重试机制和降级响应策略
     * 
     * @param sessionId 会话ID，用于ChatMemory上下文管理
     * @param userInput 用户输入
     * @param courses 相关课程数据（作为上下文补充）
     * @param comments 相关评论数据（作为上下文补充）
     * @return AI回复内容
     */
    private String generateAIResponse(String sessionId, String userInput, 
                                      List<CourseBase> courses, List<CourseComment> comments) {
        log.debug("[{}] 开始生成AI回复", sessionId);
        
        // 构建上下文信息（作为用户消息的补充）
        String contextInfo = buildContextInfo(courses, comments);
        
        // 构建完整的用户消息
        String fullUserMessage = buildUserMessage(userInput, contextInfo);
        
        // 使用重试执行器调用AI
        return aiRetryExecutor.executeWithRetry(
                () -> callAIService(sessionId, fullUserMessage),
                sessionId,
                AI_OPERATION_NAME,
                () -> buildFallbackResponse(sessionId)  // 降级响应
        );
    }

    /**
     * 调用AI服务（实际执行方法）
     * 
     * @param sessionId 会话ID
     * @param fullUserMessage 完整的用户消息
     * @return AI回复内容
     */
    private String callAIService(String sessionId, String fullUserMessage) {
        try {
            // 使用ChatClient调用AI
            // 通过advisors参数传递会话ID，实现ChatMemory上下文管理
            // ChatClient会自动调用注册的工具函数（searchCourses, getCourseComments, getAllCourses）
            String aiReply = chatClient.prompt()
                    .user(fullUserMessage)
                    .advisors(advisor -> advisor.param(CHAT_MEMORY_CONVERSATION_ID, sessionId))
                    .call()
                    .content();
            
            log.debug("[{}] AI回复生成成功，长度：{}", sessionId, 
                    aiReply != null ? aiReply.length() : 0);
            
            return aiReply != null ? aiReply : buildFallbackResponse(sessionId);
            
        } catch (Exception e) {
            log.error("[{}] 调用AI服务失败: {}", sessionId, e.getMessage());
            // 转换为AIException并抛出，让重试机制处理
            throw AIException.fromException(e);
        }
    }

    /**
     * 构建降级响应
     * 当AI服务不可用或重试失败时返回
     * 
     * @param sessionId 会话ID
     * @return 降级响应消息
     */
    private String buildFallbackResponse(String sessionId) {
        log.info("[{}] 使用降级响应", sessionId);
        return aiRetryExecutor.getFallbackMessage();
    }

    /**
     * 构建上下文信息字符串
     */
    private String buildContextInfo(List<CourseBase> courses, List<CourseComment> comments) {
        StringBuilder contextInfo = new StringBuilder();
        
        if (courses != null && !courses.isEmpty()) {
            contextInfo.append("【相关课程信息】\n");
            for (CourseBase course : courses) {
                contextInfo.append(String.format("- %s（教师：%s，校区：%s，类别：%s）\n", 
                        course.getCourseName(), 
                        course.getTeacherName(), 
                        course.getCampus(),
                        course.getCategory()));
            }
        }
        
        if (comments != null && !comments.isEmpty()) {
            contextInfo.append("\n【课程评价】\n");
            for (CourseComment comment : comments) {
                contextInfo.append(String.format("- 评分：%d/5，评价：%s\n", 
                        comment.getScore(), comment.getContent()));
            }
        }
        
        return contextInfo.toString();
    }

    /**
     * 构建完整的用户消息（优化版）
     */
    private String buildUserMessage(String userInput, String contextInfo) {
        if (contextInfo == null || contextInfo.isEmpty()) {
            return String.format("""
                    📋 用户问题：%s
                    
                    💡 提示：
                    - 如果需要获取课程信息，请调用相应工具
                    - 回答要简洁、有帮助
                    """, userInput);
        }
        
        return String.format("""
                📋 用户问题：%s
                
                📚 已有的参考信息：
                %s
                
                💡 操作指南：
                - 如果参考信息足够，直接回答用户问题
                - 如果信息不足或需要更新，调用工具查询
                - 回答要具体、清晰、友好
                """, userInput, contextInfo);
    }

    /**
     * 检测是否有明确的课程查询意图（优化版）
     * 采用置信度分级策略：高置信度关键词权重2，中等置信度关键词权重1
     * 置信度 >= 50% 时触发预查询，兼顾成本和效果
     * 
     * @param userInput 用户输入
     * @return 是否有课程查询意图
     */
    private boolean hasCourseQueryIntent(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return false;
        }
        
        int confidence = 0;
        int maxScore = 0;
        
        // 高置信度关键词（直接关联课程查询，权重2）
        String[] highConfidenceKeywords = {
            "课程", "课", "选课", "推荐", "评分", "评价"
        };
        
        // 中等置信度关键词（可能关联课程，权重1）
        String[] mediumConfidenceKeywords = {
            "老师", "教授", "教师", "校区", "上课时间",
            "学分", "学时", "难度", "作业", "考试"
        };
        
        // 计算置信度分数
        for (String keyword : highConfidenceKeywords) {
            if (userInput.contains(keyword)) {
                confidence += 2;
            }
            maxScore += 2;
        }
        
        for (String keyword : mediumConfidenceKeywords) {
            if (userInput.contains(keyword)) {
                confidence += 1;
            }
            maxScore += 1;
        }
        
        // 置信度 >= 50% 时触发预查询
        double confidenceRate = maxScore > 0 ? (double) confidence / maxScore : 0;
        boolean shouldQuery = confidenceRate >= 0.5;
        
        log.debug("[意图检测] 用户输入: '{}', 置信度: {}/{} ({}%), 是否预查询: {}", 
                userInput, confidence, maxScore, 
                String.format("%.1f", confidenceRate * 100), shouldQuery);
        
        return shouldQuery;
    }

    /**
     * 根据清洗参数查询相关课程（默认无限制）
     */
    private List<CourseBase> queryRelatedCoursesByParams(AIQueryParamDTO p) {
        return queryRelatedCoursesByParams(p, Integer.MAX_VALUE);
    }
    
    /**
     * 根据清洗参数查询相关课程（带数量限制）
     * 
     * @param p 查询参数
     * @param limit 返回数量限制
     * @return 课程列表
     */
    private List<CourseBase> queryRelatedCoursesByParams(AIQueryParamDTO p, int limit) {
        try {
            List<CourseBase> courses;
            if (p.getCourseName() != null && !p.getCourseName().isEmpty()) {
                courses = courseBaseMapper.selectByKeyword(p.getCourseName());
            } else {
                StringBuilder kw = new StringBuilder();
                if (p.getCampus() != null) kw.append(p.getCampus()).append(" ");
                if (p.getCategory() != null) kw.append(p.getCategory()).append(" ");
                if (p.getTags() != null) kw.append(p.getTags());
                if (kw.length() > 0) {
                    courses = courseBaseMapper.selectByKeyword("%" + kw.toString().trim() + "%");
                } else {
                    return new ArrayList<>();
                }
            }
            
            // 应用数量限制
            if (limit > 0 && courses.size() > limit) {
                return courses.subList(0, limit);
            }
            return courses;
        } catch (Exception e) {
            log.error("按清洗参数查询课程失败", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 计算用户输入的课程查询意图置信度
     * 返回值范围：0.0（无意图）~ 1.0（强烈意图）
     * 
     * @param userInput 用户输入
     * @return 意图置信度
     */
    private double calculateIntentConfidence(String userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return 0.0;
        }
        
        int confidence = 0;
        int maxScore = 0;
        
        // 高置信度关键词（直接关联课程查询，权重2）
        String[] highConfidenceKeywords = {
            "课程", "课", "选课", "推荐", "评分", "评价"
        };
        
        // 中等置信度关键词（可能关联课程，权重1）
        String[] mediumConfidenceKeywords = {
            "老师", "教授", "教师", "校区", "上课时间",
            "学分", "学时", "难度", "作业", "考试"
        };
        
        // 计算置信度分数
        for (String keyword : highConfidenceKeywords) {
            if (userInput.contains(keyword)) {
                confidence += 2;
            }
            maxScore += 2;
        }
        
        for (String keyword : mediumConfidenceKeywords) {
            if (userInput.contains(keyword)) {
                confidence += 1;
            }
            maxScore += 1;
        }
        
        return maxScore > 0 ? (double) confidence / maxScore : 0.0;
    }

    /**
     * 根据清洗参数查询相关评论
     */
    private List<CourseComment> queryRelatedCommentsByParams(AIQueryParamDTO p) {
        try {
            // 1. 先根据参数查询相关课程
            List<CourseBase> courses = queryRelatedCoursesByParams(p);
            if (courses == null || courses.isEmpty()) {
                log.debug("未找到相关课程，跳过评论查询");
                return new ArrayList<>();
            }
            
            // 2. 收集课程ID并查询评论
            List<String> courseIds = courses.stream()
                    .map(CourseBase::getCourseId)
                    .filter(id -> id != null && !id.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            
            if (courseIds.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 3. 查询评论（最多查询前5个课程的评论，每个课程最多取3条）
            List<CourseComment> allComments = new ArrayList<>();
            int maxCoursesToQuery = Math.min(5, courseIds.size());
            
            for (int i = 0; i < maxCoursesToQuery; i++) {
                String courseId = courseIds.get(i);
                List<CourseComment> comments = courseCommentMapper.selectByCourseId(courseId);
                if (comments != null && !comments.isEmpty()) {
                    // 每个课程最多取3条评论
                    int maxComments = Math.min(3, comments.size());
                    allComments.addAll(comments.subList(0, maxComments));
                }
            }
            
            log.debug("查询到{}条相关评论", allComments.size());
            return allComments;
            
        } catch (Exception e) {
            log.error("按清洗参数查询评论失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 构建错误响应
     */
    private AIChatResponseDTO buildErrorResponse(String sessionId, long startTime) {
        AIChatResponseDTO errorResponse = new AIChatResponseDTO();
        errorResponse.setAiResponse("抱歉，我暂时无法处理您的请求，请稍后再试。");
        errorResponse.setContextId(sessionId);
        errorResponse.setProcessingTime(System.currentTimeMillis() - startTime);
        errorResponse.setFromCache(false);
        return errorResponse;
    }

    /**
     * 流式AI对话请求（优化版）
     * 使用Spring AI的流式API实现实时响应
     * 
     * 优化内容：
     * 1. 心跳保活机制 - 定期发送心跳防止连接断开
     * 2. 完善错误处理 - 区分错误类型，提供标准错误格式
     * 3. 资源管理 - 正确管理订阅和线程资源
     * 4. 取消机制 - 支持客户端主动取消
     * 
     * @param session HTTP会话
     * @param request AI对话请求
     * @return SSE发射器
     */
    @Override
    public SseEmitter streamAIChatRequest(HttpSession session, AIChatRequestDTO request) {
        // 创建SSE发射器，设置超时时间
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String sessionId = session != null ? session.getId() : "default";
        long startTime = System.currentTimeMillis();
        
        log.info("[{}] 开始流式AI对话，用户输入：{}", sessionId, request.getUserInput());
        
        // 用于管理订阅和心跳任务
        final AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        final AtomicReference<ScheduledFuture<?>> heartbeatFutureRef = new AtomicReference<>();
        final AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());
        
        // 1. 设置取消回调 - 客户端断开连接时清理资源
        emitter.onCompletion(() -> {
            log.info("[{}] SSE连接完成，清理资源", sessionId);
            cleanupResources(subscriptionRef.get(), heartbeatFutureRef.get(), sessionId);
        });
        
        // 2. 设置超时回调
        emitter.onTimeout(() -> {
            log.warn("[{}] SSE连接超时", sessionId);
            cleanupResources(subscriptionRef.get(), heartbeatFutureRef.get(), sessionId);
            try {
                sendErrorEvent(emitter, "TIMEOUT", "连接超时，请重试", sessionId);
                emitter.complete();
            } catch (Exception e) {
                log.error("[{}] 完成超时SSE连接失败", sessionId, e);
            }
        });
        
        // 3. 设置错误回调
        emitter.onError(error -> {
            log.error("[{}] SSE连接错误: {}", sessionId, error.getMessage());
            cleanupResources(subscriptionRef.get(), heartbeatFutureRef.get(), sessionId);
        });
        
        try {
            // 4. 准备对话上下文
            AIChatContextDTO context = prepareChatContext(session, request, sessionId);
            
            // 5. 查询相关数据
            AIQueryParamDTO params = textCleanService.cleanAndBuildParams(request.getUserInput());
            context.getCleanedQueryConditions().add(params.getCleanedInput());
            
            List<CourseBase> relatedCourses = queryRelatedCoursesByParams(params);
            List<CourseComment> relatedComments = queryRelatedCommentsByParams(params);
            context.getUsedComments().addAll(relatedComments);
            
            // 6. 构建用户消息
            String contextInfo = buildContextInfo(relatedCourses, relatedComments);
            String fullUserMessage = buildUserMessage(request.getUserInput(), contextInfo);
            
            // 7. 启动心跳保活机制
            ScheduledFuture<?> heartbeatFuture = startHeartbeat(emitter, sessionId);
            heartbeatFutureRef.set(heartbeatFuture);
            
            // 8. 使用流式调用AI
            Flux<String> responseFlux = chatClient.prompt()
                    .user(fullUserMessage)
                    .advisors(advisor -> advisor.param(CHAT_MEMORY_CONVERSATION_ID, sessionId))
                    .stream()
                    .content();
            
            // 9. 订阅流式响应
            Disposable subscription = responseFlux.subscribe(
                    // 数据处理
                    content -> handleStreamContent(content, emitter, fullResponse, sessionId),
                    // 错误处理
                    error -> handleStreamError(error, emitter, subscriptionRef, heartbeatFutureRef, 
                                               fullResponse, session, context, sessionId, startTime),
                    // 完成处理
                    () -> handleStreamComplete(emitter, heartbeatFutureRef, fullResponse, 
                                              session, context, sessionId, startTime)
            );
            
            subscriptionRef.set(subscription);
            
        } catch (Exception e) {
            log.error("[{}] 初始化流式AI对话失败", sessionId, e);
            cleanupResources(subscriptionRef.get(), heartbeatFutureRef.get(), sessionId);
            sendErrorEvent(emitter, "INIT_ERROR", "初始化失败：" + e.getMessage(), sessionId);
            try {
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("[{}] 完成错误SSE连接失败", sessionId, ex);
            }
        }
        
        return emitter;
    }
    
    /**
     * 准备对话上下文
     */
    private AIChatContextDTO prepareChatContext(HttpSession session, AIChatRequestDTO request, String sessionId) {
        AIChatContextDTO context = getCurrentChatContext(session);
        if (context == null) {
            context = new AIChatContextDTO();
        }
        
        // 如果要求清除历史，则清空上下文
        if (Boolean.TRUE.equals(request.getClearHistory())) {
            context = new AIChatContextDTO();
            chatMemory.clear(sessionId);
            log.info("[{}] 清除对话历史", sessionId);
        }
        
        return context;
    }
    
    /**
     * 启动心跳保活机制
     * SSE注释行（以冒号开头）会被浏览器忽略，但能保持连接活跃
     */
    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, String sessionId) {
        return heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                // 发送心跳注释（冒号开头的行在SSE中是注释，客户端会忽略）
                emitter.send(SseEmitter.event()
                        .comment("heartbeat")
                        .reconnectTime(3000)); // 建议客户端重连时间
                log.trace("[{}] 发送心跳", sessionId);
            } catch (IOException e) {
                log.warn("[{}] 发送心跳失败，连接可能已断开", sessionId);
            }
        }, SSE_HEARTBEAT_INTERVAL, SSE_HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 处理流式内容
     */
    private void handleStreamContent(String content, SseEmitter emitter, 
                                     AtomicReference<StringBuilder> fullResponse, String sessionId) {
        try {
            if (content != null && !content.isEmpty()) {
                fullResponse.get().append(content);
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(content));
            }
        } catch (IOException e) {
            log.error("[{}] 发送流式消息失败", sessionId, e);
            // 发送失败通常意味着连接已断开，不需要继续处理
        }
    }
    
    /**
     * 处理流式错误
     */
    private void handleStreamError(Throwable error, SseEmitter emitter,
                                   AtomicReference<Disposable> subscriptionRef,
                                   AtomicReference<ScheduledFuture<?>> heartbeatFutureRef,
                                   AtomicReference<StringBuilder> fullResponse,
                                   HttpSession session, AIChatContextDTO context,
                                   String sessionId, long startTime) {
        log.error("[{}] 流式调用失败", sessionId, error);
        
        // 清理资源
        cleanupResources(subscriptionRef.get(), heartbeatFutureRef.get(), sessionId);
        
        // 判断错误类型
        String errorType = classifyError(error);
        String errorMessage = buildErrorMessage(error);
        
        // 发送错误事件
        sendErrorEvent(emitter, errorType, errorMessage, sessionId);
        
        // 如果有部分响应，保存到上下文
        savePartialResponse(fullResponse.get(), session, context, sessionId);
        
        try {
            emitter.completeWithError(error);
        } catch (Exception e) {
            log.error("[{}] 完成错误SSE连接失败", sessionId, e);
        }
        
        log.info("[{}] 流式对话异常结束，耗时：{}ms", sessionId, System.currentTimeMillis() - startTime);
    }
    
    /**
     * 处理流式完成
     */
    private void handleStreamComplete(SseEmitter emitter,
                                      AtomicReference<ScheduledFuture<?>> heartbeatFutureRef,
                                      AtomicReference<StringBuilder> fullResponse,
                                      HttpSession session, AIChatContextDTO context,
                                      String sessionId, long startTime) {
        try {
            // 停止心跳
            stopHeartbeat(heartbeatFutureRef.get());
            
            // 保存完整响应到上下文
            String completeResponse = fullResponse.get().toString();
            if (!completeResponse.isEmpty()) {
                context.getAiResponses().add(completeResponse);
                saveAIChatContext(session, context);
            }
            
            // 发送完成事件
            emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(String.format("{\"contextId\":\"%s\",\"processingTime\":%d}", 
                            sessionId, System.currentTimeMillis() - startTime)));
            
            emitter.complete();
            
            log.info("[{}] 流式对话完成，响应长度：{}，耗时：{}ms", 
                    sessionId, completeResponse.length(), System.currentTimeMillis() - startTime);
                    
        } catch (IOException e) {
            log.error("[{}] 完成流式响应失败", sessionId, e);
        }
    }
    
    /**
     * 清理资源
     */
    private void cleanupResources(Disposable subscription, ScheduledFuture<?> heartbeatFuture, String sessionId) {
        // 取消订阅
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.debug("[{}] 已取消流式订阅", sessionId);
        }
        
        // 停止心跳
        stopHeartbeat(heartbeatFuture);
    }
    
    /**
     * 停止心跳任务
     */
    private void stopHeartbeat(ScheduledFuture<?> heartbeatFuture) {
        if (heartbeatFuture != null && !heartbeatFuture.isCancelled()) {
            heartbeatFuture.cancel(false);
        }
    }
    
    /**
     * 发送错误事件
     */
    private void sendErrorEvent(SseEmitter emitter, String errorType, String message, String sessionId) {
        try {
            String errorData = String.format(
                    "{\"errorType\":\"%s\",\"message\":\"%s\",\"sessionId\":\"%s\",\"timestamp\":%d}",
                    errorType, 
                    escapeJson(message), 
                    sessionId,
                    System.currentTimeMillis()
            );
            
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(errorData));
        } catch (IOException e) {
            log.error("[{}] 发送错误事件失败", sessionId, e);
        }
    }
    
    /**
     * 分类错误类型
     */
    private String classifyError(Throwable error) {
        String errorClass = error.getClass().getName();
        String errorMessage = error.getMessage() != null ? error.getMessage().toLowerCase() : "";
        
        // 网络相关错误
        if (errorClass.contains("SocketTimeoutException") || 
            errorClass.contains("ConnectException") ||
            errorClass.contains("UnknownHostException")) {
            return "NETWORK_ERROR";
        }
        
        // AI服务相关错误
        if (errorClass.contains("AI") || 
            errorMessage.contains("rate limit") ||
            errorMessage.contains("quota")) {
            return "AI_SERVICE_ERROR";
        }
        
        // 超时错误
        if (errorClass.contains("TimeoutException") || 
            errorMessage.contains("timeout")) {
            return "TIMEOUT";
        }
        
        // 认证错误
        if (errorMessage.contains("authentication") || 
            errorMessage.contains("unauthorized") ||
            errorMessage.contains("api key")) {
            return "AUTH_ERROR";
        }
        
        return "UNKNOWN_ERROR";
    }
    
    /**
     * 构建用户友好的错误消息
     */
    private String buildErrorMessage(Throwable error) {
        String errorType = classifyError(error);
        
        return switch (errorType) {
            case "NETWORK_ERROR" -> "网络连接失败，请检查网络后重试";
            case "AI_SERVICE_ERROR" -> "AI服务暂时不可用，请稍后重试";
            case "TIMEOUT" -> "请求超时，请重试";
            case "AUTH_ERROR" -> "API认证失败，请联系管理员";
            default -> "AI服务调用失败：" + error.getMessage();
        };
    }
    
    /**
     * 保存部分响应（当发生错误时）
     */
    private void savePartialResponse(StringBuilder partialResponse, HttpSession session, 
                                     AIChatContextDTO context, String sessionId) {
        String response = partialResponse.toString();
        if (!response.isEmpty()) {
            context.getAiResponses().add(response + " [响应中断]");
            saveAIChatContext(session, context);
            log.info("[{}] 已保存部分响应，长度：{}", sessionId, response.length());
        }
    }
    
    /**
     * 转义JSON字符串
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
