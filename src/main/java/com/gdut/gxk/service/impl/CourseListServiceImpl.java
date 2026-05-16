package com.gdut.gxk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gdut.gxk.DTO.CourseListQueryDTO;
import com.gdut.gxk.entity.CourseBase;
import com.gdut.gxk.mapper.CourseBaseMapper;
import com.gdut.gxk.service.CourseListService;
import com.gdut.gxk.service.RedisCacheService;
import com.gdut.gxk.VO.CourseListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;
import cn.hutool.crypto.SecureUtil;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** 课程列表页服务实现 */
@Service
@Slf4j
public class CourseListServiceImpl extends ServiceImpl<CourseBaseMapper, CourseBase>
        implements CourseListService {

    @Resource
    private CourseBaseMapper courseBaseMapper;

    @Resource
    private RedisCacheService redisCacheService;

    // 课程列表缓存Key前缀（Redis，20分钟过期）
    private static final String COURSE_LIST_CACHE_KEY = "redis:course:list:query:";
    private static final long COURSE_LIST_EXPIRE = 20 * 60;

    @Override
    public IPage<CourseListVO> queryCourseList(CourseListQueryDTO queryDTO) {
        // 1. 生成缓存Key（基于筛选参数MD5，避免Key过长）
        String cacheKey = generateCacheKey(queryDTO);

        // 2. 查缓存（存储IPage<CourseListVO>）
        IPage<CourseListVO> cachedPage = (IPage<CourseListVO>) redisCacheService.get(cacheKey);
        if (cachedPage != null) {
            log.debug("课程列表缓存命中，cacheKey={}", cacheKey);
            return cachedPage;
        }

        // 3. 构建查询条件（含学分字段）
        LambdaQueryWrapper<CourseBase> queryWrapper = new LambdaQueryWrapper<>();
        // 搜索：课程名/教师名模糊匹配
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().trim().isEmpty()) {
            String keyword = "%" + queryDTO.getKeyword().trim() + "%";
            queryWrapper.and(w -> w.like(CourseBase::getCourseName, keyword)
                    .or().like(CourseBase::getTeacherName, keyword));
        }
        // 筛选：类别/校区精确匹配，标签包含匹配
        if (queryDTO.getCategory() != null && !queryDTO.getCategory().trim().isEmpty()) {
            queryWrapper.eq(CourseBase::getCategory, queryDTO.getCategory().trim());
        }
        if (queryDTO.getCampus() != null && !queryDTO.getCampus().trim().isEmpty()) {
            queryWrapper.eq(CourseBase::getCampus, queryDTO.getCampus().trim());
        }
        if (queryDTO.getTag() != null && !queryDTO.getTag().trim().isEmpty()) {
            queryWrapper.apply("FIND_IN_SET('" + queryDTO.getTag().trim() + "', tag)");
        }
        // 排序：评分降序→课程名升序
        queryWrapper.orderByDesc(CourseBase::getScore)
                .orderByAsc(CourseBase::getCourseName);

        // 4. 分页查询CourseBase
        Page<CourseBase> coursePage = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        IPage<CourseBase> resultPage = courseBaseMapper.selectPage(coursePage, queryWrapper);

        // 5. 转换为CourseListVO（含学分）
        List<CourseListVO> voList = resultPage.getRecords().stream()
                .map(CourseListVO::fromEntity)
                .collect(Collectors.toList());

        // 6. 封装VO分页对象
        Page<CourseListVO> voPage = new Page<>();
        voPage.setRecords(voList);
        voPage.setTotal(resultPage.getTotal());
        voPage.setSize(resultPage.getSize());
        voPage.setCurrent(resultPage.getCurrent());
        voPage.setPages(resultPage.getPages());

        // 7. 写入缓存（空结果1分钟过期）
        if (voList.isEmpty()) {
            redisCacheService.set(cacheKey, voPage, 1, TimeUnit.MINUTES);
        } else {
            redisCacheService.set(cacheKey, voPage, COURSE_LIST_EXPIRE, TimeUnit.SECONDS);
        }

        log.debug("课程列表查询完成，条件={}，总条数={}（含学分）", queryDTO, voPage.getTotal());
        return voPage;
    }

    @Override
    public IPage<CourseListVO> getDefaultTop100Courses() {
        CourseListQueryDTO defaultQuery = new CourseListQueryDTO();
        defaultQuery.setPageNum(1);
        defaultQuery.setPageSize(100); // 未筛选显示前100
        return queryCourseList(defaultQuery);
    }

    /** 生成缓存Key：筛选参数MD5哈希 */
    private String generateCacheKey(CourseListQueryDTO queryDTO) {
        String paramStr = String.format(
                "keyword=%s&category=%s&campus=%s&tag=%s&pageNum=%d&pageSize=%d",
                queryDTO.getKeyword(), queryDTO.getCategory(), queryDTO.getCampus(),
                queryDTO.getTag(), queryDTO.getPageNum(), queryDTO.getPageSize()
        );
        return COURSE_LIST_CACHE_KEY + SecureUtil.md5(paramStr);
    }
}