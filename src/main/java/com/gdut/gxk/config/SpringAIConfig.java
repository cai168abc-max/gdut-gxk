package com.gdut.gxk.config;

import com.gdut.gxk.tool.CourseQueryTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Spring AI 配置类
 * 配置 ChatClient、ChatMemory 和相关组件
 */
@Configuration
@Slf4j
public class SpringAIConfig {

    /**
     * 优化后的System Prompt - 结构化提示词，提升AI角色定位和工具调用能力
     */
    private static final String SYSTEM_PROMPT = """
            你是广东工业大学课程评价系统的智能助手「广小课」。
            
            你的核心职责：
            - 帮助学生查询和了解广工的课程信息
            - 提供真实的课程评价数据
            - 给出选课建议和推荐
            
             可用工具：
            1. searchCourses(关键词) - 根据关键词搜索课程（支持课程名、教师名、校区）
            2. getCourseComments(课程ID) - 查询指定课程的学生评价
            3. getAllCourses() - 获取所有课程列表
            
             操作指南：
            - 当用户询问课程相关问题时，优先使用工具获取实时数据
            - 回答要简洁明了，重点突出关键信息
            - 保持友好、热情的语气，使用表情符号增加亲和力
            
            ⚠ 注意事项：
            - 只回答与广工课程相关的问题
            - 对于敏感问题或超出范围的请求，礼貌拒绝
            - 如果工具返回空结果，如实告知用户
            
            现在开始对话吧！
            """;

    private static final String CHAT_MEMORY_KEY_PREFIX = "ai:chat:memory:";
    
    @Value("${spring.ai.chat.memory.expire-seconds:3600}")
    private long chatMemoryExpireSeconds;
    
    @Value("${spring.ai.chat.memory.max-messages:20}")
    private int maxMessageCount;

    /**
     * 配置ChatMemory Bean
     * 使用自定义的RedisChatMemory实现，支持分布式会话管理
     * 优化：支持配置化的消息窗口大小限制
     */
    @Bean
    public ChatMemory chatMemory(StringRedisTemplate stringRedisTemplate) {
        log.info("初始化RedisChatMemory，key前缀: {}, 过期时间: {}秒, 消息窗口: {}条", 
                CHAT_MEMORY_KEY_PREFIX, chatMemoryExpireSeconds, maxMessageCount);
        return new RedisChatMemory(
                stringRedisTemplate,
                CHAT_MEMORY_KEY_PREFIX,
                chatMemoryExpireSeconds,
                maxMessageCount
        );
    }

    /**
     * 配置ChatClient Bean
     * 使用Spring AI 1.1.6的Builder模式创建ChatClient
     * 集成ChatMemory实现上下文管理
     * 
     * 注意：工具对象通过.defaultTools()方法直接注册
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory, CourseQueryTool courseQueryTool) {
        log.info("初始化ChatClient，集成ChatMemory和工具对象");
        
        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(memoryAdvisor)
                .defaultTools(courseQueryTool)
                .build();
    }

}