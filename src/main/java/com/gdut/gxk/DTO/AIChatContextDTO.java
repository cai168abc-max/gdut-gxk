package com.gdut.gxk.DTO;
import com.gdut.gxk.entity.CourseComment;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** AI对话页上下文（第三页用，存储清洗后条件/AI返回/评论数据） */
@Data
public class AIChatContextDTO {
    // 清洗后的查询条件（如["courseName=Python","campus=龙洞校区"]）
    private List<String> cleanedQueryConditions = new ArrayList<>();
    // AI返回结果列表（按对话顺序）
    private List<String> aiResponses = new ArrayList<>();
    // 对话中使用的用户评论数据
    private List<CourseComment> usedComments = new ArrayList<>();
    // 最后更新时间（用于缓存过期判断）
    private Long lastUpdateTime = System.currentTimeMillis();
    // 连续追问次数（用于限制追问次数）
    private int followUpCount = 0;
}
