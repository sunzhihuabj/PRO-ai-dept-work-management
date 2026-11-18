package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 黄金指令回归用例实体（V2.0 M8/V2.0 验收）：25 条路由断言 + 5 条越权断言。
 *
 * @author dept-mgmt
 */
@Data
@TableName("hermes_regression_suite")
public class HermesRegressionCase implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用例号 G01…P05 */
    private String caseNo;

    /** ROUTE 路由断言 / PERM 越权断言 */
    private String caseKind;

    /** WEBSEARCH / ADVISOR / WORKFLOW / OPS / DELEGATE / PERM */
    private String scene;

    /** 黄金指令原文（PERM 类为目标端点路径） */
    private String instruction;

    /** 期望路由（ROUTE 类） */
    private String expectedRoute;

    /** 越权请求方法（PERM 类） */
    private String permMethod;

    /** 越权期望 403 */
    private Integer expectDenied;

    private Integer enabled;

    /** 最近回归结果 PASS/FAIL */
    private String lastResult;
    private LocalDateTime lastRunAt;
}
