package com.gdut.gxk.DTO;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import jakarta.validation.constraints.Min;

/** 课程列表页筛选+分页参数（第一页用） */
@Data
public class CourseListQueryDTO {
    // 搜索：模糊匹配课程名/教师名
    private String keyword;

    // 筛选：课程类别/校区/标签（精确匹配/包含匹配）
    private String category; // 如“自然科学与工程技术类”
    private String campus;   // 如“广东工业大学龙洞校区”
    private String tag;      // 如“给分高”

    // 分页：默认1页10条，未筛选时前端传pageSize=100
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;

    @Range(min = 1, max = 100, message = "每页条数需在1-100之间")
    private Integer pageSize = 20;
}