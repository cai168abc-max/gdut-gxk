package com.gdut.gxk.service.impl;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.gdut.gxk.DTO.AIChatContextDTO;
import com.gdut.gxk.DTO.AIChatRequestDTO;
import com.gdut.gxk.DTO.AIChatResponseDTO;
import com.gdut.gxk.config.AIRetryConfig;
import com.gdut.gxk.exception.AIException;
import com.gdut.gxk.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
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

/**
 * AI对话服务实现 —— 基于 ReactAgent 1.1.2.2 最终优化版
 * 同步：agent.call() 直接返回 AssistantMessage
 * 流式：agent.streamMessages() 过滤 AssistantMessage 获取最终回复
 * 记忆：由 RedisSaver 通过 threadId 自动管理，无需手动干预
 */
@Service
@Slf4j
public class AIChatServiceImpl implements AIChatService {

    @Resource
    private RedisCacheService redisCacheService; // 用于业务上下文缓存，非Agent记忆

    @Resource
    private AIRetryConfig.AIRetryExecutor aiRetryExecutor;

    @Resource
    private ConversationTopicService conversationTopicService;


    @Resource
    private ApiRateLimitService apiRateLimitService;

    @Resource
    private AIResponseValidationService aiResponseValidationService;

    @Resource
    private QuestionRefinementService questionRefinementService;

    // 直接注入 ReactAgent，Agent的短期记忆由RedisSaver通过threadId自动管理
    @Resource
    private ReactAgent courseAgent;

    private static final String AI_OPERATION_NAME = "AI对话生成";
    private static final long SSE_HEARTBEAT_INTERVAL = 15_000L;
    private static final long SSE_TIMEOUT = 300_000L;

    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newScheduledThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    r -> {
                        Thread t = new Thread(r, "sse-heartbeat");
                        t.setDaemon(true);
                        return t;
                    });

    @PostConstruct
    public void init() {
        log.info("ReactAgent 注入成功，类型: {}", courseAgent.getClass().getName());
    }

    // ==================== 上下文管理（保留业务上下文逻辑，但移除Agent记忆的手动管理） ====================
    @Override
    public void saveAIChatContext(HttpSession session, AIChatContextDTO context) {
        if (session == null || context == null) return;
        redisCacheService.cacheAIChatContext(session.getId(), context);
    }

    @Override
    public AIChatContextDTO getCurrentChatContext(HttpSession session) {
        if (session == null) return new AIChatContextDTO();
        return redisCacheService.getAIChatContext(session.getId());
    }

    @Override
    public void clearAIChatContext(HttpSession session) {
        if (session == null) return;
        String sid = session.getId();
        
        // 清除业务上下文缓存
        redisCacheService.deleteAIChatContext(sid);
        
        // ⭐ 核心：使session失效，下次请求会生成新的sessionId（即新的threadId）
        // Agent记忆由RedisSaver通过threadId隔离，新的threadId会开启全新对话
        session.invalidate();
        
        log.info("已清除会话上下文并生成新会话，oldSessionId={}", sid);
    }

    @Override
    public AIChatContextDTO getChatHistory(HttpSession session) {
        return getCurrentChatContext(session);
    }

    // ==================== 核心对话方法 ====================
    @Override
    public AIChatResponseDTO processAIChatRequest(HttpSession session, AIChatRequestDTO request) {
        long start = System.currentTimeMillis();
        // 优先使用 request 中的 contextId 作为 threadId（保证多轮对话一致性）
        // 如果没有 contextId，则使用 sessionId（但前端可能每次创建新 session）
        String sessionId = (request.getContextId() != null && !request.getContextId().trim().isEmpty()) 
                ? request.getContextId() 
                : (session != null ? session.getId() : "default");
        log.info("[{}] 处理对话请求：{}, session 创建时间={}, lastAccessedTime={}, contextId={}", 
                sessionId, request.getUserInput(), 
                session != null ? session.getCreationTime() : "N/A",
                session != null ? session.getLastAccessedTime() : "N/A",
                request.getContextId());
        try {

            // 1. 限流
            if (!apiRateLimitService.tryAcquire(sessionId)) {
                AIChatResponseDTO resp = buildErrorResponse(sessionId, start);
                resp.setAiResponse("当前AI服务调用频繁，请稍后再试。");
                return resp;
            }

            // 2. 上下文
            AIChatContextDTO context = getCurrentChatContext(session);
            if (context == null) context = new AIChatContextDTO();

            if (Boolean.TRUE.equals(request.getClearHistory())) {
                context = new AIChatContextDTO();
                log.info("[{}] 清除历史", sessionId);
            }

            // 3. 空输入兜底（仅极端场景：true 空或纯空格）
            // ✅ 不再拦截"看似无意义"的输入，让 Agent 自主决定如何回应
            // ✅ 即使是乱码、闲聊、无意义内容，也交给 Agent 处理
            String userInput = request.getUserInput();
            if (userInput == null || userInput.trim().isEmpty()) {
                AIChatResponseDTO resp = new AIChatResponseDTO();
                resp.setAiResponse("请告诉我你想了解的课程或老师～ 😊");
                resp.setContextId(sessionId);
                resp.setProcessingTime(System.currentTimeMillis() - start);
                return resp;
            }

            // 4. 话题追踪（仅用于监控和调试，不清理历史）
            // ✅ 用户可能只是中途问别的问题，后面还会回到原话题，所以不清理历史
            // ✅ 历史清理只由用户主动触发（点击"清除历史"按钮）
            List<String> topics = conversationTopicService.analyzeTopic(sessionId, request.getUserInput());
            if (conversationTopicService.isTopicChanged(sessionId, topics)) {
                // conversationTopicService.cleanIrrelevantHistory(sessionId, topics); // 已禁用清理功能
                log.debug("[{}] 检测到主题变化，但保留完整历史（用户可能只是中途问别的问题）", sessionId);
            }
            conversationTopicService.updateTopic(sessionId, topics);

            // 5. Agent 调用 (核心：使用 agent.call() + 事实审计)
            String aiResponse = generateAIResponse(sessionId, request.getUserInput(), context);

            context.getAiResponses().add(aiResponse);
            saveAIChatContext(session, context);

            AIChatResponseDTO resp = new AIChatResponseDTO();
            resp.setAiResponse(aiResponse);
            resp.setContextId(sessionId);
            resp.setProcessingTime(System.currentTimeMillis() - start);
            resp.setFromCache(false);
            return resp;

        } catch (Exception e) {
            log.error("[{}] 对话处理失败", sessionId, e);
            return buildErrorResponse(sessionId, start);
        }
    }

    // ==================== Agent 调用封装 ====================
    private String generateAIResponse(String sessionId, String userInput, AIChatContextDTO context) {
        String fullMessage = buildUserMessage(userInput, context);
        return aiRetryExecutor.executeWithRetry(
                () -> callAgentSync(sessionId, fullMessage),
                sessionId,
                AI_OPERATION_NAME,
                this::buildFallbackResponse
        );
    }

    /**
     * 同步 Agent 调用 —— 获取 AssistantMessage + 工具调用结果，进行事实审计
     */
    private String callAgentSync(String sessionId, String fullUserMessage) {
        try {
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();
            
            // 检查 RedisSaver 中是否有历史状态
            try {
                CompiledGraph compiledGraph = courseAgent.getCompiledGraph();
                StateSnapshot snapshot = compiledGraph.getState(config);
                if (snapshot != null && snapshot.state() != null) {
                    Object messagesObj = snapshot.state().value("messages").orElse(null);
                    if (messagesObj instanceof List<?> messageList) {
                        log.info("[{}] RedisSaver 中加载到历史消息数：{}", sessionId, messageList.size());
                        // 打印最近 3 条消息类型和内容摘要，帮助调试
                        int size = messageList.size();
                        for (int i = Math.max(0, size - 3); i < size; i++) {
                            Message msg = (Message) messageList.get(i);
                            String msgType = msg.getClass().getSimpleName();
                            String text = msg.getText() != null ? msg.getText() : "N/A";
                            String textSummary = text.length() > 50 ? text.substring(0, 50) + "..." : text;
                            log.info("[{}]   - 消息[{}]: {} - {}", sessionId, i, msgType, textSummary);
                        }
                    }
                } else {
                    log.info("[{}] RedisSaver 中无历史消息（首轮对话或 session 变化）", sessionId);
                }
            } catch (Exception e) {
                log.debug("[{}] 检查历史状态失败：{}", sessionId, e.getMessage());
            }
            
            // call() 直接返回 AssistantMessage
            AssistantMessage reply = courseAgent.call(fullUserMessage, config);
            if (reply == null) {
                throw new AIException(AIException.ErrorType.MODEL_ERROR, "Agent返回空");
            }
            String text = reply.getText();
            if (text == null) return buildFallbackResponse();

            // 事实审计：从Agent状态中提取本轮工具调用结果，校验回复是否有据
            List<String> toolResults = extractToolResultsFromState(sessionId, config);
            String validatedText = aiResponseValidationService.factCheckAgainstToolResults(text, toolResults);
            return validatedText;
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] Agent同步调用失败: {}", sessionId, e.getMessage());
            throw new AIException(classifyErrorType(e), "Agent调用异常: " + e.getMessage());
        }
    }

    /**
     * 从Agent状态中提取本轮工具调用结果（作为事实审计的证据）
     */
    private List<String> extractToolResultsFromState(String sessionId, RunnableConfig config) {
        List<String> results = new ArrayList<>();
        try {
            CompiledGraph compiledGraph = courseAgent.getCompiledGraph();
            StateSnapshot snapshot = compiledGraph.getState(config);
            if (snapshot == null || snapshot.state() == null) return results;

            OverAllState state = snapshot.state();
            Object messagesObj = state.value("messages").orElse(null);
            if (!(messagesObj instanceof List<?> messageList)) return results;

            for (Object msg : messageList) {
                if (msg instanceof ToolResponseMessage toolMsg) {
                    for (ToolResponseMessage.ToolResponse resp : toolMsg.getResponses()) {
                        if (resp.responseData() != null) {
                            results.add(resp.responseData());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 状态获取失败不影响主流程，仅记录日志
            log.debug("[{}] 获取工具调用结果失败（不影响主流程）: {}", sessionId, e.getMessage());
        }
        return results;
    }

    // ==================== 流式对话 ====================
    @Override
    public SseEmitter streamAIChatRequest(HttpSession session, AIChatRequestDTO request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        final String sessionId = session != null ? session.getId() : "default";
        final long start = System.currentTimeMillis();

        final AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());
        final AtomicReference<ScheduledFuture<?>> heartbeatRef = new AtomicReference<>();

        // 业务上下文准备 (不预查询数据)
        AIChatContextDTO context = getCurrentChatContext(session);
        if (context == null) context = new AIChatContextDTO();
        if (Boolean.TRUE.equals(request.getClearHistory())) {
            context = new AIChatContextDTO();
        }
        final AIChatContextDTO finalContext = context; // 保证 effectively final

        emitter.onCompletion(() -> cleanupHeartbeat(heartbeatRef.get(), sessionId));
        emitter.onTimeout(() -> {
            cleanupHeartbeat(heartbeatRef.get(), sessionId);
            sendErrorEvent(emitter, "TIMEOUT", "连接超时", sessionId);
            emitter.complete();
        });
        emitter.onError(err -> cleanupHeartbeat(heartbeatRef.get(), sessionId));

        try {
            String fullMessage = buildUserMessage(request.getUserInput(), finalContext);
            ScheduledFuture<?> heartbeat = startHeartbeat(emitter, sessionId);
            heartbeatRef.set(heartbeat);

            RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();

            // 流式调用：使用官方推荐 streamMessages()，过滤最终 AssistantMessage
            Flux<String> textFlux = courseAgent.streamMessages(fullMessage, config)
                    .filter(msg -> msg instanceof AssistantMessage)   // 只保留最终回复
                    .cast(AssistantMessage.class)
                    .mapNotNull(AssistantMessage::getText);

            textFlux.subscribe(
                    content -> {
                        if (content != null && !content.isEmpty()) {
                            fullResponse.get().append(content);
                            try {
                                emitter.send(SseEmitter.event().name("message").data(content));
                            } catch (IOException e) {
                                log.error("[{}] 发送失败", sessionId, e);
                            }
                        }
                    },
                    error -> {
                        log.error("[{}] 流式调用失败：{}", sessionId, error.getMessage());
                        cleanupHeartbeat(heartbeatRef.get(), sessionId);
                        // 不暴露具体错误信息，统一返回友好提示
                        String userFriendlyMsg = "AI 服务暂时不可用，请稍后重试";
                        if (error.getMessage() != null && error.getMessage().toLowerCase().contains("connection")) {
                            userFriendlyMsg = "网络连接不稳定，请检查网络后重试";
                        }
                        sendErrorEvent(emitter, "STREAM_ERROR", userFriendlyMsg, sessionId);
                        if (!fullResponse.get().isEmpty()) {
                            finalContext.getAiResponses().add(fullResponse.get() + " [中断]");
                            saveAIChatContext(session, finalContext);
                        }
                        emitter.completeWithError(error);
                    },
                    () -> {
                        cleanupHeartbeat(heartbeatRef.get(), sessionId);
                        String finalText = fullResponse.get().toString();
                        if (!finalText.isEmpty()) {
                            finalContext.getAiResponses().add(finalText);
                            saveAIChatContext(session, finalContext);
                        }
                        try {
                            emitter.send(SseEmitter.event().name("complete")
                                    .data("{\"contextId\":\"" + sessionId + "\",\"time\":"
                                            + (System.currentTimeMillis() - start) + "}"));
                            emitter.complete();
                        } catch (IOException e) {
                            log.error("[{}] 完成事件发送失败", sessionId, e);
                        }
                    }
            );
        } catch (Exception e) {
            log.error("[{}] 启动流式对话失败", sessionId, e);
            cleanupHeartbeat(heartbeatRef.get(), sessionId);
            sendErrorEvent(emitter, "INIT_ERROR", "AI服务暂时不可用，请稍后重试", sessionId);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    // ==================== 辅助方法 ====================
    
    /**
     * 确定 session ID 的优先级策略：
     * 1. 优先使用前端传入的 contextId（最可靠）
     * 2. 如果没有 contextId，使用前端传入的 sessionId
     * 3. 如果都没有，使用 HttpSession 的 sessionId
     * 4. 最后才生成新的（仅在 session 为 null 时）
     */
    private String determineSessionId(AIChatRequestDTO request, HttpSession session) {
        // 1. 优先使用 contextId
        if (request.getContextId() != null && !request.getContextId().trim().isEmpty()) {
            return request.getContextId();
        }
        
        // 2. 使用前端传入的 sessionId
        if (request.getSessionId() != null && !request.getSessionId().trim().isEmpty()) {
            return request.getSessionId();
        }
        
        // 3. 使用 HttpSession 的 sessionId
        if (session != null) {
            return session.getId();
        }
        
        // 4. 生成新的（仅在 session 为 null 时）
        return "default-" + System.currentTimeMillis();
    }

    private String buildUserMessage(String userInput, AIChatContextDTO context) {
        // 不再手动注入上下文，完全依赖 RedisSaver 的自动记忆
        // 直接返回用户输入，由 Agent 从历史消息中自主回顾
        return userInput;
    }

    private AIException.ErrorType classifyErrorType(Throwable e) {
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("rate limit") || msg.contains("quota")) return AIException.ErrorType.RATE_LIMITED;
        if (msg.contains("timeout")) return AIException.ErrorType.NETWORK_ERROR;
        if (msg.contains("auth") || msg.contains("api key") || msg.contains("arrearage")
                || msg.contains("access denied") || msg.contains("401")) return AIException.ErrorType.AUTH_ERROR;
        if (msg.contains("400") || msg.contains("invalid")) return AIException.ErrorType.INVALID_PARAM;
        return AIException.ErrorType.MODEL_ERROR;
    }

    private String buildFallbackResponse() {
        return aiRetryExecutor.getFallbackMessage();
    }

    private AIChatResponseDTO buildErrorResponse(String sessionId, long start) {
        AIChatResponseDTO resp = new AIChatResponseDTO();
        resp.setAiResponse("抱歉，我暂时无法处理您的请求，请稍后再试。");
        resp.setContextId(sessionId);
        resp.setProcessingTime(System.currentTimeMillis() - start);
        return resp;
    }

    // ---------------- SSE工具方法 ----------------
    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, String sid) {
        return heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                log.trace("[{}] 心跳发送失败", sid);
            }
        }, SSE_HEARTBEAT_INTERVAL, SSE_HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void cleanupHeartbeat(ScheduledFuture<?> f, String sid) {
        if (f != null && !f.isCancelled()) f.cancel(false);
        log.debug("[{}] 心跳任务已清理", sid);
    }

    private void sendErrorEvent(SseEmitter emitter, String type, String msg, String sid) {
        try {
            String data = String.format(
                    "{\"errorType\":\"%s\",\"message\":\"%s\",\"sessionId\":\"%s\",\"timestamp\":%d}",
                    type, msg, sid, System.currentTimeMillis()
            );
            emitter.send(SseEmitter.event().name("error").data(data));
        } catch (IOException e) {
            log.error("[{}] 发送错误事件失败", sid, e);
        }
    }
}