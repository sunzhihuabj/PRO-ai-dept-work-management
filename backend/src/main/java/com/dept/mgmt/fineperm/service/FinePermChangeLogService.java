package com.dept.mgmt.fineperm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.fineperm.entity.SysPermChangeLog;
import com.dept.mgmt.mapper.SysPermChangeLogMapper;
import com.dept.mgmt.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 权限变更日志服务（T-404）
 * 写入：每次权限配置变更完整记录操作人/时间/主体/维度/变更前后内容快照
 * 查询：分页审计查询（主体/操作人/时间范围）
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinePermChangeLogService {

    private final SysPermChangeLogMapper changeLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 记录权限变更日志
     *
     * @param subjectType 主体类型（USER/DEPT/POSITION）
     * @param subjectId   主体ID
     * @param subjectName 主体名称
     * @param dimension   变更维度（DEPT/FIXED/TEMP/POSITION）
     * @param operation   操作类型（GRANT/DENY/CLEAR/ASSIGN_POSITION/REMOVE_POSITION/APPLY_TEMPLATE/BATCH_GRANT/BATCH_CLEAR）
     * @param permCodes   本次涉及的权限点
     * @param before      变更前快照对象（序列化为 JSON）
     * @param after       变更后快照对象
     * @param remark      备注
     */
    public void record(String subjectType, Long subjectId, String subjectName, String dimension,
                       String operation, Collection<String> permCodes,
                       Object before, Object after, String remark) {
        try {
            SysPermChangeLog logEntry = new SysPermChangeLog();
            LoginUser operator = LoginUser.current();
            if (operator != null) {
                logEntry.setOperatorId(operator.getUserId());
                logEntry.setOperatorName(operator.getUsername());
            }
            logEntry.setSubjectType(subjectType);
            logEntry.setSubjectId(subjectId);
            logEntry.setSubjectName(subjectName);
            logEntry.setDimension(dimension);
            logEntry.setOperation(operation);
            if (permCodes != null && !permCodes.isEmpty()) {
                logEntry.setPermCodes(objectMapper.writeValueAsString(permCodes));
            }
            logEntry.setBeforeJson(before == null ? null : objectMapper.writeValueAsString(before));
            logEntry.setAfterJson(after == null ? null : objectMapper.writeValueAsString(after));
            logEntry.setRemark(remark);
            changeLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 日志失败不阻断业务，但必须告警
            log.error("权限变更日志记录失败 subject={}:{}", subjectType, subjectId, e);
        }
    }

    /**
     * 分页审计查询
     */
    public PageResult<SysPermChangeLog> page(String subjectType, Long subjectId, String operatorName,
                                             String dimension, String operation,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             long pageNo, long pageSize) {
        LambdaQueryWrapper<SysPermChangeLog> wrapper = new LambdaQueryWrapper<>();
        if (subjectType != null && !subjectType.isBlank()) {
            wrapper.eq(SysPermChangeLog::getSubjectType, subjectType);
        }
        if (subjectId != null) {
            wrapper.eq(SysPermChangeLog::getSubjectId, subjectId);
        }
        if (operatorName != null && !operatorName.isBlank()) {
            wrapper.like(SysPermChangeLog::getOperatorName, operatorName);
        }
        if (dimension != null && !dimension.isBlank()) {
            wrapper.eq(SysPermChangeLog::getDimension, dimension);
        }
        if (operation != null && !operation.isBlank()) {
            wrapper.eq(SysPermChangeLog::getOperation, operation);
        }
        if (startTime != null) {
            wrapper.ge(SysPermChangeLog::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysPermChangeLog::getCreateTime, endTime);
        }
        wrapper.orderByDesc(SysPermChangeLog::getCreateTime).orderByDesc(SysPermChangeLog::getId);

        Page<SysPermChangeLog> page = changeLogMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page);
    }

    /**
     * 按主体查询最近日志（非分页，审计详情页用）
     */
    public List<SysPermChangeLog> listBySubject(String subjectType, Long subjectId, int limit) {
        Page<SysPermChangeLog> page = changeLogMapper.selectPage(new Page<>(1, limit),
                new LambdaQueryWrapper<SysPermChangeLog>()
                        .eq(SysPermChangeLog::getSubjectType, subjectType)
                        .eq(SysPermChangeLog::getSubjectId, subjectId)
                        .orderByDesc(SysPermChangeLog::getCreateTime));
        return page.getRecords();
    }
}
