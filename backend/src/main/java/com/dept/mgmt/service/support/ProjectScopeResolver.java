package com.dept.mgmt.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目名称查询解析器（T-405 项目名称口径）
 *
 * 前端项目筛选统一传 projectName（模糊、大小写不敏感），本组件负责：
 * 1. 将项目名称模糊解析为项目ID集合（MySQL utf8mb4_unicode_ci 排序规则天然大小写不敏感）
 * 2. 统一应用过滤逻辑：projectName 解析优先，其次保留精确 projectId 入参向后兼容
 *
 * @author dept-mgmt
 */
@Component
@RequiredArgsConstructor
public class ProjectScopeResolver {

    private final ProjectMapper projectMapper;

    /**
     * 按项目名称模糊解析项目ID集合
     *
     * @return null 表示未按名称过滤（调用方按原逻辑处理）；空集合表示无匹配项目（调用方应返回空结果）
     */
    public List<Long> resolveIds(String projectName) {
        if (!StringUtils.hasText(projectName)) {
            return null;
        }
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .like(Project::getProjectName, projectName.trim()))
                .stream().map(Project::getId).collect(Collectors.toList());
    }

    /**
     * 应用项目过滤条件：名称解析结果优先；未按名称过滤时保留精确 projectId 兼容
     */
    public <T> void applyProjectScope(LambdaQueryWrapper<T> wrapper,
                                      SFunction<T, ?> projectIdColumn,
                                      Long projectId, List<Long> nameIds) {
        if (nameIds != null) {
            // 名称匹配到空集合时用不可能的ID兜底，返回空结果（避免 MP in 空集合生成非法SQL）
            wrapper.in(projectIdColumn, nameIds.isEmpty() ? List.of(-1L) : nameIds);
        } else if (projectId != null) {
            wrapper.eq(projectIdColumn, projectId);
        }
    }
}
