package com.gdut.gxk.controller;

import com.gdut.gxk.service.CourseDataMigrationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/admin/data")
public class DataMigrationController {

    @Resource
    private CourseDataMigrationService migrationService;

    /**
     * 触发课程ID批量更新（仅管理员可调用）
     */
    @PostMapping("/update-course-id")
    public String updateCourseId() {
        try {
            migrationService.batchUpdateCourseId();
            return "课程ID更新成功";
        } catch (Exception e) {
            return "更新失败，请稍后重试";
        }
    }
}
