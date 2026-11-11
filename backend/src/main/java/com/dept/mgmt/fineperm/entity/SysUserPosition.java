package com.dept.mgmt.fineperm.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dept.mgmt.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户职位关联实体（T-404 精细化权限）
 * FIXED 固定职位（无有效期）；TEMP 临时职位必须配置 start/end，到期自动失效
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_position")
public class SysUserPosition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    public static final String KIND_FIXED = "FIXED";
    public static final String KIND_TEMP = "TEMP";

    /** 用户ID */
    private Long userId;

    /** 职位ID */
    private Long positionId;

    /** 职位性质（FIXED固定 TEMP临时） */
    private String positionKind;

    /** 生效开始时间（TEMP 必填） */
    private LocalDateTime startTime;

    /** 生效结束时间（TEMP 必填，到期自动失效） */
    private LocalDateTime endTime;
}
