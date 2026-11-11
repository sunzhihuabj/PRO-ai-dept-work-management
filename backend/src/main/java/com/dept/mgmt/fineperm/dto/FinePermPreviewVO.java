package com.dept.mgmt.fineperm.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限预览结果 VO（T-404）
 * 模拟查看指定用户的实际生效权限集合：分维度明细 + 冲突明细 + 最终合并结果
 *
 * @author dept-mgmt
 */
@Data
public class FinePermPreviewVO {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 所属部门名称 */
    private String deptName;

    /** 是否启用精细控制（false=三维度均未配置，行为与纯角色一致） */
    private boolean finePermEnabled;

    /** 权限版本号 */
    private long version;

    /** 各维度来源明细 */
    private List<DimensionEntry> dimensions = new ArrayList<>();

    /** 冲突明细（同一权限点在不同维度/不同来源效果不一致） */
    private List<ConflictEntry> conflicts = new ArrayList<>();

    /** 最终生效权限点集合（合并结果，仅 GRANT） */
    private List<String> grantedPerms = new ArrayList<>();

    /** 生效的 MENU 权限点（前端菜单过滤用） */
    private List<String> grantedMenus = new ArrayList<>();

    /** 角色编码（JWT authorities 保留角色，向后兼容现有 hasAuthority 校验） */
    private List<String> roles = new ArrayList<>();

    /**
     * 维度来源明细
     */
    @Data
    public static class DimensionEntry {

        /** 维度（DEPT部门 FIXED固定职位 TEMP临时职位） */
        private String dimension;

        /** 来源名称（部门名/职位名） */
        private String sourceName;

        /** 来源ID（部门ID/职位ID） */
        private Long sourceId;

        /** 该来源下分配的权限点（perm_code:effect） */
        private List<String> perms = new ArrayList<>();
    }

    /**
     * 冲突明细
     */
    @Data
    public static class ConflictEntry {

        /** 权限点编码 */
        private String permCode;

        /** 冲突描述（如 "部门维度=DENY, 临时职位=GRANT → 按 临时职位 生效 GRANT"） */
        private String detail;

        /** 最终生效效果 */
        private String finalEffect;
    }
}
