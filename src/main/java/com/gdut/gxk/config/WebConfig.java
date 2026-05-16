package com.gdut.gxk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * 配置跨域、拦截器等
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 配置跨域
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                // 允许的域名列表（根据实际前端部署地址配置）
                .allowedOrigins(
                    "http://localhost:3000",    // React开发服务器
                    "http://localhost:8081",    // Vue开发服务器
                    "http://localhost:5173",    // Vite开发服务器
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:8081",
                    "http://127.0.0.1:5173"
                    // 生产环境域名需要在这里添加
                    // "https://your-frontend-domain.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
