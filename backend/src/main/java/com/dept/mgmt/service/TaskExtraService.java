package com.dept.mgmt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.Task;
import com.dept.mgmt.entity.TaskComment;
import com.dept.mgmt.entity.TaskSub;
import com.dept.mgmt.mapper.TaskCommentMapper;
import com.dept.mgmt.mapper.TaskMapper;
import com.dept.mgmt.mapper.TaskSubMapper;
import com.dept.mgmt.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务扩展服务：子任务 / 评论
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExtraService {

    private final TaskSubMapper taskSubMapper;
    private final TaskCommentMapper taskCommentMapper;
    private final TaskMapper taskMapper;

    // ==================== 子任务 ====================

    public List<TaskSub> listSubs(Long taskId) {
        LambdaQueryWrapper<TaskSub> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskSub::getTaskId, taskId);
        wrapper.orderByAsc(TaskSub::getOrderNum).orderByAsc(TaskSub::getId);
        return taskSubMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskSub addSub(Long taskId, TaskSub dto) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (!StringUtils.hasText(dto.getSubName())) {
            throw new BusinessException(400, "子任务名称不能为空");
        }
        dto.setId(null);
        dto.setTaskId(taskId);
        if (dto.getStatus() == null) {
            dto.setStatus(0);
        }
        dto.setOrderNum(dto.getOrderNum() == null ? (int) (listSubs(taskId).size() + 1) : dto.getOrderNum());
        taskSubMapper.insert(dto);
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSub(Long id, TaskSub dto) {
        TaskSub exists = taskSubMapper.selectById(id);
        if (exists == null) {
            throw new BusinessException(404, "子任务不存在");
        }
        exists.setSubName(dto.getSubName());
        exists.setAssigneeId(dto.getAssigneeId());
        exists.setPlanHours(dto.getPlanHours());
        exists.setDeadline(dto.getDeadline());
        taskSubMapper.updateById(exists);
    }

    /**
     * 勾选子任务状态：0待办 / 1进行中 / 2已完成（完成记时间）
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggleSub(Long id, Integer status) {
        TaskSub sub = taskSubMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(404, "子任务不存在");
        }
        if (status == null || status < 0 || status > 2) {
            throw new BusinessException(400, "状态不合法");
        }
        sub.setStatus(status);
        sub.setCompleteTime(status == 2 ? LocalDateTime.now() : null);
        taskSubMapper.updateById(sub);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSub(Long id) {
        if (taskSubMapper.selectById(id) == null) {
            throw new BusinessException(404, "子任务不存在");
        }
        taskSubMapper.deleteById(id);
    }

    // ==================== 评论 ====================

    public List<TaskComment> listComments(Long taskId) {
        LambdaQueryWrapper<TaskComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskComment::getTaskId, taskId);
        wrapper.orderByAsc(TaskComment::getCreateTime);
        return taskCommentMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public TaskComment addComment(Long taskId, TaskComment dto) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        if (!StringUtils.hasText(dto.getContent())) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        LoginUser loginUser = LoginUser.current();
        dto.setId(null);
        dto.setTaskId(taskId);
        dto.setUserId(loginUser != null ? loginUser.getUserId() : null);
        dto.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        taskCommentMapper.insert(dto);
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long id) {
        TaskComment comment = taskCommentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        LoginUser loginUser = LoginUser.current();
        // 仅评论人本人可删除
        if (loginUser == null || !loginUser.getUserId().equals(comment.getUserId())) {
            throw new BusinessException(403, "仅评论人本人可删除");
        }
        taskCommentMapper.deleteById(id);
    }
}
