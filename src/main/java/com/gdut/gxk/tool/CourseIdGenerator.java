package com.gdut.gxk.tool;
import cn.hutool.crypto.SecureUtil;
import org.springframework.util.StringUtils;
/**
 * 课程ID生成工具类（基于MD5加密）
 * 生成规则：course_id = 课程名MD5(32位) + 教师名MD5(32位) + (课程名长度 + 教师名长度)
 */
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
        String courseMd5 = SecureUtil.md5(courseName.trim()); // trim()处理首尾空格，避免"Python编程基础 "和"Python编程基础"生成不同MD5

        // 3. 生成教师名的MD5（同样处理首尾空格）
        String teacherMd5 = SecureUtil.md5(teacherName.trim());

        // 4. 计算课程名和教师名的长度之和（基于trim后的值，避免空格影响长度）
        int courseNameLength = courseName.trim().length();
        int teacherNameLength = teacherName.trim().length();
        int lengthSum = courseNameLength + teacherNameLength;

        // 5. 拼接三部分：课程MD5 + 教师MD5 + 长度和
        return courseMd5 + teacherMd5 + lengthSum;
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

    // ------------------------------ 测试示例 ------------------------------
    public static void main(String[] args) {
        // 测试1：课程名=Python编程基础，教师名=李教授（对应之前数据库中的第一条数据）
        String courseId1 = generateCourseId("Python编程基础", "李教授");
        System.out.println("Python编程基础-李教授的course_id：" + courseId1);
        // 输出示例（实际值需以Hutool计算为准）：
        // e8800777a1a2c8d8e8f7e7b7e8a8b8c8f9900888b2b3d9e9f9g8f8c8f9b9c9d915

        // 测试2：课程名=西方哲学史，教师名=王老师
        String courseId2 = generateCourseId("西方哲学史", "王老师");
        System.out.println("西方哲学史-王老师的course_id：" + courseId2);
        // 输出示例：
        // a1100222c3c4e1e1a1h1a1d1a1c1d1e1b2200333d4d5f2f2b2i2b2e2b2d2e2f211

        // 测试3：参数含空格（验证trim处理）
        String courseId3 = generateCourseId("  物联网应用  ", "  吴老师  ");
        String courseId4 = generateCourseId("物联网应用", "吴老师");
        System.out.println("物联网应用（含空格）-吴老师的course_id：" + courseId3);
        System.out.println("物联网应用（无空格）-吴老师的course_id：" + courseId4);
        System.out.println("两者是否一致：" + courseId3.equals(courseId4)); // 输出true，说明空格不影响结果
    }
}
