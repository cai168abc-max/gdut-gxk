package com.gdut.gxk.config;

import com.gdut.gxk.DTO.GetCourseCommentsRequest;
import com.gdut.gxk.DTO.SearchCoursesRequest;
import com.gdut.gxk.tool.CourseQueryTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 工具注册管理器
 * 实现工具的统一注册和管理
 * 
 * ⭐ 关键注意事项：
 * - 工具参数必须使用POJO类（而非基础类型）
 * - 这样AI模型生成的JSON对象才能正确映射
 * - 例如：{"keyword":"Java"} → SearchCoursesRequest
 */
@Configuration
@Slf4j
public class ToolRegistryConfig {

    /**
     * 统一注册所有工具
     * 当需要添加新工具时，只需在这里添加即可
     */
    @Bean
    public List<ToolCallback> registeredTools(CourseQueryTool courseQueryTool) {
        List<ToolCallback> tools = List.of(
                FunctionToolCallback.builder("searchCourses", courseQueryTool::searchCourses)
                        .description("根据关键词搜索课程信息，支持课程名、教师名、校区等关键词")
                        .inputType(SearchCoursesRequest.class)  // 使用POJO类型
                        .build(),
                FunctionToolCallback.builder("getCourseComments", courseQueryTool::getCourseComments)
                        .description("根据课程ID查询课程评价信息")
                        .inputType(GetCourseCommentsRequest.class)  // 使用POJO类型
                        .build()
        );
        
        log.info("已注册 {} 个工具", tools.size());
        return tools;
    }
}
