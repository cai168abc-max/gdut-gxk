package com.gdut.gxk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.entity.CourseComment;
import com.gdut.gxk.mapper.CourseBaseMapper;
import com.gdut.gxk.mapper.CourseCommentMapper;
import com.gdut.gxk.service.CourseDetailService;
import com.gdut.gxk.service.RedisCacheService;
import com.gdut.gxk.VO.CourseDetailVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 课程详情评论页服务实现 */
@Service
@Slf4j
public class CourseDetailServiceImpl extends ServiceImpl<CourseCommentMapper, CourseComment>
        implements CourseDetailService {

    @Resource
    private CourseBaseMapper courseBaseMapper;

    @Resource
    private CourseCommentMapper commentMapper;

    @Resource
    private RedisCacheService redisCacheService;

    // 缓存Key前缀
    private static final String COURSE_DETAIL_KEY = "redis:course:info:";
    private static final String COMMENT_LIST_KEY = "redis:comment:list:";

    @Override
    public CourseDetailVO getCourseDetail(String courseId) {
        if (courseId == null || courseId.trim().isEmpty()) {
            log.warn("查询详情失败：courseId为空");
            return null;
        }
        String trimmedCourseId = courseId.trim();
        String cacheKey = COURSE_DETAIL_KEY + trimmedCourseId;

        // 1. 查缓存（存储CourseDetailVO）
        CourseDetailVO cachedVO = (CourseDetailVO) redisCacheService.get(cacheKey);
        if (cachedVO != null) {
            log.debug("详情缓存命中，courseId={}", trimmedCourseId);
            return cachedVO;
        }

        // 2. 查课程基础信息（含时间/安排/学分）
        LambdaQueryWrapper<CourseBase> courseQuery = new LambdaQueryWrapper<>();
        courseQuery.eq(CourseBase::getCourseId, trimmedCourseId)
                .select(
                        CourseBase::getCourseId, CourseBase::getCourseName, CourseBase::getTeacherName,
                        CourseBase::getScore, CourseBase::getCampus, CourseBase::getCollege,
                        CourseBase::getAiSummary, CourseBase::getTag,
                        CourseBase::getCredit, CourseBase::getCourseTime, CourseBase::getCourseSchedule // 新增字段
                );
        CourseBase course = courseBaseMapper.selectOne(courseQuery);
        if (course == null) {
            log.warn("查询详情失败：courseId不存在");
            redisCacheService.set(cacheKey, new CourseDetailVO(), 1, TimeUnit.MINUTES);
            return new CourseDetailVO();
        }

        // 3. 统计总评论数（新增：动态计算）
        LambdaQueryWrapper<CourseComment> commentCountQuery = new LambdaQueryWrapper<>();
        commentCountQuery.eq(CourseComment::getCourseId, trimmedCourseId);
        int totalCommentCount = commentMapper.selectCount(commentCountQuery).intValue();

        // 4. 转换为CourseDetailVO
        CourseDetailVO detailVO = CourseDetailVO.fromEntity(course, totalCommentCount);

        // 5. 写入缓存（35分钟过期）
        redisCacheService.set(cacheKey, detailVO, 35 * 60, TimeUnit.SECONDS);
        log.debug("详情查询完成，courseId={}（含时间/安排/总评论数/学分）", trimmedCourseId);

        return detailVO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CourseComment addCourseComment(CourseComment comment) {
        // 1. 参数校验
        validateCommentParam(comment);

        // 2. 检查课程存在性
        CourseBase course = courseBaseMapper.selectById(comment.getCourseId());
        if (course == null) {
            throw new IllegalArgumentException("新增评论失败：课程不存在");
        }

        // 3. 插入评论
        int insertCount = commentMapper.insert(comment);
        if (insertCount != 1) {
            throw new RuntimeException("新增评论失败：数据库插入异常");
        }
        log.debug("新增评论成功，commentId={}", comment.getCommentId());
        String commentPrefix = COMMENT_LIST_KEY + comment.getCourseId();
        // 4. 删除缓存（评论列表+详情，因总评论数变化）
        redisCacheService.deletePattern(commentPrefix + ":*");
        log.debug("新增评论ID：{}", comment.getCommentId());
        redisCacheService.delete(commentPrefix);
        redisCacheService.delete(COURSE_DETAIL_KEY + comment.getCourseId());

        // 5. 返回新增评论
        return commentMapper.selectById(comment.getCommentId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteComment(Long commentId) {
        if (commentId == null) {
            log.warn("删除评论失败：commentId为空");
            return false;
        }

        // 1. 查询评论关联的课程ID
        CourseComment comment = getById(commentId);
        if (comment == null) {
            log.warn("删除评论失败：评论不存在");
            return false;
        }
        String courseId = comment.getCourseId();

        // 2. 删除评论
        boolean deleteSuccess = removeById(commentId);
        if (deleteSuccess) {
            log.debug("删除评论成功，commentId={}", commentId);
            // 3. 删除缓存（评论列表+详情）
            redisCacheService.delete(COMMENT_LIST_KEY + courseId);
            redisCacheService.delete(COURSE_DETAIL_KEY + courseId);
        }
        return deleteSuccess;
    }

    @Override
    public IPage<CourseComment> getCommentPage(String courseId, Integer pageNum, Integer pageSize) {
        // 参数默认值
        if (pageNum == null || pageNum < 1) pageNum = 1;
        if (pageSize == null || pageSize < 1 || pageSize > 50) pageSize = 10;

        // 1. 缓存Key
        String cacheKey = COMMENT_LIST_KEY + courseId + ":" + pageNum + ":" + pageSize;

        // 2. 查缓存
        IPage<CourseComment> cachedPage = (IPage<CourseComment>) redisCacheService.get(cacheKey);
        if (cachedPage != null) {
            return cachedPage;
        }

        // 3. 查数据库（按时间降序）
        LambdaQueryWrapper<CourseComment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CourseComment::getCourseId, courseId)
                .orderByDesc(CourseComment::getCreateTime);

        Page<CourseComment> page = new Page<>(pageNum, pageSize);
        IPage<CourseComment> resultPage = commentMapper.selectPage(page, queryWrapper);

        // 4. 写入缓存
        if (resultPage.getRecords().isEmpty()) {
            redisCacheService.set(cacheKey, resultPage, 1, TimeUnit.MINUTES);
        } else {
            redisCacheService.set(cacheKey, resultPage, 10 * 60, TimeUnit.SECONDS);
        }
        return resultPage;
    }

    /** 评论参数业务校验 */
    private void validateCommentParam(CourseComment comment) {
        if (comment.getScore() < 1 || comment.getScore() > 5) {
            throw new IllegalArgumentException("评分必须为1-5分");
        }
        List<String> validAttendance = List.of("从不", "偶尔", "每节课点名", "随机点名");
        if (!validAttendance.contains(comment.getAttendanceFrequency())) {
            throw new IllegalArgumentException("考勤频率必须为：" + String.join("/", validAttendance));
        }
        List<String> validExamType = List.of("论文", "开卷", "闭卷", "报告+答辩", "实践考核", "报告");
        if (!validExamType.contains(comment.getExamType())) {
            throw new IllegalArgumentException("考试形式必须为：" + String.join("/", validExamType));
        }
        if (comment.getContent().trim().length() < 10) {
            throw new IllegalArgumentException("评论内容需至少10个字符");
        }
    }
}