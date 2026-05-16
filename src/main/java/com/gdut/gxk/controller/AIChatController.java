package com.gdut.gxk.controller;

import com.gdut.gxk.DTO.AIChatContextDTO;
import com.gdut.gxk.DTO.AIChatRequestDTO;
import com.gdut.gxk.DTO.AIChatResponseDTO;
import com.gdut.gxk.DTO.AIRealChatRequestDTO;
import com.gdut.gxk.common.Result;
import com.gdut.gxk.DTO.AIQueryParamDTO;
import com.gdut.gxk.DTO.AIAvailableDataDTO;
import com.gdut.gxk.DTO.CleanTextRequest;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.mapper.CourseBaseMapper;
import com.gdut.gxk.service.RedisCacheService;
import com.gdut.gxk.mapper.SearchKeywordMapper;
import com.gdut.gxk.entity.SearchKeyword;
import com.gdut.gxk.service.TextCleanService;
import com.gdut.gxk.service.AIChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * AI对话页控制器（第三页用）
 * 支持AI对话、上下文管理、历史记录等功能
 */
@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AIChatController {

    @Resource
    private AIChatService aiChatService;

    @Resource
    private TextCleanService textCleanService;

    @Resource
    private RedisCacheService redisCacheService;

    @Resource
    private CourseBaseMapper courseBaseMapper;

    @Resource
    private SearchKeywordMapper searchKeywordMapper;

    @Resource
    private Environment environment;

    /**
     * 处理AI对话请求
     * @param request 用户输入和对话参数
     * @param session HTTP会话
     * @return AI回复和相关数据
     */
    @PostMapping("/chat")
    public Result<AIChatResponseDTO> chat(@Valid @RequestBody AIChatRequestDTO request, HttpSession session) {
        try {
            log.info("收到AI对话请求，用户输入：{}", request.getUserInput());
            
            AIChatResponseDTO response = aiChatService.processAIChatRequest(session, request);
            return Result.success("AI回复生成成功", response);
            
        } catch (Exception e) {
            log.error("AI对话处理失败", e);
            return Result.error("AI对话处理失败：" + e.getMessage());
        }
    }

    /**
     * 可用数据聚合：清洗条件 + 课程候选 + 热门搜索（如识别到热度意图）
     * @param req 原始文本
     * @param limit 课程返回条数（默认10）
     */
    @PostMapping("/available-data")
    public Result<AIAvailableDataDTO> availableData(@RequestBody CleanTextRequest req,
                                                    @RequestParam(name = "limit", required = false, defaultValue = "10") Integer limit) {
        try {
            String rawText = req != null ? req.getRawText() : null;
            int lim = (limit == null ? 10 : Math.max(1, Math.min(50, limit)));

            // 1) 清洗
            AIQueryParamDTO params = textCleanService.cleanAndBuildParams(rawText);

            // 2) 课程候选（复用 clean-search 的查询拼装）
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseBase> qw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

            if (params.getCourseName() != null && !params.getCourseName().isEmpty()) {
                String kw = trimLike(params.getCourseName());
                qw.or(w -> w.like(CourseBase::getCourseName, kw)
                        .or().like(CourseBase::getTeacherName, kw)
                        .or().like(CourseBase::getTag, kw));
            }
            if (params.getTeacherName() != null && !params.getTeacherName().isEmpty()) {
                qw.or().like(CourseBase::getTeacherName, trimLike(params.getTeacherName()));
            }
            if (params.getCampus() != null && !params.getCampus().trim().isEmpty()) {
                qw.or().eq(CourseBase::getCampus, params.getCampus().trim());
            }
            if (params.getCategory() != null && !params.getCategory().trim().isEmpty()) {
                qw.or().eq(CourseBase::getCategory, params.getCategory().trim());
            }
            if (params.getTags() != null && !params.getTags().trim().isEmpty()) {
                // 第一标签优先匹配
                qw.or().like(CourseBase::getTag, params.getTags().split(",")[0]);
            }
            if (params.getCredit() != null && !params.getCredit().trim().isEmpty()) {
                try {
                    java.math.BigDecimal credit = new java.math.BigDecimal(params.getCredit().trim());
                    qw.or().eq(CourseBase::getCredit, credit);
                } catch (Exception ignore) {}
            }
            if (params.getScoreExpr() != null && !params.getScoreExpr().trim().isEmpty()) {
                qw.or(w -> applyScoreExpr(w, params.getScoreExpr().trim()));
            }
            qw.last("LIMIT " + lim);
            log.debug("生成的查询SQL：{}", qw.getCustomSqlSegment());
            java.util.List<CourseBase> courses = courseBaseMapper.selectList(qw);

            // 3) 热度意图识别：若包含“热门/热度/热搜/大家都在搜/最近在搜”等
            boolean hotIntent = textContainsAny(rawText == null ? "" : rawText,
                    "热门", "热度", "热搜", "大家都在搜", "最近在搜", "最近大家都在搜什么课程");
            java.util.List<SearchKeyword> hot = java.util.Collections.emptyList();
            if (hotIntent) {
                hot = searchKeywordMapper.selectHotByTypes(java.util.Collections.singletonList("course_name"), 5);
            }

            // 4) 聚合返回
            AIAvailableDataDTO dto = new AIAvailableDataDTO();
            dto.setCleanedParams(params);
            dto.setMatchedCourses(courses);
            dto.setHotKeywords(hot);
            dto.setHotIntent(hotIntent);
            return Result.success("可用数据聚合成功", dto);
        } catch (Exception e) {
            log.error("获取可用数据失败", e);
            return Result.error("获取可用数据失败：" + e.getMessage());
        }
    }

    private static boolean textContainsAny(String text, String... parts) {
        String t = text == null ? "" : text;
        for (String p : parts) if (t.contains(p)) return true;
        return false;
    }
    /**
     * 获取对话历史
     * @param session HTTP会话
     * @return 对话上下文和历史记录
     */
    @GetMapping("/history")
    public Result<AIChatContextDTO> getChatHistory(HttpSession session) {
        try {
            log.info("获取对话历史，sessionId：{}", session.getId());
            
            AIChatContextDTO history = aiChatService.getChatHistory(session);
            return Result.success("获取对话历史成功", history);
            
        } catch (Exception e) {
            log.error("获取对话历史失败", e);
            return Result.error("获取对话历史失败：" + e.getMessage());
        }
    }

    /**
     * 文本清洗测试接口：输入原始对话文本，返回清洗后的查询参数
     * @param req 原始用户输入文本
     * @return 清洗后的字段映射（对接数据库字段）
     */
    @PostMapping("/clean")
    public Result<AIQueryParamDTO> clean(@RequestBody CleanTextRequest req) {
        try {
            String rawText = req != null ? req.getRawText() : null;
            log.info("收到清洗请求，原始文本长度={}", rawText != null ? rawText.length() : 0);
            AIQueryParamDTO params = textCleanService.cleanAndBuildParams(rawText);
            return Result.success("清洗成功", params);
        } catch (Exception e) {
            log.error("清洗失败", e);
            return Result.error("清洗失败：" + e.getMessage());
        }
    }

    /**
     * 清洗对话文本后，依据清洗结果查询缓存与数据库，返回限定条数的课程结果
     * @param req 原始对话文本
     * @param limit 返回条数上限（默认10，范围1-50）
     */
    @PostMapping("/clean-search")
    public Result<AIAvailableDataDTO> cleanSearch(@RequestBody CleanTextRequest req,
                                                          @RequestParam(name = "limit", required = false, defaultValue = "10") Integer limit) {
        try {
            String rawText = req != null ? req.getRawText() : null;
            int lim = (limit == null ? 10 : Math.max(1, Math.min(50, limit)));

            // 1) 清洗得到结构化参数
            AIQueryParamDTO params = textCleanService.cleanAndBuildParams(rawText);

            // 2) 生成缓存Key并尝试读取
            String cacheKey = buildCleanSearchCacheKey(params, lim);
            Object cached = redisCacheService.get(cacheKey);
            if (cached instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<CourseBase> list = (java.util.List<CourseBase>) cached;
                log.debug("clean-search 缓存命中, key={}, size={}", cacheKey, list.size());
                // 组装可用数据（缓存只对课程生效，热度实时查询）
                AIAvailableDataDTO dto = new AIAvailableDataDTO();
                dto.setCleanedParams(params);
                dto.setMatchedCourses(list);
                boolean hotIntent = textContainsAny(rawText == null ? "" : rawText,
                        "热门", "热度", "热搜", "大家都在搜", "最近在搜", "最近大家都在搜什么课程");
                dto.setHotIntent(hotIntent);
                if (hotIntent) {
                    java.util.List<SearchKeyword> hot = searchKeywordMapper.selectHotByTypes(java.util.Collections.singletonList("course_name"), 5);
                    dto.setHotKeywords(hot);
                }
                return Result.success("查询成功（缓存）", dto);
            }

            // 3) 组织数据库查询条件
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseBase> qw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();

            if (params.getCourseName() != null && !params.getCourseName().isEmpty()) {
                String kw = trimLike(params.getCourseName());
                // 模糊匹配：课程名/教师名/标签 三字段 OR 匹配同一关键词
                qw.and(w -> w.like(CourseBase::getCourseName, kw)
                        .or().like(CourseBase::getTeacherName, kw)
                        .or().like(CourseBase::getTag, kw));
            }
            if (params.getTeacherName() != null && !params.getTeacherName().isEmpty()) {
                qw.like(CourseBase::getTeacherName, trimLike(params.getTeacherName()));
            }
            if (params.getCampus() != null && !params.getCampus().trim().isEmpty()) {
                qw.eq(CourseBase::getCampus, params.getCampus().trim());
            }
            if (params.getCategory() != null && !params.getCategory().trim().isEmpty()) {
                qw.eq(CourseBase::getCategory, params.getCategory().trim());
            }
            if (params.getTags() != null && !params.getTags().trim().isEmpty()) {
                // 标签字段为逗号分隔，这里用LIKE做包含匹配
                qw.like(CourseBase::getTag, params.getTags().split(",")[0]);
            }
            if (params.getCredit() != null && !params.getCredit().trim().isEmpty()) {
                try {
                    java.math.BigDecimal credit = new java.math.BigDecimal(params.getCredit().trim());
                    qw.eq(CourseBase::getCredit, credit);
                } catch (Exception ignore) {
                    // 无法解析则忽略学分过滤
                }
            }
            if (params.getScoreExpr() != null && !params.getScoreExpr().trim().isEmpty()) {
                applyScoreExpr(qw, params.getScoreExpr().trim());
            }

            // 4) 限制条数并查询
            qw.last("LIMIT " + lim);
            java.util.List<CourseBase> records = courseBaseMapper.selectList(qw);

            // 5) 写入缓存并返回（Redis不可用时忽略异常）
            try {
                redisCacheService.set(cacheKey, records, 10, java.util.concurrent.TimeUnit.MINUTES);
            } catch (Exception ex) {
                log.warn("clean-search 缓存写入失败，key={}", cacheKey, ex);
            }
            // 6) 识别热度意图并聚合
            boolean hotIntent = textContainsAny(rawText == null ? "" : rawText,
                    "热门", "热度", "热搜", "大家都在搜", "最近在搜", "最近大家都在搜什么课程");
            java.util.List<SearchKeyword> hot = java.util.Collections.emptyList();
            if (hotIntent) {
                hot = searchKeywordMapper.selectHotByTypes(java.util.Collections.singletonList("course_name"), 5);
            }

            AIAvailableDataDTO dto = new AIAvailableDataDTO();
            dto.setCleanedParams(params);
            dto.setMatchedCourses(records);
            dto.setHotIntent(hotIntent);
            dto.setHotKeywords(hot);
            return Result.success("查询成功", dto);
        } catch (Exception e) {
            log.error("clean-search 查询失败", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 热门搜索TopN（基于搜索词表计数）
     * @param limit 返回条数（默认5，1-20）
     * @param type  搜索词类型（默认course_name，可选：course_name/teacher_name/campus/college/tag/category）
     */
    @GetMapping("/hot-search")
    public Result<java.util.List<SearchKeyword>> hotSearch(
            @RequestParam(name = "limit", required = false, defaultValue = "5") Integer limit,
            @RequestParam(name = "type", required = false, defaultValue = "course_name") String type) {
        try {
            int lim = (limit == null ? 5 : Math.max(1, Math.min(20, limit)));
            java.util.List<String> types = java.util.Collections.singletonList(type);
            java.util.List<SearchKeyword> top = searchKeywordMapper.selectHotByTypes(types, lim);
            return Result.success("热门搜索获取成功", top);
        } catch (Exception e) {
            log.error("获取热门搜索失败", e);
            return Result.error("获取热门搜索失败：" + e.getMessage());
        }
    }

    private static String trimLike(String v) {
        String t = v.trim();
        if (t.startsWith("%") && t.endsWith("%")) return t.substring(1, t.length() - 1);
        return t;
    }

    private static void applyScoreExpr(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseBase> qw, String expr) {
        try {
            String e = expr.replaceAll("\\s+", "");
            java.math.BigDecimal val;
            if (e.startsWith(">=")) {
                val = new java.math.BigDecimal(e.substring(2));
                qw.ge(CourseBase::getScore, val);
            } else if (e.startsWith("<=")) {
                val = new java.math.BigDecimal(e.substring(2));
                qw.le(CourseBase::getScore, val);
            } else if (e.startsWith(">")) {
                val = new java.math.BigDecimal(e.substring(1));
                qw.gt(CourseBase::getScore, val);
            } else if (e.startsWith("<")) {
                val = new java.math.BigDecimal(e.substring(1));
                qw.lt(CourseBase::getScore, val);
            } else if (e.startsWith("=")) {
                val = new java.math.BigDecimal(e.substring(1));
                qw.eq(CourseBase::getScore, val);
            } else {
                val = new java.math.BigDecimal(e);
                qw.ge(CourseBase::getScore, val);
            }
        } catch (Exception ignore) {
            // 非法表达式忽略
        }
    }

    private static String buildCleanSearchCacheKey(AIQueryParamDTO p, int limit) {
        String raw = String.join("|",
                safe(p.getCourseName()),
                safe(p.getTeacherName()),
                safe(p.getCampus()),
                safe(p.getCategory()),
                safe(p.getCourseTime()),
                safe(p.getCredit()),
                safe(p.getScoreExpr()),
                safe(p.getTags())) + "|" + limit;
        return "redis:ai:cleanSearch:" + md5(raw);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String md5(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] dig = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /**
     * 清除对话历史
     * @param session HTTP会话
     * @return 清除结果
     */
    @DeleteMapping("/history")
    public Result<Boolean> clearChatHistory(HttpSession session) {
        try {
            log.info("清除对话历史，sessionId：{}", session.getId());
            
            aiChatService.clearAIChatContext(session);
            return Result.success("对话历史清除成功", true);
            
        } catch (Exception e) {
            log.error("清除对话历史失败", e);
            return Result.error("清除对话历史失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前对话上下文
     * @param session HTTP会话
     * @return 当前对话上下文
     */
    @GetMapping("/context")
    public Result<AIChatContextDTO> getCurrentContext(HttpSession session) {
        try {
            log.info("获取当前对话上下文，sessionId：{}", session.getId());
            
            AIChatContextDTO context = aiChatService.getCurrentChatContext(session);
            return Result.success("获取对话上下文成功", context);
            
        } catch (Exception e) {
            log.error("获取对话上下文失败", e);
            return Result.error("获取对话上下文失败：" + e.getMessage());
        }
    }

    /**
     * 保存对话上下文（手动保存，通常由系统自动处理）
     * @param context 对话上下文
     * @param session HTTP会话
     * @return 保存结果
     */
    @PostMapping("/context")
    public Result<Boolean> saveContext(@RequestBody AIChatContextDTO context, HttpSession session) {
        try {
            log.info("手动保存对话上下文，sessionId：{}", session.getId());
            
            aiChatService.saveAIChatContext(session, context);
            return Result.success("对话上下文保存成功", true);
            
        } catch (Exception e) {
            log.error("保存对话上下文失败", e);
            return Result.error("保存对话上下文失败：" + e.getMessage());
        }
    }

    /**
     * 快速对话接口（简化版，用于简单问答）
     * @param question 用户问题
     * @param session HTTP会话
     * @return AI回复
     */
    @GetMapping("/quick")
    public Result<String> quickChat(@RequestParam String question, HttpSession session) {
        try {
            log.info("快速AI对话，问题：{}", question);
            
            AIChatRequestDTO request = new AIChatRequestDTO();
            request.setUserInput(question);
            
            AIChatResponseDTO response = aiChatService.processAIChatRequest(session, request);
            return Result.success("快速回复生成成功", response.getAiResponse());
            
        } catch (Exception e) {
            log.error("快速AI对话失败", e);
            return Result.error("快速AI对话失败：" + e.getMessage());
        }
    }

    /**
     * 流式AI对话请求（SSE）
     * @param request 用户输入和对话参数
     * @param session HTTP会话
     * @return SSE流式响应
     */
    @PostMapping("/chat/stream")
    public SseEmitter streamChat(@Valid @RequestBody AIChatRequestDTO request, HttpSession session) {
        try {
            log.info("收到流式AI对话请求，用户输入：{}", request.getUserInput());
            return aiChatService.streamAIChatRequest(session, request);
        } catch (Exception e) {
            log.error("流式AI对话处理失败", e);
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("流式AI对话处理失败：" + e.getMessage()));
                emitter.completeWithError(e);
            } catch (Exception ex) {
                log.error("发送错误消息失败", ex);
            }
            return emitter;
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
                public final String version = "2.0.0";
                public final String mode = "spring-ai"; // 使用Spring AI
                public final String provider = "dashscope";
                public final long timestamp = System.currentTimeMillis();
            });
            
        } catch (Exception e) {
            log.error("获取AI服务状态失败", e);
            return Result.error("获取AI服务状态失败：" + e.getMessage());
        }
    }

    /**
     * 真实AI对话：按示例aitry调用阿里百炼应用
     * 流程：存储会话ID与用户输入 -> 调用清洗与数据聚合 -> 构造Prompt(可用数据+用户对话) -> 调用AI并返回
     */
    @PostMapping("/real-chat")
    public Result<AIChatResponseDTO> realChat(@Valid @RequestBody AIRealChatRequestDTO req) {
        try {
            String sessionId = req.getSessionId();
            String userText = req.getUserInput();
            log.info("realChat - sessionId: {}, userText: {}", sessionId, userText);
            // 1) 清洗 + 相关数据聚合
            AIAvailableDataDTO available = availableData(new CleanTextRequest() {{ setRawText(userText); }}, 10).getData();

            // 2) 组装 Prompt：将可用数据与用户对话整合
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("可用数据：\n");
            promptBuilder.append(serializeAvailableData(available)).append("\n\n");
            promptBuilder.append("用户对话：").append(userText);
            String prompt = promptBuilder.toString();

            // 3) 调用真实AI（阿里百炼）
            String apiKey = environment.getProperty("spring.ai.dashscope.api-key");
            String appId = environment.getProperty("spring.ai.dashscope.app-id", "a04974b2ea4d4e129b0a08e62b121584");
            
            com.alibaba.dashscope.app.ApplicationParam param = com.alibaba.dashscope.app.ApplicationParam.builder()
                    .apiKey(apiKey)
                    .appId(appId)
                    .prompt(prompt)
                    .sessionId(sessionId)
                    .build();
            com.alibaba.dashscope.app.Application application = new com.alibaba.dashscope.app.Application();
            com.alibaba.dashscope.app.ApplicationResult result = application.call(param);

            String aiText = result != null && result.getOutput() != null ? result.getOutput().getText() : "";

            AIChatResponseDTO resp = new AIChatResponseDTO();
            resp.setAiResponse(aiText);
            log.info("realChat - aiResponse: {}", aiText);
            resp.setContextId(sessionId);
            resp.setRelatedCourses(available != null ? available.getMatchedCourses() : java.util.Collections.emptyList());
            resp.setRelatedComments(java.util.Collections.emptyList());
            resp.setProcessingTime(0L);
            resp.setFromCache(false);
            return Result.success("AI回复生成成功", resp);
        } catch (Exception e) {
            log.error("真实AI对话失败", e);
            return Result.error("真实AI对话失败：" + e.getMessage());
        }
    }

    private static String serializeAvailableData(AIAvailableDataDTO dto) {
        if (dto == null) return "{}";
        try {
            // 简易序列化：仅挑核心字段，避免过长
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"cleanedParams\":\"")
                    .append(safe(dto.getCleanedParams() != null ? dto.getCleanedParams().getCleanedInput() : ""))
                    .append("\",");
            sb.append("\"hotIntent\":").append(Boolean.TRUE.equals(dto.getHotIntent()));
            if (dto.getHotKeywords() != null && !dto.getHotKeywords().isEmpty()) {
                sb.append(",\"hotKeywords\":[");
                for (int i = 0; i < Math.min(5, dto.getHotKeywords().size()); i++) {
                    com.gdut.gxk.entity.SearchKeyword k = dto.getHotKeywords().get(i);
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(safe(k.getValue())).append("\"");
                }
                sb.append("]");
            }
            if (dto.getMatchedCourses() != null && !dto.getMatchedCourses().isEmpty()) {
                sb.append(",\"courses\":[");
                for (int i = 0; i < Math.min(5, dto.getMatchedCourses().size()); i++) {
                    com.gdut.gxk.entity.CourseBase c = dto.getMatchedCourses().get(i);
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(safe(c.getCourseName())).append("|")
                            .append(safe(c.getTeacherName())).append("|")
                            .append(safe(String.valueOf(c.getScore()))).append("\"");
                }
                sb.append("]");
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}


