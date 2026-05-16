package com.gdut.gxk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 多维度搜索词实体类（对应search_keyword表）
 */
@Data
@TableName("search_keyword")  // 绑定数据库表名
public class SearchKeyword {

    /**
     * 搜索词唯一标识（自增主键）
     */
    @TableId(type = IdType.AUTO, value = "keyword_id")
    private Long keywordId;

    /**
     * 搜索词类型（预设6大维度：course_name/teacher_name/campus/college/tag/category）
     * 后端需校验取值范围
     */
    @TableField("type")
    private String type;

    /**
     * 搜索词具体值（如“Python编程基础”“广东工业大学龙洞校区”）
     */
    @TableField("value")
    private String value;

    /**
     * 搜索次数（默认0，用户搜索成功一次累加1）
     * 数据库默认值0，后端可直接赋值或通过SQL累加
     */
    @TableField("count")
    private Integer count;
}
