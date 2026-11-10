package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.common.result.PageResult;
import com.dept.mgmt.dto.TaskDTO;
import com.dept.mgmt.dto.WorkHourDTO;
import com.dept.mgmt.entity.Task;
import com.dept.mgmt.entity.TaskLog;
import com.dept.mgmt.entity.WorkHour;
import com.dept.mgmt.mapper.TaskLogMapper;
import com.dept.mgmt.mapper.TaskMapper;
import com.dept.mgmt.mapper.WorkHourMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务服务
 * 含状态流转、工时记录、任务日志
 *
 * @author dept-mgmt
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskLogMapper taskLogMapper;
    private final WorkHourMapper workHourMapper;

    /**
     * 任务列表分页
     */
    public PageResult<Task> list(Integer pageNum, Integer pageSize, Long projectId, Integer status,
                                  Integer priority, Long assigneeId, String keyword) {
        Page<Task> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, Task::getProjectId, projectId);
        wrapper.eq(status != null, Task::getStatus, status);
        wrapper.eq(priority != null, Task::getPriority, priority);
        wrapper.eq(assigneeId != null, Task::getAssigneeId, assigneeId);
        wrapper.like(StringUtils.hasText(keyword), Task::getTaskName, keyword);
        wrapper.orderByDesc(Task::getPriority).orderByAsc(Task::getDeadline);
        taskMapper.selectPage(page, wrapper);
        return PageResult.of(page);
    }

    /**
     * 任务详情
     */
    public Task detail(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return task;
    }

    /**
     * 创建任务
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(TaskDTO dto) {
        LoginUser loginUser = LoginUser.current();
        Task task = new Task();
        task.setProjectId(dto.getProjectId());
        task.setPhaseId(dto.getPhaseId());
        task.setTaskName(dto.getTaskName());
        task.setTaskType(dto.getTaskType());
        task.setValueType(dto.getValueType());
        task.setWasteType(dto.getWasteType());
        task.setAssigneeId(dto.getAssigneeId());
        task.setCollaboratorIds(dto.getCollaboratorIds());
        task.setPriority(dto.getPriority() == null ? 2 : dto.getPriority());
        task.setStatus(0);
        task.setProgress(0);
        task.setPlanHours(dto.getPlanHours());
        task.setActualHours(BigDecimal.ZERO);
        task.setStartDate(dto.getStartDate());
        task.setDeadline(dto.getDeadline());
        task.setDescription(dto.getDescription());
        task.setPullMode(dto.getPullMode() == null ? 0 : dto.getPullMode());
        task.setCreateBy(loginUser != null ? loginUser.getUsername() : "system");

        taskMapper.insert(task);

        recordLog(task.getId(), "CREATE", "创建任务: " + task.getTaskName(),
                loginUser != null ? loginUser.getUsername() : "system", null, task.getTaskName());

        log.info("创建任务成功: id={}, name={}", task.getId(), task.getTaskName());
        return task.getId();
    }

    /**
     * 更新任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TaskDTO dto) {
        Task existing = taskMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (existing.getStatus() == 3 || existing.getStatus() == 4) {
            throw new BusinessException(409, "已完成或已关闭的任务不可修改");
        }
        Task task = new Task();
        task.setId(id);
        task.setPhaseId(dto.getPhaseId());
        task.setTaskName(dto.getTaskName());
        task.setTaskType(dto.getTaskType());
        task.setValueType(dto.getValueType());
        task.setWasteType(dto.getWasteType());
        task.setAssigneeId(dto.getAssigneeId());
        task.setCollaboratorIds(dto.getCollaboratorIds());
        task.setPriority(dto.getPriority());
        task.setPlanHours(dto.getPlanHours());
        task.setStartDate(dto.getStartDate());
        task.setDeadline(dto.getDeadline());
        task.setDescription(dto.getDescription());
        task.setPullMode(dto.getPullMode());
        taskMapper.updateById(task);
        log.info("更新任务成功: id={}", id);
    }

    /**
     * 删除任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Task existing = taskMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (existing.getStatus() == 1) {
            throw new BusinessException(409, "进行中的任务不可删除");
        }
        taskMapper.deleteById(id);
        log.info("删除任务成功: id={}", id);
    }

    /**
     * 任务状态流转
     * 0待办→1进行中→2待审核→3已完成→4已关闭
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer targetStatus, String comment) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        Integer currentStatus = task.getStatus();
        validateStatusTransition(currentStatus, targetStatus);

        Task update = new Task();
        update.setId(id);
        update.setStatus(targetStatus);

        switch (targetStatus) {
            case 1 -> {
                if (task.getActualStartTime() == null) {
                    update.setActualStartTime(LocalDateTime.now());
                }
            }
            case 3 -> {
                update.setCompleteTime(LocalDateTime.now());
                update.setProgress(100);
            }
        }

        taskMapper.updateById(update);

        LoginUser loginUser = LoginUser.current();
        Map<String, Object> oldVal = new HashMap<>();
        oldVal.put("status", currentStatus);
        Map<String, Object> newVal = new HashMap<>();
        newVal.put("status", targetStatus);
        recordLog(id, "STATUS_CHANGE",
                "状态变更: " + statusName(currentStatus) + " → " + statusName(targetStatus)
                        + (StringUtils.hasText(comment) ? " | " + comment : ""),
                loginUser != null ? loginUser.getUsername() : "system",
                oldVal.toString(), newVal.toString());

        log.info("任务状态流转: id={}, {} → {}", id, statusName(currentStatus), statusName(targetStatus));
    }

    /**
     * 校验状态流转合法性
     */
    private void validateStatusTransition(Integer from, Integer to) {
        boolean valid = switch (from) {
            case 0 -> to == 1 || to == 4;
            case 1 -> to == 2 || to == 0 || to == 4;
            case 2 -> to == 3 || to == 1 || to == 4;
            case 3 -> false;
            case 4 -> false;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(409,
                    "状态流转不允许: " + statusName(from) + " → " + statusName(to));
        }
    }

    /**
     * 录入工时
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkHour addWorkHour(Long taskId, WorkHourDTO dto) {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            throw new BusinessException(401, "未登录");
        }

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        WorkHour workHour = new WorkHour();
        workHour.setTaskId(taskId);
        workHour.setUserId(loginUser.getUserId());
        workHour.setWorkDate(dto.getWorkDate());
        workHour.setHours(dto.getHours());
        workHour.setDescription(dto.getDescription());
        workHour.setStatus(1);
        workHourMapper.insert(workHour);

        taskMapper.addActualHours(taskId, dto.getHours());

        log.info("录入工时成功: taskId={}, hours={}, user={}", taskId, dto.getHours(), loginUser.getUsername());
        return workHour;
    }

    /**
     * 获取任务工时列表
     */
    public List<WorkHour> listWorkHours(Long taskId) {
        LambdaQueryWrapper<WorkHour> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkHour::getTaskId, taskId);
        wrapper.orderByDesc(WorkHour::getWorkDate);
        return workHourMapper.selectList(wrapper);
    }

    /**
     * 获取任务日志列表
     */
    public List<TaskLog> listTaskLogs(Long taskId) {
        LambdaQueryWrapper<TaskLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskLog::getTaskId, taskId);
        wrapper.orderByDesc(TaskLog::getOperTime);
        return taskLogMapper.selectList(wrapper);
    }

    /**
     * 获取当前用户的待办任务
     */
    public List<Task> myTodoTasks(Long userId) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getAssigneeId, userId);
        wrapper.in(Task::getStatus, 0, 1, 2);
        wrapper.orderByAsc(Task::getDeadline);
        return taskMapper.selectList(wrapper);
    }

    /**
     * 更新任务进度
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(Long id, Integer progress) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (progress < 0 || progress > 100) {
            throw new BusinessException(400, "进度值必须在0-100之间");
        }
        Task update = new Task();
        update.setId(id);
        update.setProgress(progress);
        taskMapper.updateById(update);
        log.info("更新任务进度: id={}, progress={}", id, progress);
    }

    /**
     * 记录任务日志
     */
    private void recordLog(Long taskId, String operType, String operDesc, String operBy, String oldVal, String newVal) {
        TaskLog log = new TaskLog();
        log.setTaskId(taskId);
        log.setOperType(operType);
        log.setOperDesc(operDesc);
        log.setOperBy(operBy);
        log.setOperTime(LocalDateTime.now());
        log.setOldValue(oldVal);
        log.setNewValue(newVal);
        taskLogMapper.insert(log);
    }

    /**
     * 状态名称
     */
    public static String statusName(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待办";
            case 1 -> "进行中";
            case 2 -> "待审核";
            case 3 -> "已完成";
            case 4 -> "已关闭";
            default -> "未知";
        };
    }
}
