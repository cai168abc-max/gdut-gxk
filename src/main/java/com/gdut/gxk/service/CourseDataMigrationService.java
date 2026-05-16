package com.gdut.gxk.service;

import org.springframework.transaction.annotation.Transactional;

public interface CourseDataMigrationService {
    @Transactional(rollbackFor = Exception.class) // 事务控制：任何异常回滚
    void batchUpdateCourseId();
}
