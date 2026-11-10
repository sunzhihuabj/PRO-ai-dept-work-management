package com.dept.mgmt.platform.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 权限判定结果（含判定链路，审计与调试友好，不用裸布尔）
 *
 * @author dept-mgmt
 */
@Data
@AllArgsConstructor
public class PermissionCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否允许 */
    private boolean allowed;

    /** 拒绝原因（allowed=true 时为 "OK"） */
    private String reason;

    /** 判定链路（每步检查项及结果，供审计日志/工作台展示） */
    private List<String> chain;

    public static PermissionCheckResult allow(List<String> chain) {
        return new PermissionCheckResult(true, "OK", chain);
    }

    public static PermissionCheckResult deny(String reason, List<String> chain) {
        return new PermissionCheckResult(false, reason, chain);
    }
}
