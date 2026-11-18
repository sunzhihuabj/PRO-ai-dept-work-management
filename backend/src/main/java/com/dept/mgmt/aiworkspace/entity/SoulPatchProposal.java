package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SOUL 补丁建议实体（V2.0 M8 迭代管线）：每周低分归因 → 建议 → 人工采纳 → SOUL.md 版本链。
 *
 * @author dept-mgmt
 */
@Data
@TableName("soul_patch_proposal")
public class SoulPatchProposal implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计周期（ISO 周如 2026-W47） */
    private String period;

    /** SOUL_PATCH 口径补丁 / TOOL_REQUEST 工具需求 / SKILL_TEMPLATE 模板沉淀 */
    private String category;

    /** TASK_UNCLEAR / TOOL_MISSING / KNOWLEDGE_GAP / OTHER */
    private String rootCause;

    /** 问题摘要 */
    private String summary;

    /** 改进建议（采纳后追加进 SOUL.md 的补丁段文本） */
    private String suggestion;

    /** 证据（低分 run/失败任务清单 JSON） */
    private String evidenceJson;

    /** PROPOSED / ADOPTED / REJECTED */
    private String status;

    private String adoptNote;
    private String soulVersion;
    private LocalDateTime adoptedAt;
    private LocalDateTime createTime;
}
