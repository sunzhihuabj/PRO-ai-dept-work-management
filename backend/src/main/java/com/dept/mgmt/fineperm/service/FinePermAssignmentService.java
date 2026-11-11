package com.dept.mgmt.fineperm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.SysDept;
import com.dept.mgmt.fineperm.dto.PermAssignDTO;
import com.dept.mgmt.fineperm.dto.PermPointTreeVO;
import com.dept.mgmt.fineperm.entity.SysPermAssignment;
import com.dept.mgmt.fineperm.entity.SysPermPoint;
import com.dept.mgmt.fineperm.entity.SysPermChangeLog;
import com.dept.mgmt.fineperm.entity.SysPermTemplateItem;
import com.dept.mgmt.fineperm.entity.SysPosition;
import com.dept.mgmt.mapper.SysDeptMapper;
import com.dept.mgmt.mapper.SysPermAssignmentMapper;
import com.dept.mgmt.mapper.SysPermPointMapper;
import com.dept.mgmt.mapper.SysPermTemplateItemMapper;
import com.dept.mgmt.mapper.SysPositionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限分配服务（T-404）
 * 部门维度 / 职位维度的权限分配、批量授予、批量拒绝、批量移除、清空、模板应用
 * 每次变更：记录变更日志 + 递增权限版本号（实时生效）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinePermAssignmentService {

    private final SysPermAssignmentMapper assignmentMapper;
    private final SysPermPointMapper permPointMapper;
    private final SysPermTemplateItemMapper templateItemMapper;
    private final SysDeptMapper sysDeptMapper;
    private final SysPositionMapper sysPositionMapper;
    private final FinePermCacheService cacheService;
    private final FinePermChangeLogService changeLogService;

    /**
     * 批量分配（授予或拒绝）：逐条 upsert，同主体同权限点唯一
     */
    @Transactional(rollbackFor = Exception.class)
    public int assign(PermAssignDTO dto) {
        validateSubject(dto.getSubjectType(), dto.getSubjectId());
        validateEffect(dto.getEffect());

        // 校验权限点存在
        for (String code : dto.getPermCodes()) {
            Long count = permPointMapper.selectCount(new LambdaQueryWrapper<SysPermPoint>()
                    .eq(SysPermPoint::getPermCode, code));
            if (count == null || count == 0) {
                throw new BusinessException(400, "权限点不存在: " + code);
            }
        }

        // 变更前快照
        List<SysPermAssignment> before = listAssignments(dto.getSubjectType(), dto.getSubjectId());

        int affected = 0;
        for (String code : dto.getPermCodes()) {
            SysPermAssignment existing = findOne(dto.getSubjectType(), dto.getSubjectId(), code);
            if (existing != null) {
                existing.setEffect(dto.getEffect());
                assignmentMapper.updateById(existing);
            } else {
                SysPermAssignment assignment = new SysPermAssignment();
                assignment.setSubjectType(dto.getSubjectType());
                assignment.setSubjectId(dto.getSubjectId());
                assignment.setPermCode(code);
                assignment.setPermType(resolvePermType(code));
                assignment.setEffect(dto.getEffect());
                assignmentMapper.insert(assignment);
            }
            affected++;
        }

        // 变更日志 + 版本递增（实时生效）
        List<SysPermAssignment> after = listAssignments(dto.getSubjectType(), dto.getSubjectId());
        changeLogService.record(
                SysPermAssignment.SUBJECT_DEPT.equals(dto.getSubjectType())
                        ? SysPermChangeLog.SUBJECT_DEPT
                        : SysPermChangeLog.SUBJECT_POSITION,
                dto.getSubjectId(),
                subjectName(dto.getSubjectType(), dto.getSubjectId()),
                SysPermAssignment.SUBJECT_DEPT.equals(dto.getSubjectType()) ? "DEPT" : "POSITION",
                SysPermAssignment.EFFECT_GRANT.equals(dto.getEffect()) ? "GRANT" : "DENY",
                dto.getPermCodes(), snapshot(before), snapshot(after), dto.getRemark());
        cacheService.bump();

        log.info("权限分配完成 subject={}:{} effect={} 共{}项",
                dto.getSubjectType(), dto.getSubjectId(), dto.getEffect(), affected);
        return affected;
    }

    /**
     * 批量移除：物理删除分配记录（逻辑删除表中 deleted 标记，直接 update deleted=1 由 MP @TableLogic 处理）
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchRemove(String subjectType, Long subjectId, List<String> permCodes, String remark) {
        validateSubject(subjectType, subjectId);
        List<SysPermAssignment> before = listAssignments(subjectType, subjectId);

        int removed = 0;
        for (String code : permCodes) {
            SysPermAssignment existing = findOne(subjectType, subjectId, code);
            if (existing != null) {
                assignmentMapper.deleteById(existing.getId());
                removed++;
            }
        }

        List<SysPermAssignment> after = listAssignments(subjectType, subjectId);
        changeLogService.record(
                SysPermAssignment.SUBJECT_DEPT.equals(subjectType)
                        ? SysPermChangeLog.SUBJECT_DEPT
                        : SysPermChangeLog.SUBJECT_POSITION,
                subjectId, subjectName(subjectType, subjectId),
                SysPermAssignment.SUBJECT_DEPT.equals(subjectType) ? "DEPT" : "POSITION",
                "CLEAR", permCodes, snapshot(before), snapshot(after), remark);
        cacheService.bump();

        log.info("权限批量移除 subject={}:{} 移除{}项", subjectType, subjectId, removed);
        return removed;
    }

    /**
     * 清空主体全部分配
     */
    @Transactional(rollbackFor = Exception.class)
    public int clear(String subjectType, Long subjectId, String remark) {
        validateSubject(subjectType, subjectId);
        List<SysPermAssignment> before = listAssignments(subjectType, subjectId);
        List<String> codes = before.stream().map(SysPermAssignment::getPermCode).collect(Collectors.toList());
        if (codes.isEmpty()) {
            return 0;
        }
        assignmentMapper.delete(new LambdaQueryWrapper<SysPermAssignment>()
                .eq(SysPermAssignment::getSubjectType, subjectType)
                .eq(SysPermAssignment::getSubjectId, subjectId));

        List<SysPermAssignment> after = listAssignments(subjectType, subjectId);
        changeLogService.record(
                SysPermAssignment.SUBJECT_DEPT.equals(subjectType)
                        ? SysPermChangeLog.SUBJECT_DEPT
                        : SysPermChangeLog.SUBJECT_POSITION,
                subjectId, subjectName(subjectType, subjectId),
                SysPermAssignment.SUBJECT_DEPT.equals(subjectType) ? "DEPT" : "POSITION",
                "CLEAR", codes, snapshot(before), snapshot(after), remark);
        cacheService.bump();
        return codes.size();
    }

    /**
     * 应用权限模板到主体（模板条目逐条 upsert，GRANT/DENY 均支持）
     */
    @Transactional(rollbackFor = Exception.class)
    public int applyTemplate(String subjectType, Long subjectId, Long templateId, String remark) {
        validateSubject(subjectType, subjectId);
        List<Map<String, String>> items = templateItems(templateId);
        if (items.isEmpty()) {
            throw new BusinessException(400, "模板条目为空，无法应用");
        }

        List<SysPermAssignment> before = listAssignments(subjectType, subjectId);
        List<String> codes = new ArrayList<>();
        for (Map<String, String> item : items) {
            String code = item.get("permCode");
            String effect = item.getOrDefault("effect", SysPermAssignment.EFFECT_GRANT);
            SysPermAssignment existing = findOne(subjectType, subjectId, code);
            if (existing != null) {
                existing.setEffect(effect);
                assignmentMapper.updateById(existing);
            } else {
                SysPermAssignment assignment = new SysPermAssignment();
                assignment.setSubjectType(subjectType);
                assignment.setSubjectId(subjectId);
                assignment.setPermCode(code);
                assignment.setPermType(resolvePermType(code));
                assignment.setEffect(effect);
                assignmentMapper.insert(assignment);
            }
            codes.add(code);
        }

        List<SysPermAssignment> after = listAssignments(subjectType, subjectId);
        changeLogService.record(
                SysPermAssignment.SUBJECT_DEPT.equals(subjectType)
                        ? SysPermChangeLog.SUBJECT_DEPT
                        : SysPermChangeLog.SUBJECT_POSITION,
                subjectId, subjectName(subjectType, subjectId),
                SysPermAssignment.SUBJECT_DEPT.equals(subjectType) ? "DEPT" : "POSITION",
                "APPLY_TEMPLATE", codes, snapshot(before), snapshot(after),
                (remark == null ? "" : remark + " ") + "应用模板#" + templateId);
        cacheService.bump();
        return codes.size();
    }

    /** 模板条目读取（permCode/effect 映射列表） */
    private List<Map<String, String>> templateItems(Long templateId) {
        return templateItemMapper.selectList(new LambdaQueryWrapper<SysPermTemplateItem>()
                        .eq(SysPermTemplateItem::getTemplateId, templateId)
                        .orderByAsc(SysPermTemplateItem::getSortOrder))
                .stream()
                .<Map<String, String>>map(item -> Map.of(
                        "permCode", item.getPermCode(),
                        "effect", item.getEffect() == null ? SysPermAssignment.EFFECT_GRANT : item.getEffect()))
                .collect(Collectors.toList());
    }

    private String resolvePermType(String permCode) {
        if (permCode.startsWith("api:")) {
            return SysPermPoint.TYPE_API;
        }
        if (permCode.startsWith("btn:")) {
            return SysPermPoint.TYPE_BUTTON;
        }
        return SysPermPoint.TYPE_MENU;
    }

    private SysPermAssignment findOne(String subjectType, Long subjectId, String permCode) {
        return assignmentMapper.selectOne(new LambdaQueryWrapper<SysPermAssignment>()
                .eq(SysPermAssignment::getSubjectType, subjectType)
                .eq(SysPermAssignment::getSubjectId, subjectId)
                .eq(SysPermAssignment::getPermCode, permCode)
                .last("LIMIT 1"));
    }

    public List<SysPermAssignment> listAssignments(String subjectType, Long subjectId) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<SysPermAssignment>()
                .eq(SysPermAssignment::getSubjectType, subjectType)
                .eq(SysPermAssignment::getSubjectId, subjectId)
                .orderByAsc(SysPermAssignment::getPermCode));
    }

    private void validateSubject(String subjectType, Long subjectId) {
        if (SysPermAssignment.SUBJECT_DEPT.equals(subjectType)) {
            if (sysDeptMapper.selectById(subjectId) == null) {
                throw new BusinessException(404, "部门不存在: " + subjectId);
            }
        } else if (SysPermAssignment.SUBJECT_POSITION.equals(subjectType)) {
            if (sysPositionMapper.selectById(subjectId) == null) {
                throw new BusinessException(404, "职位不存在: " + subjectId);
            }
        } else {
            throw new BusinessException(400, "主体类型非法: " + subjectType);
        }
    }

    private void validateEffect(String effect) {
        if (!SysPermAssignment.EFFECT_GRANT.equals(effect) && !SysPermAssignment.EFFECT_DENY.equals(effect)) {
            throw new BusinessException(400, "效果非法: " + effect);
        }
    }

    private String subjectName(String subjectType, Long subjectId) {
        if (SysPermAssignment.SUBJECT_DEPT.equals(subjectType)) {
            SysDept dept = sysDeptMapper.selectById(subjectId);
            return dept == null ? null : dept.getDeptName();
        }
        SysPosition position = sysPositionMapper.selectById(subjectId);
        return position == null ? null : position.getPositionName();
    }

    /** 分配快照（perm_code:effect 列表，写日志） */
    private List<String> snapshot(List<SysPermAssignment> assignments) {
        return assignments.stream()
                .map(a -> a.getPermCode() + ":" + a.getEffect())
                .collect(Collectors.toList());
    }

    /** 权限点树（MENU -> BUTTON/API） */
    public List<PermPointTreeVO> pointTree() {
        List<SysPermPoint> all = permPointMapper.selectList(new LambdaQueryWrapper<SysPermPoint>()
                .eq(SysPermPoint::getStatus, 1)
                .orderByAsc(SysPermPoint::getSortOrder)
                .orderByAsc(SysPermPoint::getId));
        Map<String, List<SysPermPoint>> byParent = all.stream()
                .filter(p -> p.getParentCode() != null)
                .collect(Collectors.groupingBy(SysPermPoint::getParentCode));
        List<PermPointTreeVO> tree = new ArrayList<>();
        for (SysPermPoint menu : all) {
            if (!SysPermPoint.TYPE_MENU.equals(menu.getPermType())) {
                continue;
            }
            PermPointTreeVO node = new PermPointTreeVO();
            node.setPermCode(menu.getPermCode());
            node.setPermName(menu.getPermName());
            node.setPermType(menu.getPermType());
            for (SysPermPoint child : byParent.getOrDefault(menu.getPermCode(), List.of())) {
                PermPointTreeVO childNode = new PermPointTreeVO();
                childNode.setPermCode(child.getPermCode());
                childNode.setPermName(child.getPermName());
                childNode.setPermType(child.getPermType());
                node.getChildren().add(childNode);
            }
            tree.add(node);
        }
        return tree;
    }
}
