package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.dto.ProjectDTO;
import com.dept.mgmt.entity.Milestone;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.entity.ProjectMember;
import com.dept.mgmt.entity.ProjectPhase;
import com.dept.mgmt.mapper.MilestoneMapper;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mapper.ProjectMemberMapper;
import com.dept.mgmt.mapper.ProjectPhaseMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目服务
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectPhaseMapper projectPhaseMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final MilestoneMapper milestoneMapper;

    /** 项目类型缩写 */
    private static final String[] TYPE_CODES = {"", "ZJC", "JG", "YF"};
    /** 整机厂阶段模板 */
    private static final String[] ZJC_PHASES = {
            "项目立项", "需求分析", "技术方案", "方案评审", "详细设计", "设计评审",
            "采购策划", "招标采购", "到货验收", "安装调试", "试运行", "竣工验收",
            "培训交付", "质保服务", "项目总结", "资料归档", "合同管理", "变更管理", "风险管理"
    };
    /** 技改阶段模板 */
    private static final String[] JG_PHASES = {
            "需求调研", "方案设计", "实施改造", "测试验证", "验收评估", "项目收尾"
    };
    /** 研发阶段模板 */
    private static final String[] YF_PHASES = {
            "需求分析", "系统设计", "开发实现", "测试验证", "部署上线"
    };

    /**
     * 项目列表分页
     */
    public PageResult<Project> list(Integer pageNum, Integer pageSize, String name, Integer status, Integer projectType) {
        Page<Project> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), Project::getProjectName, name);
        wrapper.eq(status != null, Project::getStatus, status);
        wrapper.eq(projectType != null, Project::getProjectType, projectType);
        wrapper.orderByDesc(Project::getCreateTime);
        projectMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    /**
     * 项目详情
     */
    public Project detail(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        return project;
    }

    /**
     * 创建项目（含自动生成编号、阶段模板）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProjectDTO dto) {
        LoginUser loginUser = LoginUser.current();

        // 生成项目编号 PRJ-年份-类型缩写-序号
        String typeCode = dto.getProjectType() >= 1 && dto.getProjectType() <= 3
                ? TYPE_CODES[dto.getProjectType()] : "OTH";
        String year = String.valueOf(LocalDate.now().getYear());
        Long count = projectMapper.selectCount(null) + 1;
        String projectCode = String.format("PRJ-%s-%s-%03d", year, typeCode, count);

        Project project = new Project();
        project.setProjectCode(projectCode);
        project.setProjectName(dto.getProjectName());
        project.setProjectType(dto.getProjectType());
        project.setCustomerName(dto.getCustomerName());
        project.setProjectLeader(dto.getProjectLeader());
        project.setStatus(0);
        project.setContractAmount(dto.getContractAmount());
        project.setTargetCost(dto.getTargetCost());
        project.setPlanStartDate(dto.getPlanStartDate());
        project.setPlanEndDate(dto.getPlanEndDate());
        project.setDescription(dto.getDescription());
        project.setCreateBy(loginUser != null ? loginUser.getUsername() : "system");

        projectMapper.insert(project);
        log.info("创建项目成功: {} ({})", project.getProjectCode(), project.getProjectName());

        generatePhases(project);

        return project.getId();
    }

    /**
     * 根据项目类型生成阶段
     */
    private void generatePhases(Project project) {
        String[] phaseNames;
        int projectType = project.getProjectType();
        if (projectType == 1) {
            phaseNames = ZJC_PHASES;
        } else if (projectType == 2) {
            phaseNames = JG_PHASES;
        } else {
            phaseNames = YF_PHASES;
        }

        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(
                project.getPlanStartDate(), project.getPlanEndDate()) + 1;
        long daysPerPhase = Math.max(1, totalDays / phaseNames.length);

        for (int i = 0; i < phaseNames.length; i++) {
            ProjectPhase phase = new ProjectPhase();
            phase.setProjectId(project.getId());
            phase.setPhaseName(phaseNames[i]);
            phase.setPhaseOrder(i + 1);
            phase.setPhaseType(projectType);
            phase.setPlanStartDate(project.getPlanStartDate().plusDays(i * daysPerPhase));
            phase.setPlanEndDate(i == phaseNames.length - 1
                    ? project.getPlanEndDate()
                    : project.getPlanStartDate().plusDays((i + 1) * daysPerPhase - 1));
            phase.setStatus(0);
            phase.setDescription(phaseNames[i] + "阶段");
            projectPhaseMapper.insert(phase);
        }
        log.info("项目{}生成{}个阶段", project.getProjectCode(), phaseNames.length);
    }

    /**
     * 更新项目
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProjectDTO dto) {
        Project existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "项目不存在");
        }
        Project project = new Project();
        project.setId(id);
        project.setProjectName(dto.getProjectName());
        project.setProjectType(dto.getProjectType());
        project.setCustomerName(dto.getCustomerName());
        project.setProjectLeader(dto.getProjectLeader());
        project.setContractAmount(dto.getContractAmount());
        project.setTargetCost(dto.getTargetCost());
        project.setPlanStartDate(dto.getPlanStartDate());
        project.setPlanEndDate(dto.getPlanEndDate());
        project.setDescription(dto.getDescription());
        projectMapper.updateById(project);
        log.info("更新项目成功: id={}", id);
    }

    /**
     * 删除项目
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Project existing = projectMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "项目不存在");
        }
        projectMapper.deleteById(id);
        log.info("删除项目成功: id={}", id);
    }

    /**
     * 获取项目阶段列表
     */
    public List<ProjectPhase> listPhases(Long projectId) {
        LambdaQueryWrapper<ProjectPhase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectPhase::getProjectId, projectId);
        wrapper.orderByAsc(ProjectPhase::getPhaseOrder);
        return projectPhaseMapper.selectList(wrapper);
    }

    /**
     * 更新阶段状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePhaseStatus(Long phaseId, Integer status) {
        ProjectPhase phase = projectPhaseMapper.selectById(phaseId);
        if (phase == null) {
            throw new BusinessException(404, "阶段不存在");
        }
        ProjectPhase update = new ProjectPhase();
        update.setId(phaseId);
        update.setStatus(status);
        if (status == 1) {
            update.setActualStartDate(LocalDate.now());
        } else if (status == 2) {
            update.setActualEndDate(LocalDate.now());
        }
        projectPhaseMapper.updateById(update);
        log.info("更新阶段状态成功: phaseId={}, status={}", phaseId, status);
    }

    // ==================== 项目成员 ====================

    /**
     * 在项目中的成员列表
     */
    public List<ProjectMember> listMembers(Long projectId) {
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, projectId);
        wrapper.eq(ProjectMember::getStatus, 1);
        wrapper.orderByAsc(ProjectMember::getJoinTime);
        return projectMemberMapper.selectList(wrapper);
    }

    /**
     * 添加成员（同项目同用户唯一，重复添加时恢复在项状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public ProjectMember addMember(Long projectId, Long userId, String roleType) {
        if (projectMapper.selectById(projectId) == null) {
            throw new BusinessException(404, "项目不存在");
        }
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getUserId, userId);
        ProjectMember exists = projectMemberMapper.selectOne(wrapper);
        if (exists != null) {
            if (exists.getStatus() != null && exists.getStatus() == 1) {
                throw new BusinessException(409, "该用户已是项目成员");
            }
            // 曾离开 → 恢复
            exists.setStatus(1);
            exists.setLeaveTime(null);
            exists.setRoleType(roleType);
            projectMemberMapper.updateById(exists);
            return exists;
        }
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setRoleType(roleType);
        member.setJoinTime(java.time.LocalDateTime.now());
        member.setStatus(1);
        projectMemberMapper.insert(member);
        log.info("添加项目成员: projectId={}, userId={}, role={}", projectId, userId, roleType);
        return member;
    }

    /**
     * 移除成员（置为已离开）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long memberId) {
        ProjectMember member = projectMemberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(404, "成员记录不存在");
        }
        member.setStatus(0);
        member.setLeaveTime(java.time.LocalDateTime.now());
        projectMemberMapper.updateById(member);
        log.info("移除项目成员: memberId={}", memberId);
    }

    // ==================== 里程碑 ====================

    /**
     * 项目里程碑（按计划日期升序）
     */
    public List<Milestone> listMilestones(Long projectId) {
        LambdaQueryWrapper<Milestone> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Milestone::getProjectId, projectId);
        wrapper.orderByAsc(Milestone::getPlanDate);
        return milestoneMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createMilestone(Milestone dto) {
        if (dto.getProjectId() == null) {
            throw new BusinessException(400, "所属项目不能为空");
        }
        if (dto.getMilestoneName() == null || dto.getMilestoneName().isBlank()) {
            throw new BusinessException(400, "里程碑名称不能为空");
        }
        dto.setId(null);
        if (dto.getStatus() == null) {
            dto.setStatus(0);
        }
        milestoneMapper.insert(dto);
        return dto.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMilestone(Long id, Milestone dto) {
        Milestone exists = milestoneMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "里程碑不存在");
        }
        exists.setMilestoneName(dto.getMilestoneName());
        exists.setPhaseId(dto.getPhaseId());
        exists.setPlanDate(dto.getPlanDate());
        exists.setDescription(dto.getDescription());
        milestoneMapper.updateById(exists);
    }

    /**
     * 里程碑状态：0未开始 → 1已完成（记实际日期）/ 2延期
     */
    @Transactional(rollbackFor = Exception.class)
    public void markMilestone(Long id, Integer status) {
        Milestone m = milestoneMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(404, "里程碑不存在");
        }
        if (status == null || (status != 1 && status != 2)) {
            throw new BusinessException(400, "仅支持标记完成(1)或延期(2)");
        }
        m.setStatus(status);
        if (status == 1) {
            m.setActualDate(LocalDate.now());
        }
        milestoneMapper.updateById(m);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteMilestone(Long id) {
        if (milestoneMapper.selectById(id) == null) {
            throw new BusinessException(404, "里程碑不存在");
        }
        milestoneMapper.deleteById(id);
    }
}
