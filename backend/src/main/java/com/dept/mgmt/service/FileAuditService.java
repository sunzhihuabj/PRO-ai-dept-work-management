package com.dept.mgmt.service;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.dto.FileAuditDTO;
import com.dept.mgmt.entity.FileAudit;
import com.dept.mgmt.entity.FileInfo;
import com.dept.mgmt.mapper.FileAuditMapper;
import com.dept.mgmt.mapper.FileInfoMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件审核业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAuditService {

    private final FileAuditMapper fileAuditMapper;
    private final FileInfoMapper fileInfoMapper;

    // 允许发起审核的角色：档案管理员、审核员、部门负责人（领导）
    private static final java.util.Set<String> AUDIT_ALLOWED_ROLES = java.util.Set.of(
            "ROLE_ARCHIVIST", "ROLE_AUDITOR", "ROLE_DEPT_LEADER");

    /**
     * 对文件进行审核（通过或驳回）
     * @param fileId 文件 ID
     * @param dto 包含 status (APPROVED/REJECTED) 和 comment
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditFile(Long fileId, FileAuditDTO dto) {
        FileInfo fileInfo = fileInfoMapper.selectById(fileId);
        if (fileInfo == null) {
            throw new BusinessException(404, "文件不存在");
        }
        if (dto.getStatus() == null || (!dto.getStatus().equalsIgnoreCase("APPROVED") && !dto.getStatus().equalsIgnoreCase("REJECTED"))) {
            throw new BusinessException(400, "审核状态必须为 APPROVED 或 REJECTED");
        }
        LoginUser loginUser = LoginUser.current();
        Long auditorId = loginUser != null ? loginUser.getUserId() : null;
        if (auditorId == null) {
            throw new BusinessException(401, "未登录");
        }
        // 角色权限校验
        String roles = loginUser.getRoles() == null ? "" : loginUser.getRoles();
        boolean allowed = java.util.Arrays.stream(roles.split(","))
                .map(String::trim)
                .anyMatch(AUDIT_ALLOWED_ROLES::contains);
        if (!allowed) {
            throw new BusinessException(403, "仅档案管理员、审核员或部门负责人可执行文件审核");
        }
        FileAudit audit = new FileAudit();
        audit.setFileId(fileId);
        audit.setAuditorId(auditorId);
        audit.setStatus(dto.getStatus().toUpperCase());
        audit.setComment(dto.getComment());
        audit.setAuditTime(LocalDateTime.now());
        fileAuditMapper.insert(audit);
        log.info("文件审核完成: fileId={}, auditorId={}, status={}", fileId, auditorId, audit.getStatus());
    }

    /**
     * 查询某个文件的所有审核记录，按时间倒序返回
     */
    public List<FileAudit> listAuditsByFileId(Long fileId) {
        return fileAuditMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileAudit>()
                        .eq(FileAudit::getFileId, fileId)
                        .orderByDesc(FileAudit::getAuditTime)
        );
    }
}
