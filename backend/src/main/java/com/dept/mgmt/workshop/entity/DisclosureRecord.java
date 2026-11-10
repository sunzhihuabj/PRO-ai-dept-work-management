package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 技术交底记录实体（T-201 W4，BR-09 数据源）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("disclosure_record")
public class DisclosureRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 项目ID */
    private Long projectId;

    /** 关联工单ID */
    private Long workorderId;

    /** 交底人 */
    private String discloser;

    /** 被交底人（逗号分隔） */
    private String receivers;

    /** 是否签字确认（0未签 1已签） */
    private Integer signed;

    /** 签字时间 */
    private LocalDateTime signTime;

    /** 交底内容 */
    private String content;
}
