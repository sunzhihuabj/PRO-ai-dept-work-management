package com.dept.mgmt.platform.code.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 编码序号实体（纯序号表，无审计字段，独立于 BaseEntity）
 * (rule_code, date_part) 唯一，current_value 原子自增
 *
 * @author dept-mgmt
 */
@Data
@TableName("sys_code_seq")
public class CodeSeq implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码 */
    private String ruleCode;

    /** 日期段值（如 2026/202611），无日期规则固定空串 */
    private String datePart;

    /** 当前流水值 */
    private Long currentValue;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
