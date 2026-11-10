package com.dept.mgmt.board.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 红灯记录实体（T-209 M1 看板）
 * 状态机：OPEN 触发 → ASSIGNED 会议过堂 → ESCALATED 超期升级（redcard.escalated 事件）→ RESOLVED 消除
 *
 * @author dept-mgmt
 */
@Data
@TableName("redcard")
public class Redcard implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 红灯ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称（冗余） */
    private String ruleName;

    /** 所属板编码 */
    private String board;

    /** 项目ID（全局指标为空） */
    private Long projectId;

    /** 触发时指标值 */
    private BigDecimal metricValue;

    /** 级别（RED/YELLOW） */
    private String level;

    /** 状态（OPEN/ASSIGNED/ESCALATED/RESOLVED） */
    private String status;

    /** 责任人（会议过堂指定） */
    private String owner;

    /** 处理期限（会议过堂指定） */
    private LocalDate dueDate;

    /** 升级时间 */
    private LocalDateTime escalatedAt;

    /** 消除时间 */
    private LocalDateTime resolvedAt;

    /** 消除说明 */
    private String resolvedNote;

    /** 触发时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
