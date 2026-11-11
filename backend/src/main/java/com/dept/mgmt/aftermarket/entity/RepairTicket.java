package com.dept.mgmt.aftermarket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 报修工单实体（T-304 W8 售后域，AGT-23 自动分级）
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repair_ticket")
public class RepairTicket extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工单编号（RP 编码引擎） */
    private String ticketNo;

    /** 项目ID */
    private Long projectId;

    /** 故障描述 */
    private String faultDesc;

    /** 报修时间 */
    private LocalDateTime faultTime;

    /** 分级（EMERGENCY紧急/GENERAL一般/INQUIRY咨询） */
    private String level;

    /** 响应时效 */
    private String responseSla;

    /** 状态（OPEN/PROCESSING/CLOSED） */
    private String status;

    /** 报修人 */
    private String reporter;

    /** 处理人 */
    private String handler;

    /** 根因（闭环必填） */
    private String rootCause;

    /** 解决方案（闭环必填） */
    private String solution;

    /** 闭环时间 */
    private LocalDateTime closedAt;
}
