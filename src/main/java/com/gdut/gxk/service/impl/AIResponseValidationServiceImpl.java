package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.AIResponseValidationService;
import com.gdut.gxk.mapper.CourseBaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * AI回复验证服务实现
 * 验证AI生成的回复内容的真实性，防止模型幻觉
 *
 * 事实审计策略：基于工具调用结果校验，而非正则提取
 * - 对比"AI说了什么"和"工具实际返回了什么"
 * - 如果AI给出了具体事实声明但无工具结果支撑 → 追加轻量警告
 * - 如果AI引用了工具结果 → 通过
 * - 闲聊/非事实性回复 → 通过
 */
@Service
@Slf4j
public class AIResponseValidationServiceImpl implements AIResponseValidationService {

    @Resource
    private CourseBaseMapper courseBaseMapper;

    /**
     * 事实声明的关键词模式（宽松匹配，仅用于判断回复是否包含事实性内容）
     * 不用于提取具体实体名，仅判断"是否在陈述事实"
     */
    private static final String[] FACT_CLAIM_INDICATORS = {
            "课程名称", "授课教师", "所在校区", "课程类型", "学分",
            "评分", "评价", "开课", "老师是", "教师是",
            "已为您找到", "找到了", "查询结果"
    };

    /**
     * 工具返回空结果的标识
     */
    private static final String[] EMPTY_RESULT_INDICATORS = {
            "[]", "empty", "未找到", "没有找到", "无结果", "0条"
    };

    @Override
    public ValidationResult validateResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return new ValidationResult(true, "空响应无需验证", 0, 0, 0, 0, null);
        }
        log.debug("AI回复验证完成（仅日志模式）");
        return new ValidationResult(true, "验证通过", 0, 0, 0, 0, null);
    }

    @Override
    public ValidationResult validateAndCorrect(String aiResponse) {
        return validateResponse(aiResponse);
    }

    @Override
    public String factCheckAgainstToolResults(String aiResponse, List<String> toolResults) {
        if (aiResponse == null || aiResponse.isEmpty()) {
            return aiResponse;
        }

        boolean hasFactClaim = containsFactClaim(aiResponse);
        boolean hasToolResults = hasRealToolResults(toolResults);

        // 场景1：AI给出了事实声明，且有工具结果支撑 → 通过
        if (hasFactClaim && hasToolResults) {
            log.debug("事实审计通过：回复有事实声明且有工具结果支撑");
            return aiResponse;
        }

        // 场景 2：AI 给出了事实声明，但没有任何工具调用或工具返回为空 → 可能幻觉
        if (hasFactClaim && (!hasToolResults || (toolResults == null || toolResults.isEmpty()))) {
            log.warn("事实审计警告：回复包含事实声明但无有效工具结果支撑，可能存在幻觉");
            return aiResponse + "\n\n⚠️ 以上信息仅供参考，请以实际课程信息为准。";
        }

        // 场景4：无事实声明（闲聊等）→ 通过
        return aiResponse;
    }

    /**
     * 判断回复是否包含事实性声明
     * 使用关键词模式而非正则提取，避免误判
     */
    private boolean containsFactClaim(String text) {
        for (String indicator : FACT_CLAIM_INDICATORS) {
            if (text.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断工具结果是否包含有效数据（非空结果）
     */
    private boolean hasRealToolResults(List<String> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return false;
        }
        for (String result : toolResults) {
            if (result == null || result.isEmpty()) continue;
            // 检查结果是否包含空结果标识
            boolean looksEmpty = false;
            for (String emptyIndicator : EMPTY_RESULT_INDICATORS) {
                if (result.contains(emptyIndicator)) {
                    looksEmpty = true;
                    break;
                }
            }
            if (!looksEmpty) return true;
        }
        return false;
    }

    @Override
    public boolean validateCourseExists(String courseName) {
        if (courseName == null || courseName.isEmpty()) {
            return false;
        }
        String keyword = courseName.replaceAll("[课程课]$", "");
        return courseBaseMapper.countByKeyword("%" + keyword + "%") > 0;
    }

    @Override
    public boolean validateTeacherExists(String teacherName) {
        if (teacherName == null || teacherName.isEmpty()) {
            return false;
        }
        String keyword = teacherName.replaceAll("[老师教授]$", "");
        return courseBaseMapper.countByTeacher("%" + keyword + "%") > 0;
    }

    @Override
    public boolean validateScore(String scoreStr) {
        if (scoreStr == null || scoreStr.isEmpty()) {
            return false;
        }
        try {
            String numStr = scoreStr.replaceAll("[^0-9.]", "");
            double score = Double.parseDouble(numStr);
            return score >= 1 && score <= 5;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}