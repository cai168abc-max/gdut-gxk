package com.gdut.gxk.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gdut.gxk.DTO.CourseListQueryDTO;
import com.gdut.gxk.VO.CourseListVO;
import com.gdut.gxk.common.Result;
import com.gdut.gxk.service.CourseListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 课程列表页控制器（第一页用）
 * 支持多条件筛选、分页查询、默认显示等功能
 */
@RestController
@RequestMapping("/api/course")
@Slf4j
public class CourseListController {

    @Resource
    private CourseListService courseListService;

    /**
     * 多条件筛选课程列表
     * @param queryDTO 查询条件（关键词、类别、校区、标签、分页参数）
     * @return 分页的课程列表VO
     */
    @PostMapping("/list")
    public Result<IPage<CourseListVO>> queryCourseList(@Valid @RequestBody CourseListQueryDTO queryDTO) {
        try {
            log.info("查询课程列表，条件：{}", queryDTO);
            IPage<CourseListVO> result = courseListService.queryCourseList(queryDTO);
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询课程列表失败", e);
            return Result.error("查询课程列表失败，请稍后重试");
        }
    }

    /**
     * 获取默认前100门课程（首页默认显示）
     * @return 分页的课程列表VO
     */
    @GetMapping("/default")
    public Result<IPage<CourseListVO>> getDefaultCourses() {
        try {
            log.info("获取默认课程列表");
            IPage<CourseListVO> result = courseListService.getDefaultTop100Courses();
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取默认课程列表失败", e);
            return Result.error("获取默认课程列表失败，请稍后重试");
        }
    }

    /**
     * 简单搜索接口（GET方式，用于快速搜索）
     * @param keyword 搜索关键词
     * @param pageNum 页码（默认1）
     * @param pageSize 每页大小（默认10）
     * @return 分页的课程列表VO
     */
    @GetMapping("/search")
    public Result<IPage<CourseListVO>> searchCourses(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            log.info("搜索课程，关键词：{}，页码：{}，每页：{}", keyword, pageNum, pageSize);
            
            CourseListQueryDTO queryDTO = new CourseListQueryDTO();
            queryDTO.setKeyword(keyword);
            queryDTO.setPageNum(pageNum);
            queryDTO.setPageSize(pageSize);
            
            IPage<CourseListVO> result = courseListService.queryCourseList(queryDTO);
            return Result.success(result);
        } catch (Exception e) {
            log.error("搜索课程失败", e);
            return Result.error("搜索课程失败，请稍后重试");
        }
    }
}

