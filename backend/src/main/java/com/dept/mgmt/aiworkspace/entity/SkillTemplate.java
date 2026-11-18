package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务书模板库实体（V2.0 M8 Skill 沉淀）：高频成功任务书自动模板化，带使用统计与成功率。
 *
 * @author dept-mgmt
 */
@Data
@TableName("skill_template")
public class SkillTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateName;
    private String templateContent;
    private Integer useCount;
    private Integer successCount;
    private java.math.BigDecimal successRate;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createTime;
}
