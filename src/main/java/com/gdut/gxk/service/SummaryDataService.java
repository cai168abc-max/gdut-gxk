package com.gdut.gxk.service;

import com.gdut.gxk.DTO.SummaryDataDTO;

/** 总结数据页业务服务（第四页用） */
public interface SummaryDataService {
    /** 获取总结统计数据（支持校区+课程类型筛选） */
    SummaryDataDTO getSummaryData(String campus, String category);
}
