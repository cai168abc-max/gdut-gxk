package com.gdut.gxk.service;

import com.gdut.gxk.entity.CourseBase;

import java.util.List;

/**
 * 课程推荐服务接口
 */
public interface CourseRecommendationService {

    /**
     * 根据用户偏好推荐课程
     * @param userId 用户ID
     * @param limit 返回数量
     * @return 推荐课程列表
     */
    List<CourseBase> recommendByUserPreference(String userId, int limit);

    /**
     * 推荐相似课程
     * @param courseId 课程ID
     * @param limit 返回数量
     * @return 相似课程列表
     */
    List<CourseBase> recommendSimilarCourses(String courseId, int limit);

    /**
     * 推荐热门课程
     * @param limit 返回数量
     * @return 热门课程列表
     */
    List<CourseBase> recommendPopularCourses(int limit);

    /**
     * 根据搜索历史推荐
     * @param searchHistory 搜索历史
     * @param limit 返回数量
     * @return 推荐课程列表
     */
    List<CourseBase> recommendByHistory(List<String> searchHistory, int limit);
}