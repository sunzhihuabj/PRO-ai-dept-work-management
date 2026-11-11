package com.dept.mgmt.fineperm;

import com.dept.mgmt.entity.SysUser;
import com.dept.mgmt.fineperm.dto.PermAssignDTO;
import com.dept.mgmt.fineperm.dto.UserPositionDTO;
import com.dept.mgmt.fineperm.entity.SysPermChangeLog;
import com.dept.mgmt.fineperm.entity.SysPosition;
import com.dept.mgmt.fineperm.entity.SysUserPosition;
import com.dept.mgmt.fineperm.service.FinePermAssignmentService;
import com.dept.mgmt.fineperm.service.FinePermCacheService;
import com.dept.mgmt.fineperm.service.FinePermChangeLogService;
import com.dept.mgmt.fineperm.service.FinePermPositionService;
import com.dept.mgmt.fineperm.service.FinePermissionService;
import com.dept.mgmt.mapper.SysPermChangeLogMapper;
import com.dept.mgmt.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 精细权限合并引擎测试（T-404，依赖本地 MySQL 3307 dept_mgmt 库）
 * 覆盖：多维度叠加 / 三维度冲突优先级 / 同维度冲突 DENY 优先 / 临时有效期 /
 *       批量操作 / 模板复用 / 变更日志 / 版本实时生效
 *
 * @author dept-mgmt
 */
@SpringBootTest
@Transactional
class FinePermissionServiceTest {

    private static final String P_LIST = "btn:system/user:list";
    private static final String P_CREATE = "btn:system/user:create";
    private static final String P_DELETE = "btn:system/user:delete";
    private static final String P_MENU = "/system/user";

    @Autowired
    private FinePermissionService finePermissionService;
    @Autowired
    private FinePermAssignmentService assignmentService;
    @Autowired
    private FinePermPositionService positionService;
    @Autowired
    private FinePermCacheService cacheService;
    @Autowired
    private FinePermChangeLogService changeLogService;
    @Autowired
    private SysPermChangeLogMapper changeLogMapper;
    @Autowired
    private SysUserMapper sysUserMapper;

    // ==================== 灰度与单维度 ====================

    @Test
    void noAssignment_finePermDisabled() {
        Long userId = createTestUser(null);
        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertFalse(r.isFinePermEnabled(), "无任何维度分配应不启用精细控制");
        assertTrue(r.getGranted().isEmpty(), "未启用时生效集合为空");
    }

    @Test
    void deptGrant_only_effective() {
        Long userId = createTestUser(createDept());
        Long deptId = sysUserMapper.selectById(userId).getDeptId();
        assign("DEPT", deptId, P_LIST, "GRANT");

        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertTrue(r.isFinePermEnabled());
        assertTrue(r.getGranted().contains(P_LIST), "部门维度 GRANT 应生效");
        assertFalse(r.getGranted().contains(P_CREATE), "未分配的权限点不应生效");
    }

    @Test
    void menuTypeGrant_effective() {
        Long userId = createTestUser(createDept());
        Long deptId = sysUserMapper.selectById(userId).getDeptId();
        assign("DEPT", deptId, P_MENU, "GRANT");
        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertTrue(r.getGranted().contains(P_MENU), "MENU 级权限点应生效");
    }

    // ==================== 三维度冲突优先级 ====================

    @Test
    void tempGrant_overridesDeptDeny() {
        Long userId = createTestUser(createDept());
        Long deptId = sysUserMapper.selectById(userId).getDeptId();
        Long posId = createPosition("T-TEST-TEMP-G");
        assignTempPosition(userId, posId, now().minusDays(1), now().plusDays(1));

        assign("DEPT", deptId, P_CREATE, "DENY");
        assign("POSITION", posId, P_CREATE, "GRANT");

        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertTrue(r.getGranted().contains(P_CREATE),
                "临时职位 GRANT 应覆盖部门维度 DENY: conflicts=" + r.getConflicts());
        assertFalse(r.getConflicts().isEmpty(), "跨维度冲突应产生冲突明细");
    }

    @Test
    void tempDeny_overridesFixedGrant() {
        Long userId = createTestUser(createDept());
        Long fixedId = createPosition("T-TEST-FIX-1");
        Long tempId = createPosition("T-TEST-TEMP-1");
        assignFixedPosition(userId, fixedId);
        assignTempPosition(userId, tempId, now().minusDays(1), now().plusDays(1));

        assign("POSITION", fixedId, P_DELETE, "GRANT");
        assign("POSITION", tempId, P_DELETE, "DENY");

        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertFalse(r.getGranted().contains(P_DELETE),
                "临时职位 DENY 优先级最高应覆盖固定职位 GRANT");
    }

    @Test
    void fixedGrant_overridesDeptDeny() {
        Long userId = createTestUser(createDept());
        Long deptId = sysUserMapper.selectById(userId).getDeptId();
        Long fixedId = createPosition("T-TEST-FIX-2");
        assignFixedPosition(userId, fixedId);

        assign("DEPT", deptId, P_CREATE, "DENY");
        assign("POSITION", fixedId, P_CREATE, "GRANT");

        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertTrue(r.getGranted().contains(P_CREATE), "固定职位 GRANT 应覆盖部门 DENY");
    }

    @Test
    void sameDimensionConflict_denyWins() {
        Long userId = createTestUser(createDept());
        Long posA = createPosition("T-TEST-DIM-A");
        Long posB = createPosition("T-TEST-DIM-B");
        assignFixedPosition(userId, posA);
        assignFixedPosition(userId, posB);

        assign("POSITION", posA, P_DELETE, "GRANT");
        assign("POSITION", posB, P_DELETE, "DENY");

        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertFalse(r.getGranted().contains(P_DELETE), "同维度冲突应 DENY 优先（安全默认）");
        assertFalse(r.getConflicts().isEmpty(), "同维度冲突应记入冲突明细");
    }

    // ==================== 临时职位有效期 ====================

    @Test
    void tempPosition_expired_notEffective() {
        Long userId = createTestUser(createDept());
        Long deptId = sysUserMapper.selectById(userId).getDeptId();
        Long tempId = createPosition("T-TEST-TEMP-EXP");
        // 已过期：结束时间为昨天
        assignTempPosition(userId, tempId, now().minusDays(10), now().minusDays(1));

        assign("DEPT", deptId, P_CREATE, "DENY");
        assign("POSITION", tempId, P_CREATE, "GRANT");

        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertFalse(r.getGranted().contains(P_CREATE), "过期临时职位的 GRANT 不应生效");
        assertTrue(r.getGranted().isEmpty() || !r.getGranted().contains(P_CREATE),
                "过期后仅剩部门维度 DENY，最终不生效");
    }

    @Test
    void tempPosition_notStarted_notEffective() {
        Long userId = createTestUser(createDept());
        Long tempId = createPosition("T-TEST-TEMP-FUT");
        // 未开始：起始时间为明天
        assignTempPosition(userId, tempId, now().plusDays(1), now().plusDays(10));
        assign("POSITION", tempId, P_CREATE, "GRANT");

        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertFalse(r.getGranted().contains(P_CREATE), "未到生效期的临时职位不应生效");
    }

    // ==================== 批量操作与模板 ====================

    @Test
    void batchAssign_and_batchRemove() {
        Long userId = createTestUser(createDept());
        Long deptId = sysUserMapper.selectById(userId).getDeptId();

        // 批量授予
        PermAssignDTO grantDto = dto("DEPT", deptId, "GRANT", List.of(P_LIST, P_CREATE, P_DELETE));
        int granted = assignmentService.assign(grantDto);
        assertEquals(3, granted, "批量授予3项");

        FinePermissionService.Resolution r1 = finePermissionService.resolve(userId);
        assertTrue(r1.getGranted().containsAll(List.of(P_LIST, P_CREATE, P_DELETE)), "批量授予应全部生效");

        // 批量移除
        PermAssignDTO removeDto = dto("DEPT", deptId, "GRANT", List.of(P_CREATE, P_DELETE));
        int removed = assignmentService.batchRemove("DEPT", deptId, removeDto.getPermCodes(), "测试移除");
        assertEquals(2, removed, "批量移除2项");

        FinePermissionService.Resolution r2 = finePermissionService.resolve(userId);
        assertTrue(r2.getGranted().contains(P_LIST), "未移除的应保留");
        assertFalse(r2.getGranted().contains(P_CREATE), "已移除的不应生效");
    }

    @Test
    void applyTemplate_grantsTemplatePerms() {
        Long userId = createTestUser(createDept());
        Long posId = createPosition("T-TEST-TPL-1");
        assignFixedPosition(userId, posId);

        // 使用种子模板 TPL_SYS_READONLY（MENU /system/user + btn list + api list）
        Long templateId = findTemplateId("TPL_SYS_READONLY");
        assertTrue(templateId != null && templateId > 0, "种子模板应存在");

        int applied = assignmentService.applyTemplate("POSITION", posId, templateId, "模板应用测试");
        assertTrue(applied >= 3, "模板应应用至少3条: " + applied);

        FinePermissionService.Resolution r = finePermissionService.resolve(userId);
        assertTrue(r.getGranted().contains("/system/user"), "模板 MENU 点应生效");
        assertTrue(r.getGranted().contains("btn:system/user:list"), "模板 BUTTON 点应生效");
    }

    // ==================== 变更日志与实时生效 ====================

    @Test
    void changeLog_recorded_withBeforeAfter() {
        Long userId = createTestUser(createDept());
        Long deptId = sysUserMapper.selectById(userId).getDeptId();
        long beforeCount = changeLogMapper.selectCount(null);

        assign("DEPT", deptId, P_LIST, "GRANT");

        Long afterCount = changeLogMapper.selectCount(null);
        assertNotEquals(beforeCount, afterCount, "权限变更应记录日志");

        var page = changeLogService.page("DEPT", deptId, null, null, null, null, null, 1, 10);
        assertFalse(page.getRecords().isEmpty(), "应能按主体查询到变更日志");
        SysPermChangeLog logEntry = page.getRecords().get(0);
        assertEquals("GRANT", logEntry.getOperation());
        assertTrue(logEntry.getBeforeJson() != null || logEntry.getAfterJson() != null,
                "日志应含变更前后快照");
    }

    @Test
    void cacheVersion_bumps_onAssignment_realtimeEffective() {
        Long userId = createTestUser(createDept());
        Long deptId = sysUserMapper.selectById(userId).getDeptId();

        long v1 = cacheService.currentVersion();
        assertFalse(cacheService.hasPerm(userId, P_LIST), "未配置前不持有（且未启用精细控制）");

        assign("DEPT", deptId, P_LIST, "GRANT");
        long v2 = cacheService.currentVersion();
        assertTrue(v2 > v1, "权限变更应递增全局版本号");

        // 版本递增后缓存重载 → 实时生效（无需重新登录）
        assertTrue(cacheService.hasPerm(userId, P_LIST), "变更后下一次请求应实时生效");

        // DENY 后实时回收
        assign("DEPT", deptId, P_LIST, "DENY");
        assertFalse(cacheService.hasPerm(userId, P_LIST), "DENY 后应实时回收权限");
    }

    @Test
    void userPositionAssign_andRemove_affectsPerm() {
        Long userId = createTestUser(createDept());
        Long posId = createPosition("T-TEST-UP-1");
        assign("POSITION", posId, P_LIST, "GRANT");

        UserPositionDTO dto = new UserPositionDTO();
        dto.setUserId(userId);
        dto.setPositionId(posId);
        dto.setPositionKind("FIXED");
        Long upId = positionService.assignUserPosition(dto);
        assertTrue(upId > 0);

        assertTrue(cacheService.hasPerm(userId, P_LIST), "分配职位后应实时生效");

        positionService.removeUserPosition(upId, "测试移除");
        assertFalse(cacheService.hasPerm(userId, P_LIST), "移除职位后应实时回收");
    }

    // ==================== helpers ====================

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private Long createTestUser(Long deptId) {
        SysUser user = new SysUser();
        user.setUsername("permtest_" + System.nanoTime());
        user.setPassword("$2b$10$RKQHeTSFJpJQkzlcbzoxS.F55tUMFEOImnegl0VUtCTIq2GoKGBCS");
        user.setRealName("权限测试用户");
        user.setDeptId(deptId);
        user.setStatus(1);
        sysUserMapper.insert(user);
        return user.getId();
    }

    private Long createDept() {
        com.dept.mgmt.entity.SysDept dept = new com.dept.mgmt.entity.SysDept();
        dept.setParentId(0L);
        dept.setDeptName("权限测试部门_" + System.nanoTime());
        dept.setStatus(1);
        sysDeptMapper.insert(dept);
        return dept.getId();
    }

    @Autowired
    private com.dept.mgmt.mapper.SysDeptMapper sysDeptMapper;

    private Long createPosition(String code) {
        SysPosition position = new SysPosition();
        position.setPositionCode(code + "_" + System.nanoTime());
        position.setPositionName("测试职位" + code);
        position.setStatus(1);
        return positionService.createPosition(position);
    }

    private void assign(String subjectType, Long subjectId, String permCode, String effect) {
        PermAssignDTO dto = dto(subjectType, subjectId, effect, List.of(permCode));
        assignmentService.assign(dto);
    }

    private PermAssignDTO dto(String subjectType, Long subjectId, String effect, List<String> codes) {
        PermAssignDTO dto = new PermAssignDTO();
        dto.setSubjectType(subjectType);
        dto.setSubjectId(subjectId);
        dto.setEffect(effect);
        dto.setPermCodes(codes);
        return dto;
    }

    private void assignFixedPosition(Long userId, Long positionId) {
        UserPositionDTO dto = new UserPositionDTO();
        dto.setUserId(userId);
        dto.setPositionId(positionId);
        dto.setPositionKind(SysUserPosition.KIND_FIXED);
        positionService.assignUserPosition(dto);
    }

    private void assignTempPosition(Long userId, Long positionId, LocalDateTime start, LocalDateTime end) {
        UserPositionDTO dto = new UserPositionDTO();
        dto.setUserId(userId);
        dto.setPositionId(positionId);
        dto.setPositionKind(SysUserPosition.KIND_TEMP);
        dto.setStartTime(start);
        dto.setEndTime(end);
        positionService.assignUserPosition(dto);
    }

    @Autowired
    private com.dept.mgmt.mapper.SysPermTemplateMapper templateMapper;

    private Long findTemplateId(String code) {
        var t = templateMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dept.mgmt.fineperm.entity.SysPermTemplate>()
                .eq(com.dept.mgmt.fineperm.entity.SysPermTemplate::getTemplateCode, code));
        return t == null ? null : t.getId();
    }
}
