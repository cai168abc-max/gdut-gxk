package com.gdut.gxk.listener;

import com.gdut.gxk.service.RedisCacheService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/** 会话监听器：会话销毁时自动清理AI对话上下文 */
@Component
@Slf4j
public class AIChatSessionListener implements HttpSessionListener {

    @Resource
    private RedisCacheService redisCacheService;

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        HttpSession session = se.getSession();
        String sessionId = session.getId();
        // 删除该会话的AI上下文缓存
        redisCacheService.deleteAIChatContext(sessionId);
        log.info("会话销毁，自动清理AI上下文，sessionId={}", sessionId);
    }
}
