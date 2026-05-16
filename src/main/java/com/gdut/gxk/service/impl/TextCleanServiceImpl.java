package com.gdut.gxk.service.impl;

import com.gdut.gxk.DTO.AIQueryParamDTO;
import com.gdut.gxk.service.TextCleanService;
import com.gdut.gxk.service.SensitiveWordFilterService;
import com.gdut.gxk.mapper.SearchKeywordMapper;
import com.gdut.gxk.entity.SearchKeyword;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 文本清洗实现：实现 YAML 规则的核心子集，面向广工公选课查询
 */
@Service
@Slf4j
public class TextCleanServiceImpl implements TextCleanService {

    private static final int INPUT_LIMIT = 1000;

    @jakarta.annotation.Resource
    private SensitiveWordFilterService sensitiveWordFilterService;

    private static final Map<String, String> TYPO_MAP = new LinkedHashMap<>();
    private static final Map<String, String> ASR_MAP = new LinkedHashMap<>();
    private static final Map<String, String> DIALECT_EN_MAP = new LinkedHashMap<>();
    private static final Map<String, String> VARIANT_UNIFY = new LinkedHashMap<>();

    // 查询词/同义词映射（优先命中这些标准化查询词）
    private static final Map<String, String> CAMPUS_QUERY_TERMS = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORY_QUERY_TERMS = new LinkedHashMap<>();
    private static final Map<String, String> TAG_SYNONYM = new LinkedHashMap<>();

    private static final Set<String> WHITE_LIST = new HashSet<>();

    private static final Pattern SENSITIVE_PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern SENSITIVE_EMAIL = Pattern.compile("[a-zA-Z0-9_-]+@(gdupt|gdut)\\.edu\\.cn");
    private static final Pattern DANGER_SQL = Pattern.compile("\\b(DROP|DELETE|UPDATE|INSERT|UNION|SELECT\\s*\\*|EXEC)\\b|--|;#|OR 1=1|AND 1=1", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGER_CMD = Pattern.compile("(;|&&|\\|\\|)|(rm -rf|del /f|shutdown|taskkill|chmod|chown|powershell)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_PATTERN = Pattern.compile("(?i)\\b((https?://|ftp://|www\\.)[^\\s]+)\\b");
    private static final Pattern CODE_PATTERN = Pattern.compile("(?i)(<script>|</script>|<\\?php>|public\\s+static\\s+void|function\\s+|var\\s+|let\\s+|const\\s+|eval\\(|alert\\()");
    private static final Pattern ACCOUNT_LIKE = Pattern.compile("[A-Za-z]\\w{2,}\\d{4,}|u?\\d{8,}");

    @jakarta.annotation.Resource
    private SearchKeywordMapper searchKeywordMapper;

    static {
        // 术语与别名
        putAll(TYPO_MAP, new String[][]{
                {"事↓", "事少"}, {"事→少", "事少"}, {"给分↑", "给分松"},
                {"网课", "线上课"}, {"8点课", "早八课"}, {"2分", "2学分"}, {"3分", "3学分"}
        });

        // ASR 纠错（部分）
        putAll(ASR_MAP, new String[][]{
                {"龙东", "龙洞"}, {"龙冬", "龙洞"}, {"史少", "事少"}, {"是烧", "事少"},
                {"不给名", "不点名"}, {"线上可", "线上课"}, {"早吧课", "早八课"}, {"晚酒课", "晚九课"}
        });

        // 方言/英文映射（部分）
        putAll(DIALECT_EN_MAP, new String[][]{
                {"俾分靓", "给分松"}, {"唔使签到", "不点名"}, {"走课", "跨校区选课"},
                {"online course", "线上课"}, {"e-learning", "线上课"}, {"no exam", "无考试"},
                {"group project", "小组作业"}, {"2 credits", "2学分"}, {"3 credits", "3学分"}
        });

        // 变体字符统一（部分）
        putAll(VARIANT_UNIFY, new String[][]{
                {"龍", "龙"}, {"東", "东"}, {"課", "课"}, {"點", "点"}, {"鬆", "松"},
                {"學", "学"}, {"選", "选"}
        });

        WHITE_LIST.addAll(Arrays.asList("事少","给分松","不点名","龙洞","大学城","东风路","线上课","早八课","晚九课","跨校区","无签到","凑学分","水课","高分课","无考试","小组作业","2学分","3学分"));

        // 查询词：校区（别名→标准名）
        putAll(CAMPUS_QUERY_TERMS, new String[][]{
                {"龙洞", "广东工业大学龙洞校区"},
                {"大学城", "广东工业大学大学城校区"},
                {"东风路", "广东工业大学东风路校区"},
                {"揭阳", "广东工业大学揭阳校区"},
                {"番禺", "广东工业大学番禺校区"},
                {"LD", "广东工业大学龙洞校区"}
        });

        // 查询词：类别（别名→标准名）
        putAll(CATEGORY_QUERY_TERMS, new String[][]{
                {"十校公选", "十校公选"}, {"公选", "十校公选"}, {"公共选修", "十校公选"}, {"选修课", "十校公选"}
        });

        // 标签同义词映射（别名→标准标签）
        putAll(TAG_SYNONYM, new String[][]{
                {"水课", "事少"}, {"摸鱼", "事少"}, {"轻松", "事少"},
                {"高分", "给分松"}, {"给分高", "给分松"}, {"给分好", "给分松"},
                {"不点名", "不点名"}, {"无签到", "不点名"}, {"免签到", "不点名"},
                {"无考试", "无考试"}, {"开卷", "无考试"}
        });
    }

    @Override
    public AIQueryParamDTO cleanAndBuildParams(String rawInput) {
        String input = limitLength(Optional.ofNullable(rawInput).orElse(""));
        // 如果传入的是一个 JSON 字符串包着 rawText，尝试提取其值
        input = extractJsonRawTextIfPresent(input);
        if (input.trim().isEmpty()) {
            AIQueryParamDTO empty = new AIQueryParamDTO();
            empty.setCategory("十校公选");
            empty.setScoreExpr(null); // 空输入不设强制评分，避免固定默认结果
            empty.setCleanedInput("");
            return empty;
        }

        // 1) 基础符号/不可见字符处理 + 变体统一
        input = normalize(input);
        input = applyMap(VARIANT_UNIFY, input);

        // 2) 术语纠错、ASR 纠错、方言&英文映射
        input = applyMap(TYPO_MAP, input);
        input = applyMap(ASR_MAP, input);
        input = applyMap(DIALECT_EN_MAP, input);

        // 3) 敏感信息脱敏（不做为参数）
        String filtered = removeMaliciousAndNoise(input);
        String desensitized = desensitize(filtered);
        
        // 4) 敏感词过滤
        String safeInput = sensitiveWordFilterService.filter(desensitized);
        log.debug("敏感词过滤完成，原始长度={}，过滤后长度={}", desensitized.length(), safeInput.length());

        // 5) 关键词提取 -> 字段填充
        AIQueryParamDTO params = new AIQueryParamDTO();
        params.setCleanedInput(safeInput);

        // 1) 查询词优先（来自查询词表）：校区/类别/标签
        String campus = matchBestByType(desensitized, "campus");
        if (campus == null || campus.isEmpty()) {
            campus = extractByQueryTerms(input, CAMPUS_QUERY_TERMS); // 词典兜底
        }
        if (!campus.isEmpty()) params.setCampus(campus);

        // 学分（正则，支持 “2学分/2.0学分/两学分/3分课”等）
        String credit = extractCreditByRegex(input);
        if (!credit.isEmpty()) params.setCredit(credit);

        // 上课时间（复杂正则，尽量命中）
        String courseTime = extractCourseTimeByRegex(input);
        if (!courseTime.isEmpty()) params.setCourseTime(courseTime);

        // 标签（按查询词/同义词收集所有命中）
        String tags = collectAllTagsByType(desensitized, "tag");
        if (tags.isEmpty()) {
            tags = extractTagsByKeywords(input); // 同义词兜底
        }
        if (!tags.isEmpty()) params.setTags(tags);

        // 类别：仅在明确提及时才设置
        if (params.getCategory() == null) {
            String category = matchBestByType(desensitized, "category");
            if (category == null || category.isEmpty()) {
                category = extractByQueryTerms(input, CATEGORY_QUERY_TERMS); // 词典兜底
            }
            if (!category.isEmpty()) params.setCategory(category);
        }

        // 课程名/教师名：优先在搜索词表中匹配
        String baseText = stripJsonKeys(desensitized);
        String matchedCourse = matchBestByType(baseText, "course_name");
        if (matchedCourse != null) params.setCourseName("%" + matchedCourse + "%");
        // 若课程名未命中，尝试从输入中抽一个较强关键词（≥2字且非功能词）作为通用模糊匹配
        if (params.getCourseName() == null || params.getCourseName().isEmpty()) {
            String kw = extractCourseKeyword(baseText);
            if (kw != null && !kw.isEmpty()) params.setCourseName("%" + kw + "%");
        }

        String matchedTeacher = matchBestByType(baseText, "teacher_name");
        if (matchedTeacher != null) params.setTeacherName("%" + matchedTeacher + "%");

        // 解析“X老师/教师”直呼其名
        if (params.getTeacherName() == null) {
            String teacherBySuffix = extractTeacherBySuffix(baseText);
            if (!teacherBySuffix.isEmpty()) {
                params.setTeacherName("%" + teacherBySuffix + "%");
            }
        }

        // 评分（复杂正则补齐）：显式评分如 “4.2分以上/4分起/≥4.0”
        if (params.getScoreExpr() == null) {
            String parsedScore = parseScoreExpression(baseText);
            if (!parsedScore.isEmpty()) params.setScoreExpr(parsedScore);
        }

        // 回退策略：若仍无任何有效过滤（课名/老师/校区/类别/标签/学分/评分），
        // 则抽取一个通用关键词用于课程名模糊匹配，避免总是走固定默认返回
        boolean noFilter =
                params.getCourseName() == null &&
                params.getTeacherName() == null &&
                (params.getCampus() == null || params.getCampus().isEmpty()) &&
                (params.getCategory() == null || params.getCategory().isEmpty()) &&
                (params.getTags() == null || params.getTags().isEmpty()) &&
                (params.getCredit() == null || params.getCredit().isEmpty()) &&
                (params.getScoreExpr() == null || params.getScoreExpr().isEmpty());
        if (noFilter) {
            String kw = extractCourseKeyword(baseText);
            if (kw != null && !kw.isEmpty()) {
                params.setCourseName("%" + kw + "%");
            }
        }

        // 不再设置全局评分兜底，除非用户表达相关意图或显式评分

        return params;
    }

    private static String extractJsonRawTextIfPresent(String s) {
        String t = s.trim();
        if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("\"") && t.endsWith("\""))) {
            // 朴素提取: 查找 "rawText" : "..."
            int k = t.indexOf("\"rawText\"");
            if (k >= 0) {
                int colon = t.indexOf(":", k);
                if (colon > 0) {
                    int firstQuote = t.indexOf('"', colon + 1);
                    if (firstQuote > 0) {
                        int secondQuote = t.indexOf('"', firstQuote + 1);
                        if (secondQuote > firstQuote) {
                            return t.substring(firstQuote + 1, secondQuote);
                        }
                    }
                }
            }
        }
        return s;
    }

    private static String stripJsonKeys(String s) {
        // 去掉可能的 JSON 键名片段，如 {"rawText": "..."}
        return s.replaceAll("\\\"[a-zA-Z0-9_]+\\\"\\s*:\\s*", "");
    }

    private static String extractTeacherBySuffix(String text) {
        // 捕获 1-3 个中文字符 + 老师/教师
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("([\\u4e00-\\u9fa5]{1,3})(老师|教师)")
                    .matcher(text);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return "";
    }

    private static String parseScoreExpression(String text) {
        String t = text.replaceAll("\\s+", "");
        // ≥4.0 / >4 / =4.5 等符号表达
        java.util.regex.Matcher sym = java.util.regex.Pattern
                .compile("(>=|<=|>|<|=)([3-5](?:\\.[0-9])?)")
                .matcher(t);
        if (sym.find()) return sym.group(1) + sym.group(2);

        // 4.5分以上 / 4分起 / 4分以上
        java.util.regex.Matcher zh = java.util.regex.Pattern
                .compile("([3-5](?:\\.[0-9])?)分(以上|起|及以上)?")
                .matcher(t);
        if (zh.find()) return ">=" + zh.group(1);
        return "";
    }

    /**
     * 在搜索词表中按类型匹配输入文本中出现的值，返回最长匹配；未匹配返回 null
     */
    private String matchBestByType(String text, String type) {
        try {
            if (text == null || text.isEmpty()) return null;
            List<SearchKeyword> list = searchKeywordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SearchKeyword>()
                            .eq("type", type)
            );
            String best = null;
            int bestLen = 0;
            for (SearchKeyword sk : list) {
                String v = sk.getValue();
                if (v == null || v.isEmpty()) continue;
                if (text.contains(v) && v.length() > bestLen) {
                    best = v;
                    bestLen = v.length();
                }
            }
            return best;
        } catch (Exception e) {
            log.warn("搜索词匹配失败，type={}", type, e);
            return null;
        }
    }

    private String collectAllByType(String text, String type) {
        try {
            if (text == null || text.isEmpty()) return "";
            List<SearchKeyword> list = searchKeywordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SearchKeyword>()
                            .eq("type", type)
            );
            LinkedHashSet<String> matched = new LinkedHashSet<>();
            for (SearchKeyword sk : list) {
                String v = sk.getValue();
                if (v == null || v.isEmpty()) continue;
                if (text.contains(v)) matched.add(v);
            }
            return String.join(",", matched);
        } catch (Exception e) {
            log.warn("搜索词聚合失败，type={}", type, e);
            return "";
        }
    }

    private String collectAllTagsByType(String text, String type) {
        String joined = collectAllByType(text, type);
        if (joined == null || joined.isEmpty()) return "";
        // 去重并按 TAG_SYNONYM 做一次标准化
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String t : joined.split(",")) {
            String trim = t.trim();
            String mapped = TAG_SYNONYM.getOrDefault(trim, trim);
            normalized.add(mapped);
        }
        return String.join(",", normalized);
    }

    private static String removeMaliciousAndNoise(String s) {
        String out = s;
        // 移除链接但保留可读文字
        out = LINK_PATTERN.matcher(out).replaceAll("");
        // 移除危险 SQL / 命令关键词
        out = DANGER_SQL.matcher(out).replaceAll(" ");
        out = DANGER_CMD.matcher(out).replaceAll(" ");
        // 移除代码片段关键词
        out = CODE_PATTERN.matcher(out).replaceAll(" ");
        // 移除账号/长数字串样式
        out = ACCOUNT_LIKE.matcher(out).replaceAll(" ");
        // 清理多余空白
        return out.trim().replaceAll("\\s{2,}", " ");
    }

    private static boolean containsAny(String text, String... parts) {
        for (String p : parts) if (text.contains(p)) return true;
        return false;
    }

    // ===================== 查询词/正则抽取方法 =====================
    private static String extractByQueryTerms(String text, Map<String, String> dict) {
        for (Map.Entry<String, String> e : dict.entrySet()) {
            if (text.contains(e.getKey())) return e.getValue();
        }
        return "";
    }

    private static String extractCreditByRegex(String text) {
        String t = text.replaceAll("\\s+", "");
        // 2.0学分 / 2学分 / 两学分 / 3分课
        java.util.regex.Matcher m1 = java.util.regex.Pattern
                .compile("([0-9](?:\\.[05])?)学分")
                .matcher(t);
        if (m1.find()) return m1.group(1);
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("([23])分课")
                .matcher(t);
        if (m2.find()) return m2.group(1) + ".0";
        if (t.contains("两学分")) return "2.0";
        if (t.contains("三学分")) return "3.0";
        return "";
    }

    private static String extractCourseTimeByRegex(String text) {
        // 匹配：周一/周二... 第n-m节 或 第n节；支持“周二3-4节/周二第3节/周2第3-4节”
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("周([一二三四五六日天2-7])[第]?([0-9]{1,2})(?:-([0-9]{1,2}))?节")
                .matcher(text);
        if (m.find()) {
            String day = normalizeWeekDay(m.group(1));
            String s = m.group(2);
            String e = m.group(3);
            if (e == null || e.isEmpty()) return "周" + day + "第" + s + "节";
            return "周" + day + "第" + s + "-" + e + "节";
        }
        return "";
    }

    private static String normalizeWeekDay(String d) {
        if ("2".equals(d)) return "二";
        if ("3".equals(d)) return "三";
        if ("4".equals(d)) return "四";
        if ("5".equals(d)) return "五";
        if ("6".equals(d)) return "六";
        if ("7".equals(d)) return "日";
        return d;
    }

    private static String extractTagsByKeywords(String text) {
        Set<String> tags = new LinkedHashSet<>();
        for (Map.Entry<String, String> e : TAG_SYNONYM.entrySet()) {
            if (text.contains(e.getKey())) tags.add(e.getValue());
        }
        return String.join(",", tags);
    }

    private static String limitLength(String s) {
        return s.length() > INPUT_LIMIT ? s.substring(0, INPUT_LIMIT) : s;
    }

    private static String normalize(String s) {
        // 去除部分特殊符号与全角转半角（简化版）
        String cleaned = s.replaceAll("[！？——…～×＋&｜【】『』「」〔〕]", " ");
        // 去除控制字符
        cleaned = cleaned.replaceAll("[\\p{Cntrl}]", "");
        // 合并多空格
        return cleaned.trim().replaceAll("\\s{2,}", " ");
    }

    private static String applyMap(Map<String, String> map, String s) {
        String out = s;
        for (Map.Entry<String, String> e : map.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private static void putAll(Map<String, String> target, String[][] pairs) {
        for (String[] p : pairs) target.put(p[0], p[1]);
    }

    private static String desensitize(String input) {
        String out = SENSITIVE_PHONE.matcher(input).replaceAll(mr -> {
            String g = mr.group();
            return g.substring(0, 3) + "****" + g.substring(7);
        });
        out = SENSITIVE_EMAIL.matcher(out).replaceAll(mr -> {
            String g = mr.group();
            int at = g.indexOf('@');
            String prefix = g.substring(0, Math.min(2, at));
            return prefix + "****" + g.substring(at);
        });
        return out;
    }

    private static String extractCourseKeyword(String input) {
        // 简化：优先中文关键词（非功能词），其次英文词
        // 这里可以扩展为基于白名单/词典的抽取
        String[] candidates = input.split("[ ,，。；;|/\\n\\r\\t]+");
        for (String c : candidates) {
            if (isValidKeywordCandidate(c)) {
                return c;
            }
        }
        return "";
    }

    private static boolean isValidKeywordCandidate(String c) {
        if (c == null) return false;
        String t = c.trim();
        if (t.length() < 2) return false;
        if (isFunctionalWord(t)) return false;
        // 过滤符号率高的片段
        int punct = 0;
        for (char ch : t.toCharArray()) if (!Character.isLetterOrDigit(ch) && !isCjk(ch)) punct++;
        double ratio = (double) punct / t.length();
        if (ratio >= 0.5) return false;
        // 过滤长数字/账号样式
        if (ACCOUNT_LIKE.matcher(t).find()) return false;
        // 过长词裁剪
        return t.length() <= 20;
    }

    private static boolean isCjk(char ch) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(ch);
        return b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }
    private static boolean isFunctionalWord(String w) {
        String[] stop = {"我想", "我要", "帮我", "推荐", "找", "一下", "的", "和", "还有", "有没有", "课", "课程", "选修课"};
        for (String s : stop) if (w.contains(s)) return true;
        return false;
    }
}


