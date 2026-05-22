package com.gdut.gxk.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 搜索词实体类（对应search_keyword表）
 * 实际数据库列名：keyword_id, type, value, count
 */
@Data
@TableName("search_keyword")
public class SearchKeyword {

    /**
     * 搜索词唯一标识（自增主键）
     * DB列名：keyword_id
     */
    @TableId(type = IdType.AUTO, value = "keyword_id")
    private Long keywordId;

    /**
     * 搜索词类型（如"general"/"campus"/"category"/"tag"等）
     * DB列名：type
     */
    @TableField("type")
    private String type;

    /**
     * 搜索关键词值
     * DB列名：value
     */
    @TableField("value")
    private String value;

    /**
     * 搜索次数（用于热度排序）
     * DB列名：count
     */
    @TableField("count")
    private Integer count;
}
