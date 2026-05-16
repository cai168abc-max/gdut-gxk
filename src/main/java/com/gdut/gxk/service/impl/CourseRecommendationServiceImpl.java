package com.gdut.gxk.service.impl;

import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.mapper.CourseBaseMapper;
import com.gdut.gxk.service.CourseRecommendationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 课程推荐服务实现
 * 支持多种推荐策略：热门推荐、相似推荐、历史推荐等
 */
@Service
@Slf4j
public class CourseRecommendationServiceImpl implements CourseRecommendationService {

    @Resource
    private CourseBaseMapper courseBaseMapper;

    @Override
    public List<CourseBase> recommendByUserPreference(String userId, int limit) {
        log.debug("根据用户偏好推荐课程 - userId: {}, limit: {}", userId, limit);
        
        // 简化实现：返回评分最高的课程
        QueryWrapper<CourseBase> qw = new QueryWrapper<>();
        qw.orderByDesc("score")
          .last("LIMIT " + Math.min(limit, 50));
        
        return courseBaseMapper.selectList(qw);
    }

    @Override
    public List<CourseBase> recommendSimilarCourses(String courseId, int limit) {
        log.debug("推荐相似课程 - courseId: {}, limit: {}", courseId, limit);
        
        CourseBase course = courseBaseMapper.selectById(courseId);
        if (course == null) {
            log.warn("课程不存在 - courseId: {}", courseId);
            return Collections.emptyList();
        }

        // 根据类别和标签推荐相似课程
        QueryWrapper<CourseBase> qw = new QueryWrapper<>();
        qw.ne("course_id", courseId)
          .eq("category", course.getCategory())
          .orderByDesc("score")
          .last("LIMIT " + Math.min(limit, 50));
        
        List<CourseBase> similar = courseBaseMapper.selectList(qw);
        
        // 如果相似课程不足，补充热门课程
        if (similar.size() < limit) {
            List<CourseBase> popular = recommendPopularCourses(limit - similar.size());
            for (CourseBase pop : popular) {
                boolean exists = similar.stream().anyMatch(c -> c.getCourseId().equals(pop.getCourseId()));
                if (!exists) {
                    similar.add(pop);
                }
            }
        }
        
        return similar;
    }

    @Override
    public List<CourseBase> recommendPopularCourses(int limit) {
        log.debug("推荐热门课程 - limit: {}", limit);
        
        // 根据选课人数排序
        QueryWrapper<CourseBase> qw = new QueryWrapper<>();
        qw.orderByDesc("score")
          .last("LIMIT " + Math.min(limit, 50));
        
        return courseBaseMapper.selectList(qw);
    }

    @Override
    public List<CourseBase> recommendByHistory(List<String> searchHistory, int limit) {
        log.debug("根据搜索历史推荐 - history: {}, limit: {}", searchHistory, limit);
        
        if (searchHistory == null || searchHistory.isEmpty()) {
            return recommendPopularCourses(limit);
        }

        List<CourseBase> result = new ArrayList<>();
        
        // 根据搜索历史中的关键词推荐
        for (String keyword : searchHistory) {
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }
            
            QueryWrapper<CourseBase> qw = new QueryWrapper<>();
            qw.and(w -> w.like("course_name", keyword.trim())
                         .or().like("teacher_name", keyword.trim())
                         .or().like("tag", keyword.trim()))
              .orderByDesc("score")
              .last("LIMIT " + Math.min(limit / searchHistory.size(), 10));
            
            List<CourseBase> courses = courseBaseMapper.selectList(qw);
            
            // 去重
            for (CourseBase course : courses) {
                boolean exists = result.stream().anyMatch(c -> c.getCourseId().equals(course.getCourseId()));
                if (!exists) {
                    result.add(course);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            
            if (result.size() >= limit) {
                break;
            }
        }
        
        // 如果结果不足，补充热门课程
        if (result.size() < limit) {
            List<CourseBase> popular = recommendPopularCourses(limit - result.size());
            for (CourseBase pop : popular) {
                boolean exists = result.stream().anyMatch(c -> c.getCourseId().equals(pop.getCourseId()));
                if (!exists) {
                    result.add(pop);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
        }
        
        return result;
    }
}