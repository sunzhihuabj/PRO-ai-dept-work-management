package com.dept.mgmt.agent.collab.mcp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MCP 工具调用审计（V2.0 铁律五：append-only，禁更新/删除）
 *
 * @author dept-mgmt
 */
@Data
@TableName("hermes_tool_audit")
public class HermesToolAudit {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Hermes run ID（非委托调用为空） */
    private String runId;

    /** MCP 工具名 */
    private String toolName;

    /** 工具类型：readonly / write（写=审批化） */
    private String toolKind;

    /** 生效调用身份（委托人/透传用户） */
    private Long userId;

    /** 身份来源：ARGS / HEADER */
    private String identitySrc;

    /** 结果：SUCCESS / DENIED / FAILED / INVALID */
    private String outcome;

    /** 失败/拒绝简要（截断 300） */
    private String errorBrief;

    /** 参数 SHA-256 前 16 位（不存原文） */
    private String paramsDigest;

    /** 执行耗时毫秒 */
    private Long costMs;

    /** 链式哈希：上一条记录 digest（防篡改链） */
    private String chainPrev;

    private LocalDateTime createTime;
}
