package com.dept.mgmt.fineperm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.entity.SysDept;
import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.fineperm.dto.FinePermPreviewVO;
import com.dept.mgmt.fineperm.entity.SysPermAssignment;
import com.dept.mgmt.fineperm.entity.SysPosition;
import com.dept.mgmt.fineperm.entity.SysUserPosition;
import com.dept.mgmt.mapper.SysDeptMapper;
import com.dept.mgmt.mapper.SysPermAssignmentMapper;
import com.dept.mgmt.mapper.SysPositionMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import com.dept.mgmt.mapper.SysUserPositionMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 精细权限合并引擎（T-404 核心）
 *
 * 合并规则：
 * 1. 三维度权限合并叠加：部门维度 + 固定职位维度 + 临时职位维度（有效期内的）
 * 2. 冲突处理：同一权限点效果不一致时，临时职位 > 固定职位 > 部门维度（高优先级维度的
 *    effect 覆盖低优先级维度）
 * 3. 同维度内多条记录冲突（如两个固定职位对同一权限点效果不同）：DENY 优先（安全默认）
 * 4. 灰度原则：用户三维度均无任何分配记录时 finePermEnabled=false，不启用精细控制
 *    （行为与纯角色体系一致，存量用户零影响）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinePermissionService {

    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysPositionMapper sysPositionMapper;
    private final SysUserPositionMapper sysUserPositionMapper;
    private final SysPermAssignmentMapper sysPermAssignmentMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 维度优先级：TEMP(3) > FIXED(2) > DEPT(1) */
    private static final int PRIO_TEMP = 3;
    private static final int PRIO_FIXED = 2;
    private static final int PRIO_DEPT = 1;

    /**
     * 合并解析结果
     */
    @Data
    public static class Resolution {

        /** 是否启用精细控制（false=三维度均未配置） */
        private boolean finePermEnabled;

        /** 各维度来源明细（含原始分配） */
        private List<DimensionSource> sources = new ArrayList<>();

        /** 冲突明细（permCode -> 描述 -> 最终效果） */
        private List<FinePermPreviewVO.ConflictEntry> conflicts = new ArrayList<>();

        /** 最终生效权限点集合（仅 GRANT） */
        private Set<String> granted = new LinkedHashSet<>();

        /** 用户角色编码（供上层合并 authorities，向后兼容角色校验） */
        private List<String> roles = new ArrayList<>();
    }

    /**
     * 维度来源（内部结构）
     */
    @Data
    public static class DimensionSource {

        /** 维度（DEPT/FIXED/TEMP） */
        private String dimension;

        /** 来源名称 */
        private String sourceName;

        /** 来源ID */
        private Long sourceId;

        /** 优先级 */
        private int priority;

        /** 原始分配记录 */
        private List<SysPermAssignment> assignments = new ArrayList<>();

        /** 临时职位有效期（TEMP 专用，展示用） */
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
    }

    /**
     * 合并解析指定用户的生效权限（每次调用实时计算，缓存层见 FinePermCacheService）
     */
    public Resolution resolve(Long userId) {
        Resolution resolution = new Resolution();

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new com.dept.mgmt.common.exception.BusinessException(404, "用户不存在: " + userId);
        }
        resolution.setRoles(parseRoleCodes(user.getRoleCodes()));

        LocalDateTime now = LocalDateTime.now();

        // ---------- 1. 部门维度 ----------
        if (user.getDeptId() != null) {
            SysDept dept = sysDeptMapper.selectById(user.getDeptId());
            if (dept != null && dept.getStatus() != null && dept.getStatus() == 1) {
                List<SysPermAssignment> perms = listAssignments(SysPermAssignment.SUBJECT_DEPT, dept.getId());
                if (!perms.isEmpty()) {
                    DimensionSource src = new DimensionSource();
                    src.setDimension("DEPT");
                    src.setSourceName(dept.getDeptName());
                    src.setSourceId(dept.getId());
                    src.setPriority(PRIO_DEPT);
                    src.setAssignments(perms);
                    resolution.getSources().add(src);
                }
            }
        }

        // ---------- 2. 职位维度（固定 + 临时，临时需在有效期内） ----------
        List<SysUserPosition> userPositions = sysUserPositionMapper.selectList(
                new LambdaQueryWrapper<SysUserPosition>().eq(SysUserPosition::getUserId, userId));
        for (SysUserPosition up : userPositions) {
            SysPosition position = sysPositionMapper.selectById(up.getPositionId());
            if (position == null || position.getStatus() == null || position.getStatus() != 1) {
                continue;
            }
            boolean isTemp = SysUserPosition.KIND_TEMP.equals(up.getPositionKind());
            if (isTemp) {
                // 临时职位有效期校验：缺失或不在窗口内一律失效
                if (up.getStartTime() == null || up.getEndTime() == null
                        || now.isBefore(up.getStartTime()) || now.isAfter(up.getEndTime())) {
                    continue;
                }
            }
            List<SysPermAssignment> perms = listAssignments(SysPermAssignment.SUBJECT_POSITION, position.getId());
            if (perms.isEmpty()) {
                continue;
            }
            DimensionSource src = new DimensionSource();
            src.setDimension(isTemp ? "TEMP" : "FIXED");
            src.setSourceName(position.getPositionName());
            src.setSourceId(position.getId());
            src.setPriority(isTemp ? PRIO_TEMP : PRIO_FIXED);
            src.setAssignments(perms);
            if (isTemp) {
                src.setValidFrom(up.getStartTime());
                src.setValidTo(up.getEndTime());
            }
            resolution.getSources().add(src);
        }

        // ---------- 3. 灰度判断：无任何维度分配 → 不启用精细控制 ----------
        if (resolution.getSources().isEmpty()) {
            resolution.setFinePermEnabled(false);
            return resolution;
        }
        resolution.setFinePermEnabled(true);

        // ---------- 4. 冲突合并 ----------
        // code -> (dimension -> effect)；同维度多条冲突 DENY 优先（安全默认）
        Map<String, Map<String, String>> buckets = new LinkedHashMap<>();
        Map<String, Set<String>> sameDimConflict = new LinkedHashMap<>();
        for (DimensionSource src : resolution.getSources()) {
            for (SysPermAssignment a : src.getAssignments()) {
                Map<String, String> bucket = buckets.computeIfAbsent(a.getPermCode(), k -> new LinkedHashMap<>());
                String existed = bucket.get(src.getDimension());
                if (existed != null && !existed.equals(a.getEffect())) {
                    // 同维度内效果冲突 → DENY 优先
                    bucket.put(src.getDimension(), SysPermAssignment.EFFECT_DENY);
                    sameDimConflict.computeIfAbsent(a.getPermCode(), k -> new LinkedHashSet<>())
                            .add(src.getDimension());
                } else {
                    bucket.put(src.getDimension(), a.getEffect());
                }
            }
        }

        for (Map.Entry<String, Map<String, String>> entry : buckets.entrySet()) {
            String code = entry.getKey();
            Map<String, String> bucket = entry.getValue();

            // 按优先级取最终效果：TEMP > FIXED > DEPT
            String finalEffect = bucket.containsKey("TEMP") ? bucket.get("TEMP")
                    : bucket.containsKey("FIXED") ? bucket.get("FIXED")
                    : bucket.get("DEPT");

            // 跨维度效果不一致 → 冲突明细
            Set<String> distinctEffects = new LinkedHashSet<>(bucket.values());
            if (distinctEffects.size() > 1 || sameDimConflict.containsKey(code)) {
                FinePermPreviewVO.ConflictEntry conflict = new FinePermPreviewVO.ConflictEntry();
                conflict.setPermCode(code);
                conflict.setDetail(buildConflictDetail(bucket, sameDimConflict.get(code), finalEffect));
                conflict.setFinalEffect(finalEffect);
                resolution.getConflicts().add(conflict);
            }

            if (SysPermAssignment.EFFECT_GRANT.equals(finalEffect)) {
                resolution.getGranted().add(code);
            }
        }

        return resolution;
    }

    /**
     * 判断用户是否持有某权限点（走缓存的轻量判断见 FinePermCacheService）
     */
    public boolean hasPerm(Long userId, String permCode) {
        return resolve(userId).getGranted().contains(permCode);
    }

    /**
     * 构建 ConflictEntry 明细：列出各维度效果与最终裁决
     */
    private String buildConflictDetail(Map<String, String> bucket, Set<String> sameDimDims, String finalEffect) {
        StringBuilder sb = new StringBuilder();
        appendDim(sb, "TEMP", bucket.get("TEMP"), sameDimDims);
        appendDim(sb, "FIXED", bucket.get("FIXED"), sameDimDims);
        appendDim(sb, "DEPT", bucket.get("DEPT"), sameDimDims);
        sb.append("→ 按优先级(临时>固定>部门)裁决为 ").append(finalEffect);
        return sb.toString();
    }

    private void appendDim(StringBuilder sb, String dim, String effect, Set<String> sameDimDims) {
        if (effect == null) {
            return;
        }
        sb.append(dim);
        if (sameDimDims != null && sameDimDims.contains(dim)) {
            sb.append("(同维度冲突,取DENY)");
        }
        sb.append("=").append(effect).append(" ");
    }

    /**
     * 查询某主体的分配记录
     */
    public List<SysPermAssignment> listAssignments(String subjectType, Long subjectId) {
        return sysPermAssignmentMapper.selectList(new LambdaQueryWrapper<SysPermAssignment>()
                .eq(SysPermAssignment::getSubjectType, subjectType)
                .eq(SysPermAssignment::getSubjectId, subjectId)
                .orderByAsc(SysPermAssignment::getPermCode));
    }

    /**
     * 组装预览 VO（供预览接口）
     */
    public FinePermPreviewVO preview(Long userId) {
        Resolution r = resolve(userId);

        FinePermPreviewVO vo = new FinePermPreviewVO();
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            vo.setUserId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setRealName(user.getRealName());
            if (user.getDeptId() != null) {
                SysDept dept = sysDeptMapper.selectById(user.getDeptId());
                if (dept != null) {
                    vo.setDeptName(dept.getDeptName());
                }
            }
        }
        vo.setFinePermEnabled(r.isFinePermEnabled());
        vo.getRoles().addAll(r.getRoles());
        vo.getGrantedPerms().addAll(r.getGranted());
        r.getGranted().stream()
                .filter(c -> c.startsWith("/") || c.startsWith("menu:"))
                .forEach(vo.getGrantedMenus()::add);
        vo.getConflicts().addAll(r.getConflicts());

        for (DimensionSource src : r.getSources()) {
            FinePermPreviewVO.DimensionEntry entry = new FinePermPreviewVO.DimensionEntry();
            entry.setDimension(src.getDimension());
            entry.setSourceName(src.getSourceName());
            entry.setSourceId(src.getSourceId());
            for (SysPermAssignment a : src.getAssignments()) {
                entry.getPerms().add(a.getPermCode() + ":" + a.getEffect());
            }
            if (src.getValidFrom() != null) {
                entry.setSourceName(src.getSourceName()
                        + "（" + src.getValidFrom().toLocalDate() + " ~ " + src.getValidTo().toLocalDate() + "）");
            }
            vo.getDimensions().add(entry);
        }
        return vo;
    }

    /**
     * 解析 roleCodes JSON 数组（与 AuthService.parseRoleCodes 同口径）
     */
    public List<String> parseRoleCodes(String roleCodesJson) {
        List<String> roles = new ArrayList<>();
        if (roleCodesJson == null || roleCodesJson.isBlank()) {
            return roles;
        }
        try {
            roles = objectMapper.readValue(roleCodesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            log.warn("roleCodes 解析失败: {}", roleCodesJson);
        }
        return roles;
    }
}
