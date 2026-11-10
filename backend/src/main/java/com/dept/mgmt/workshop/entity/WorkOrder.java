package com.dept.mgmt.workshop.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 生产工单实体（T-201 W4，BR-01/08/09 开工卡点载体）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_order")
public class WorkOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工单编号 */
    private String woNo;

    /** 项目ID */
    private Long projectId;

    /** 类型（PROTOTYPE样机/BATCH批量） */
    private String orderType;

    /** 台数 */
    private Integer qty;

    /** 状态（DRAFT/STARTED/CLOSED） */
    private String status;

    /** 开工时间 */
    private LocalDateTime startedAt;

    /** 完工时间 */
    private LocalDateTime closedAt;

    /** 备注 */
    private String remark;
}
