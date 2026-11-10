package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.entity.SysOperLog;
import com.dept.mgmt.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysOperLogService {

    private final SysOperLogMapper sysOperLogMapper;

    public PageResult<SysOperLog> list(Integer pageNum, Integer pageSize, String title, String operName, Integer status) {
        Page<SysOperLog> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(title), SysOperLog::getTitle, title);
        wrapper.like(StringUtils.hasText(operName), SysOperLog::getOperName, operName);
        wrapper.eq(status != null, SysOperLog::getStatus, status);
        wrapper.orderByDesc(SysOperLog::getOperTime);
        sysOperLogMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    /** 异步落库，异常不影响业务 */
    @Async
    public void record(SysOperLog operLog) {
        try {
            sysOperLogMapper.insert(operLog);
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }
    }
}
