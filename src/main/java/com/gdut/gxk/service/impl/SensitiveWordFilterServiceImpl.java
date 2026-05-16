package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.SensitiveWordFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 敏感词过滤服务实现
 * 使用DFA算法实现高效敏感词匹配
 */
@Service
@Slf4j
public class SensitiveWordFilterServiceImpl implements SensitiveWordFilterService {

    /**
     * 敏感词替换字符
     */
    private static final String DEFAULT_REPLACEMENT = "*";

    /**
     * 敏感词DFA树
     */
    private Map<Character, Map> sensitiveWordTree = new HashMap<>();

    /**
     * 是否启用敏感词过滤
     */
    @Value("${ai.content-filter.enabled:true}")
    private boolean enabled;

    /**
     * 敏感词列表（可从配置或数据库加载）
     */
    private static final Set<String> SENSITIVE_WORDS = Set.of(
            // 政治敏感词
            "法轮功", "台独", "藏独", "分裂", "颠覆", "反动",
            "卖国", "汉奸", "反革命", "暴乱", "示威", "静坐",
            
            // 色情低俗词
            "色情", "淫秽", "嫖娼", "卖淫", "裸聊", "性交易",
            "AV", "三级片", "黄色", "成人", "露点", "挑逗",
            
            // 暴力恐怖词
            "杀人", "自杀", "爆炸", "恐怖", "枪支", "弹药",
            "毒品", "吸毒", "贩毒", "绑架", "勒索", "抢劫",
            
            // 恶意广告词
            "赌博", "博彩", "六合彩", "时时彩", "网络赌博",
            "比特币", "虚拟货币", "传销", "诈骗", "中奖",
            
            // 人身攻击词
            "傻逼", "草泥马", "操你妈", "他妈的", "滚蛋",
            "去死", "废物", "垃圾", "脑残", "智障", "丑八怪",
            
            // 其他敏感词
            "习近平", "李克强", "胡锦涛", "温家宝", "江泽民",
            "中南海", "天安门", "人民大会堂", "国务院"
    );

    /**
     * 初始化敏感词DFA树
     */
    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("敏感词过滤功能已禁用");
            return;
        }
        
        log.info("开始初始化敏感词DFA树，敏感词数量：{}", SENSITIVE_WORDS.size());
        
        for (String word : SENSITIVE_WORDS) {
            Map<Character, Map> current = sensitiveWordTree;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (!current.containsKey(c)) {
                    current.put(c, new HashMap<>());
                }
                current = current.get(c);
            }
            // 标记词尾
            current.put('$', null);
        }
        
        log.info("敏感词DFA树初始化完成");
    }

    @Override
    public boolean containsSensitiveWord(String text) {
        if (!enabled || text == null || text.isEmpty()) {
            return false;
        }
        
        for (int i = 0; i < text.length(); i++) {
            if (matchSensitiveWord(text, i) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String filter(String text) {
        return filter(text, DEFAULT_REPLACEMENT);
    }

    @Override
    public String filter(String text, String replacement) {
        if (!enabled || text == null || text.isEmpty()) {
            return text;
        }
        
        StringBuilder result = new StringBuilder(text);
        int offset = 0;
        
        for (int i = 0; i < text.length(); i++) {
            int matchLength = matchSensitiveWord(text, i);
            if (matchLength > 0) {
                // 替换敏感词
                String replaceStr = replacement.repeat(matchLength);
                result.replace(i + offset, i + offset + matchLength, replaceStr);
                offset += replaceStr.length() - matchLength;
                i += matchLength - 1;
                log.debug("过滤敏感词: 位置={}, 长度={}", i, matchLength);
            }
        }
        
        return result.toString();
    }

    /**
     * 在指定位置查找匹配的敏感词
     * @param text 文本
     * @param startIndex 起始位置
     * @return 匹配的敏感词长度，0表示未匹配
     */
    private int matchSensitiveWord(String text, int startIndex) {
        Map<Character, Map> current = sensitiveWordTree;
        int maxLength = 0;
        
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!current.containsKey(c)) {
                break;
            }
            
            current = current.get(c);
            if (current.containsKey('$')) {
                maxLength = i - startIndex + 1;
            }
        }
        
        return maxLength;
    }
}