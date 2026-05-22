package com.gdut.gxk.tool;

import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 课程ID生成工具类（基于MD5加密）
 * 生成规则：course_id = 课程名MD5(32位) + 教师名MD5(32位) + (课程名长度 + 教师名长度)
 */
@Slf4j
public final class CourseIdGenerator {

    /**
     * 私有构造方法：防止工具类被实例化
     */
    private CourseIdGenerator() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 根据课程名称和教师名生成唯一课程ID
     * @param courseName 课程名称（不可为null/空字符串/纯空格）
     * @param teacherName 授课教师名（不可为null/空字符串/纯空格）
     * @return 符合规则的课程唯一ID（长度：32+32+N，N为长度和的位数，如15→1位，20→2位）
     * @throws IllegalArgumentException 当课程名或教师名为无效值时抛出
     */
    public static String generateCourseId(String courseName, String teacherName) {
        // 1. 参数校验：避免无效值导致MD5重复或异常
        validateParam(courseName, "课程名称");
        validateParam(teacherName, "授课教师名");

        // 2. 生成课程名的MD5（32位小写，Hutool默认生成32位小写MD5）
        String courseMd5 = SecureUtil.md5(courseName.trim()); // trim()处理首尾空格

        // 3. 生成教师名的MD5（同样处理首尾空格）
        String teacherMd5 = SecureUtil.md5(teacherName.trim());

        // 4. 计算课程名和教师名的长度之和（基于trim后的值，避免空格影响长度）
        int courseNameLength = courseName.trim().length();
        int teacherNameLength = teacherName.trim().length();
        int lengthSum = courseNameLength + teacherNameLength;

        // 5. 拼接三部分：课程MD5 + 教师MD5 + 长度和
        String courseId = courseMd5 + teacherMd5 + lengthSum;
        log.debug("生成课程ID: 课程名={}, 教师名={}, courseId={}", courseName, teacherName, courseId);
        
        return courseId;
    }

    /**
     * 私有辅助方法：校验参数是否有效（非null、非空字符串、非纯空格）
     * @param param 待校验的参数值
     * @param paramName 参数名称（用于异常提示）
     */
    private static void validateParam(String param, String paramName) {
        // 校验null
        if (param == null) {
            throw new IllegalArgumentException(paramName + "不可为null");
        }
        // 校验空字符串或纯空格（trim后长度为0）
        if (!StringUtils.hasText(param.trim())) {
            throw new IllegalArgumentException(paramName + "不可为空字符串或纯空格");
        }
    }
}
