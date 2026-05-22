package com.gdut.gxk.config;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 消息修剪 Hook：在每次 LLM 调用前，自动截断历史消息，防止上下文窗口爆炸。
 * 只保留最近 MAX_MESSAGES 条消息，避免 Token 超限和成本飙升。
 */
@HookPositions(HookPosition.BEFORE_MODEL)  // ✅ 属性名是 value，可以省略不写
public class MessageTrimmingHook extends MessagesModelHook {

    private static final int MAX_MESSAGES = 20;  // 只保留最近 20 条消息

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        // 消息数量未超限，不做任何修剪
        if (previousMessages.size() <= MAX_MESSAGES) {
            return new AgentCommand(previousMessages, UpdatePolicy.REPLACE);
        }

        // 只保留最近的 N 条消息
        List<Message> trimmed = previousMessages.subList(
                previousMessages.size() - MAX_MESSAGES, previousMessages.size()
        );
        return new AgentCommand(trimmed, UpdatePolicy.REPLACE);
    }

    @Override
    public String getName() {
        return "MessageTrimmingHook";
    }
}