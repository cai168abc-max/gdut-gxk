package com.gdut.gxk.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.mapper.CourseBaseMapper;
import com.gdut.gxk.mapper.CourseCommentMapper;
import com.gdut.gxk.service.CourseDataMigrationService;
import com.gdut.gxk.tool.CourseIdGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 课程数据迁移服务（更新course_id为MD5规则生成的值）
 */
@Service
@Slf4j
public class CourseDataMigrationServiceImpl implements CourseDataMigrationService {

    @Resource
    private CourseBaseMapper courseBaseMapper;

    @Resource
    private CourseCommentMapper courseCommentMapper;

    /**
     * 批量更新course_base和course_comment表的course_id
     * 步骤：
     * 1. 查询所有课程基础信息
     * 2. 逐个生成新ID并更新课程表
     * 3. 同步更新对应评论表的关联ID
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void batchUpdateCourseId() {
        // 1. 查询所有课程（若数据量大，可分页处理）
        List<CourseBase> allCourses = courseBaseMapper.selectList(null);
        log.info("开始更新课程ID，共需处理{}条课程数据", allCourses.size());

        try {
            for (CourseBase course : allCourses) {
                // 2.1 获取原始课程名和教师名（用于生成新ID）
                String courseName = course.getCourseName();
                String teacherName = course.getTeacherName();
                String oldCourseId = course.getCourseId();

                // 2.2 生成新ID（使用工具类）
                String newCourseId;
                try {
                    newCourseId = CourseIdGenerator.generateCourseId(courseName, teacherName);
                } catch (IllegalArgumentException e) {
                    log.error("生成新ID失败，课程名：{}，教师名：{}，原因：{}", courseName, teacherName, e.getMessage());
                    continue; // 跳过无效数据，或根据业务抛出异常终止
                }

                // 2.3 若新ID与旧ID相同，无需更新
                if (newCourseId.equals(oldCourseId)) {
                    log.info("课程ID无需更新，课程名：{}，教师名：{}，ID：{}", courseName, teacherName, oldCourseId);
                    continue;
                }

                // 2.4 更新course_base表的course_id
                LambdaUpdateWrapper<CourseBase> courseUpdateWrapper = new LambdaUpdateWrapper<>();
                courseUpdateWrapper.eq(CourseBase::getCourseId, oldCourseId)
                        .set(CourseBase::getCourseId, newCourseId);
                int courseUpdateCount = courseBaseMapper.update(null, courseUpdateWrapper);

                // 2.5 同步更新course_comment表的course_id
                LambdaUpdateWrapper<CourseComment> commentUpdateWrapper = new LambdaUpdateWrapper<>();
                commentUpdateWrapper.eq(CourseComment::getCourseId, oldCourseId)
                        .set(CourseComment::getCourseId, newCourseId);
                int commentUpdateCount = courseCommentMapper.update(null, commentUpdateWrapper);

                log.info("更新完成：课程名={}，教师名={}，旧ID={}，新ID={}，课程表影响行数={}，评论表影响行数={}",
                        courseName, teacherName, oldCourseId, newCourseId, courseUpdateCount, commentUpdateCount);
            }
        } finally {
            log.info("所有课程ID更新处理完毕");
        }
    }
}
