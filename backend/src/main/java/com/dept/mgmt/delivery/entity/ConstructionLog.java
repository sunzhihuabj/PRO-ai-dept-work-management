package com.dept.mgmt.delivery.entity;

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
 * 施工日志实体（T-206 W6）
 * progress_pct 为当日累计完成工程量快照，项目当前工程量 = 历史最大值（BR-03/04 触发数据源）
 *
 * @author dept-mgmt
 */
@Data
@TableName("construction_log")
public class ConstructionLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 日志日期 */
    private LocalDate logDate;

    /** 天气 */
    private String weather;

    /** 在场人数 */
    private Integer personnelCount;

    /** 施工内容 */
    private String workContent;

    /** 累计完成工程量%（当日快照） */
    private BigDecimal progressPct;

    /** 当日问题 */
    private String issues;

    /** 记录人 */
    private String recorder;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
