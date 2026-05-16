package com.gdut.gxk.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gdut.gxk.DTO.CourseListQueryDTO;
import com.gdut.gxk.VO.CourseListVO;

/** 课程列表页业务服务（第一页用） */
public interface CourseListService {
    /** 多条件筛选课程（返回含学分的VO列表） */
    IPage<CourseListVO> queryCourseList(CourseListQueryDTO queryDTO);

    /** 未筛选时返回前100门课程（前端默认显示） */
    IPage<CourseListVO> getDefaultTop100Courses();
}
