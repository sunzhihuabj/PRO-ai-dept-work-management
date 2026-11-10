package com.dept.mgmt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 经验教训表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lessons_learned")
public class LessonsLearned extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long projectId;
    private Long phaseId;
    private String title;
    /** 类别（成功经验/失败教训/改进建议） */
    private String category;
    private String content;
    /** 影响程度（高/中/低） */
    private String impactLevel;
    private String keywords;
    private String createBy;
}
