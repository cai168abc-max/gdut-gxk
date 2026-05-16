package com.gdut.gxk.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.VO.CourseDetailVO;

import jakarta.validation.Valid;

/** 课程详情评论页业务服务（第二页用） */
public interface CourseDetailService {
    /** 获取课程详情（含时间/安排/总评论数/学分的VO） */
    CourseDetailVO getCourseDetail(String courseId);

    /** 新增评论（自动更新总评论数缓存） */
    CourseComment addCourseComment(@Valid CourseComment comment);

    /** 删除评论（自动更新总评论数缓存） */
    boolean deleteComment(Long commentId);

    /** 评论分页查询（按时间/评分筛选） */
    IPage<CourseComment> getCommentPage(String courseId, Integer pageNum, Integer pageSize);
}