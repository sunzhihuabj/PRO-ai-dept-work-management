package com.dept.mgmt.aiworkspace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * SOUL.md 变更版本链实体（V2.0 M8）：每次采纳补丁前快照留痕，支持回滚。
 *
 * @author dept-mgmt
 */
@Data
@TableName("soul_change_log")
public class SoulChangeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 版本号 v1/v2/… */
    private String version;

    /** INIT 基线 / PATCH 补丁 / ROLLBACK 回滚 */
    private String changeType;

    /** 变更摘要 */
    private String diffSummary;

    /** 变更后全文快照 */
    private String contentSnapshot;

    /** 关联补丁建议 */
    private Long proposalId;

    private String operator;
    private LocalDateTime createTime;
}
