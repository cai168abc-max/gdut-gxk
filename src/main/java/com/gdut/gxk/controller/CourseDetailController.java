package com.gdut.gxk.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gdut.gxk.VO.CourseDetailVO;
import com.gdut.gxk.common.Result;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.service.CourseDetailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 课程详情页控制器（第二页用）
 * 支持课程详情查询、评论增删改查等功能
 */
@RestController
@RequestMapping("/api/course/detail")
@Slf4j
public class CourseDetailController {

    @Resource
    private CourseDetailService courseDetailService;

    /**
     * 获取课程详情
     * @param courseId 课程ID
     * @return 课程详情VO（含基础信息、时间安排、学分、总评论数等）
     */
    @GetMapping("/{courseId}")
    public Result<CourseDetailVO> getCourseDetail(@PathVariable String courseId) {
        try {
            log.info("查询课程详情，courseId：{}", courseId);
            CourseDetailVO detail = courseDetailService.getCourseDetail(courseId);
            if (detail == null || detail.getCourseId() == null) {
                return Result.notFound("课程不存在");
            }
            return Result.success(detail);
        } catch (Exception e) {
            log.error("查询课程详情失败，courseId：{}", courseId, e);
            return Result.error("查询课程详情失败：" + e.getMessage());
        }
    }

    /**
     * 获取课程评论列表（分页）
     * @param courseId 课程ID
     * @param pageNum 页码（默认1）
     * @param pageSize 每页大小（默认10，最大50）
     * @return 分页的评论列表
     */
    @GetMapping("/{courseId}/comments")
    public Result<IPage<CourseComment>> getCommentList(
            @PathVariable String courseId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            log.info("查询课程评论，courseId：{}，页码：{}，每页：{}", courseId, pageNum, pageSize);
            
            // 限制每页最大50条
            if (pageSize > 50) {
                pageSize = 50;
            }
            
            IPage<CourseComment> comments = courseDetailService.getCommentPage(courseId, pageNum, pageSize);
            return Result.success(comments);
        } catch (Exception e) {
            log.error("查询课程评论失败，courseId：{}", courseId, e);
            return Result.error("查询课程评论失败：" + e.getMessage());
        }
    }

    /**
     * 新增课程评论
     * @param courseId 课程ID
     * @param comment 评论信息
     * @return 新增的评论信息
     */
    @PostMapping("/{courseId}/comments")
    public Result<CourseComment> addComment(
            @PathVariable String courseId,
            @Valid @RequestBody CourseComment comment) {
        try {
            log.info("新增课程评论，courseId：{}，评论：{}", courseId, comment);
            
            // 设置课程ID
            comment.setCourseId(courseId);
            
            CourseComment savedComment = courseDetailService.addCourseComment(comment);
            return Result.success("评论添加成功", savedComment);
        } catch (IllegalArgumentException e) {
            log.warn("新增评论参数错误，courseId：{}，错误：{}", courseId, e.getMessage());
            return Result.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("新增课程评论失败，courseId：{}", courseId, e);
            return Result.error("新增评论失败：" + e.getMessage());
        }
    }

    /**
     * 删除课程评论
     * @param courseId 课程ID
     * @param commentId 评论ID
     * @return 删除结果
     */
    @DeleteMapping("/{courseId}/comments/{commentId}")
    public Result<Boolean> deleteComment(
            @PathVariable String courseId,
            @PathVariable Long commentId) {
        try {
            log.info("删除课程评论，courseId：{}，commentId：{}", courseId, commentId);
            
            boolean success = courseDetailService.deleteComment(commentId);
            if (success) {
                return Result.success("评论删除成功", true);
            } else {
                return Result.error("评论删除失败");
            }
        } catch (Exception e) {
            log.error("删除课程评论失败，courseId：{}，commentId：{}", courseId, commentId, e);
            return Result.error("删除评论失败：" + e.getMessage());
        }
    }

    /**
     * 获取课程基础信息（轻量级查询，不包含评论统计）
     * @param courseId 课程ID
     * @return 课程基础信息
     */
    @GetMapping("/{courseId}/basic")
    public Result<CourseDetailVO> getCourseBasicInfo(@PathVariable String courseId) {
        try {
            log.info("查询课程基础信息，courseId：{}", courseId);
            CourseDetailVO detail = courseDetailService.getCourseDetail(courseId);
            if (detail == null || detail.getCourseId() == null) {
                return Result.notFound("课程不存在");
            }
            return Result.success(detail);
        } catch (Exception e) {
            log.error("查询课程基础信息失败，courseId：{}", courseId, e);
            return Result.error("查询课程基础信息失败：" + e.getMessage());
        }
    }
}

