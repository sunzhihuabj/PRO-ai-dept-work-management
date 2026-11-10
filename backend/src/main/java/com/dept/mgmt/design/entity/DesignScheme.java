package com.dept.mgmt.design.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设计方案版本实体（T-115 W3 设计域）
 * 受控发布：RELEASED 后不可修改（发布动作人工执行，AI 只起草草稿）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("design_scheme")
public class DesignScheme extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 关联合同ID */
    private Long contractId;

    /** 版本号（项目内递增） */
    private Integer versionNo;

    /** 方案标题 */
    private String title;

    /** 方案正文 */
    private String content;

    /** 生成方式（MANUAL人工/AI起草） */
    private String genType;

    /** 状态（DRAFT/RELEASED/ARCHIVED） */
    private String status;

    /** 发布说明 */
    private String releaseNote;

    /** 发布时间 */
    private LocalDateTime releaseTime;

    /** 发布人 */
    private String releasedBy;
}
