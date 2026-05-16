package com.gdut.gxk.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 课程评论实体类（对应course_comment表）
 */
@Data
@TableName("course_comment")  // 绑定数据库表名
public class CourseComment {

    /**
     * 评论唯一标识（自增主键）
     */
    @TableId(type = IdType.AUTO, value = "comment_id")
    private Long commentId;

    /**
     * 关联课程ID（对应course_base.course_id）
     * 需与CourseBase的courseId保持一致（MD5拼接规则）
     */
    @TableField("course_id")
    private String courseId;

    /**
     * 考勤频率（预设值：从不/偶尔/每节课点名/随机点名）
     * 后端需校验取值范围
     */
    @TableField("attendance_frequency")
    private String attendanceFrequency;

    /**
     * 考试形式（预设值：论文/开卷/闭卷/报告+答辩/实践考核/报告）
     * 已新增“报告”选项，后端需校验取值范围
     */
    @TableField("exam_type")
    private String examType;

    /**
     * 用户评分（1-5分，1分最差，5分最好）
     * 后端需校验取值范围
     */
    @TableField("score")
    private Integer score;

    /**
     * 评论内容（需过滤特殊字符，防止XSS攻击）
     */
    @TableField("content")
    private String content;

    /**
     * 评论提交时间（新评论在前，按此字段倒序排序）
     * 数据库默认CURRENT_TIMESTAMP，后端通过填充器自动赋值
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}