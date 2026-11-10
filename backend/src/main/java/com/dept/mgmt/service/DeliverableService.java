package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.DeliverableChecklist;
import com.dept.mgmt.entity.DeliverableTemplate;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.entity.ProjectPhase;
import com.dept.mgmt.mapper.DeliverableChecklistMapper;
import com.dept.mgmt.mapper.DeliverableTemplateMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 交付物管理服务
 * 模板按项目类型定义 → 项目一键生成检查清单 → 绑定档案文件 → 检查确认
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliverableService {

    private final DeliverableTemplateMapper templateMapper;
    private final DeliverableChecklistMapper checklistMapper;
    private final ProjectService projectService;

    /** 交付物模板列表（可按项目类型过滤） */
    public List<DeliverableTemplate> listTemplates(Integer projectType) {
        LambdaQueryWrapper<DeliverableTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectType != null, DeliverableTemplate::getProjectType, projectType);
        wrapper.orderByAsc(DeliverableTemplate::getPhaseOrder);
        return templateMapper.selectList(wrapper);
    }

    /**
     * 按项目类型从模板生成检查清单（幂等：已生成的模板跳过）
     * @return 本次新增条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int generate(Long projectId) {
        Project project = projectService.detail(projectId);
        List<ProjectPhase> phases = projectService.listPhases(projectId);
        Map<Integer, Long> phaseIdByOrder = phases.stream()
                .collect(Collectors.toMap(ProjectPhase::getPhaseOrder, ProjectPhase::getId, (a, b) -> b));

        List<DeliverableTemplate> templates = listTemplates(project.getProjectType());
        if (templates.isEmpty()) {
            throw new BusinessException(404, "该项目类型没有交付物模板");
        }
        // 已生成的模板ID集合（幂等）
        LambdaQueryWrapper<DeliverableChecklist> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(DeliverableChecklist::getProjectId, projectId);
        Set<Long> existTemplateIds = checklistMapper.selectList(existWrapper).stream()
                .map(DeliverableChecklist::getTemplateId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        int created = 0;
        for (DeliverableTemplate t : templates) {
            if (t.getId() != null && existTemplateIds.contains(t.getId())) {
                continue;
            }
            DeliverableChecklist item = new DeliverableChecklist();
            item.setProjectId(projectId);
            Long phaseId = phaseIdByOrder.get(t.getPhaseOrder());
            if (phaseId == null && !phases.isEmpty()) {
                // 模板顺序号缺省(0/未匹配)时归入第一阶段
                phaseId = phases.get(0).getId();
            }
            item.setPhaseId(phaseId);
            item.setTemplateId(t.getId());
            item.setDeliverableName(t.getTemplateName());
            item.setRequired(t.getRequired() == null ? 1 : t.getRequired());
            item.setFileType(t.getFileType());
            item.setUploaded(0);
            item.setChecked(0);
            checklistMapper.insert(item);
            created++;
        }
        log.info("生成交付物清单: projectId={}, 新增={}", projectId, created);
        return created;
    }

    /** 项目交付物清单（按阶段ID/名称排序） */
    public List<DeliverableChecklist> listByProject(Long projectId) {
        LambdaQueryWrapper<DeliverableChecklist> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeliverableChecklist::getProjectId, projectId);
        wrapper.orderByAsc(DeliverableChecklist::getPhaseId).orderByAsc(DeliverableChecklist::getId);
        return checklistMapper.selectList(wrapper);
    }

    /**
     * 绑定已上传文件（档案归档模块 file_info 的 id）
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliverableChecklist bindFile(Long id, Long fileId) {
        if (fileId == null) {
            throw new BusinessException(400, "fileId 不能为空");
        }
        DeliverableChecklist item = checklistMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "交付物不存在");
        }
        item.setFileId(fileId);
        item.setUploaded(1);
        checklistMapper.updateById(item);
        log.info("交付物绑定文件: id={}, fileId={}", id, fileId);
        return item;
    }

    /**
     * 检查确认（必需记录检查人与时间）
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliverableChecklist check(Long id) {
        DeliverableChecklist item = checklistMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "交付物不存在");
        }
        if (item.getUploaded() == null || item.getUploaded() != 1) {
            throw new BusinessException(409, "该交付物尚未上传文件，不能检查通过");
        }
        item.setChecked(1);
        item.setCheckTime(LocalDateTime.now());
        LoginUser loginUser = LoginUser.current();
        item.setCheckBy(loginUser != null ? loginUser.getUsername() : "system");
        checklistMapper.updateById(item);
        return item;
    }

    /**
     * 手动添加清单项
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(DeliverableChecklist dto) {
        if (dto.getProjectId() == null || !StringUtils.hasText(dto.getDeliverableName())) {
            throw new BusinessException(400, "项目与交付物名称必填");
        }
        dto.setId(null);
        dto.setTemplateId(null);
        if (dto.getUploaded() == null) dto.setUploaded(0);
        if (dto.getChecked() == null) dto.setChecked(0);
        checklistMapper.insert(dto);
        return dto.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (checklistMapper.selectById(id) == null) {
            throw new BusinessException(404, "交付物不存在");
        }
        checklistMapper.deleteById(id);
    }

    /** 项目交付物完成度统计 */
    public Map<String, Object> stats(Long projectId) {
        List<DeliverableChecklist> all = listByProject(projectId);
        long total = all.size();
        long uploaded = all.stream().filter(i -> i.getUploaded() != null && i.getUploaded() == 1).count();
        long checked = all.stream().filter(i -> i.getChecked() != null && i.getChecked() == 1).count();
        long requiredTotal = all.stream().filter(i -> i.getRequired() != null && i.getRequired() == 1).count();
        long requiredMissing = all.stream()
                .filter(i -> i.getRequired() != null && i.getRequired() == 1)
                .filter(i -> i.getUploaded() == null || i.getUploaded() != 1)
                .count();
        return Map.of(
                "total", total,
                "uploaded", uploaded,
                "checked", checked,
                "requiredTotal", requiredTotal,
                "requiredMissing", requiredMissing
        );
    }
}
