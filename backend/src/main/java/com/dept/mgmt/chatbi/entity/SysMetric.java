package com.dept.mgmt.chatbi.entity;

import com.dept.mgmt.entity.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ChatBI 指标白名单实体（T-307 AGT-25）
 * ChatBI 仅回答本表注册指标；sql_template 必须为受限 SELECT，执行前二次校验
 *
 * @author dept-mgmt
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysMetric extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 指标编码（唯一，如 po.overdue_count） */
    private String metricCode;

    /** 指标名称（与九板口径对齐） */
    private String metricName;

    /** 所属域（对齐九板编码 CONTRACT/KITTING/.../OVERVIEW） */
    private String category;

    /** 口径说明（回答时原样引用，禁止改写数字） */
    private String description;

    /** 别名（分号分隔，NL 确定性匹配用） */
    private String aliases;

    /** 受限 SELECT 模板（#{param} 占位，执行前校验） */
    private String sqlTemplate;

    /** 参数白名单 JSON [{"name":"projectId","type":"long","required":true,"desc":"..."}] */
    @JsonIgnore
    private String paramsSchema;

    /** 状态（ACTIVE启用/DISABLED停用） */
    private String status;
}
