package com.dept.mgmt.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dept.mgmt.common.result.R;
import com.dept.mgmt.entity.Project;
import com.dept.mgmt.entity.Task;
import com.dept.mgmt.entity.UserMessage;
import com.dept.mgmt.mapper.ProjectMapper;
import com.dept.mgmt.mapper.TaskMapper;
import com.dept.mgmt.mapper.UserMessageMapper;
import com.dept.mgmt.security.LoginUser;
import com.dept.mgmt.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * 工作台接口
 * 提供个人待办、数据概览、最新消息、快捷入口
 *
 * @author dept-mgmt
 */
@Slf4j
@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "工作台", description = "个人待办、数据概览、消息")
public class DashboardController {

    private final TaskMapper taskMapper;
    private final ProjectMapper projectMapper;
    private final UserMessageMapper userMessageMapper;

    @GetMapping("/todo")
    @Operation(summary = "个人待办")
    public R<Map<String, Object>> todo() {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            return R.fail(401, "未登录");
        }
        Long userId = loginUser.getUserId();

        LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(Task::getAssigneeId, userId);
        taskWrapper.in(Task::getStatus, 0, 1, 2);
        taskWrapper.orderByAsc(Task::getDeadline);
        List<Task> todoTasks = taskMapper.selectList(taskWrapper);

        LocalDate today = LocalDate.now();
        long urgentCount = todoTasks.stream()
                .filter(t -> (t.getPriority() != null && t.getPriority() == 1)
                        || (t.getDeadline() != null && t.getDeadline().isBefore(today)))
                .count();

        Map<String, Object> taskGroup = new HashMap<>();
        taskGroup.put("type", "TASK");
        taskGroup.put("name", "待办任务");
        taskGroup.put("count", todoTasks.size());
        taskGroup.put("urgent", (int) urgentCount);
        taskGroup.put("items", todoTasks.stream().limit(10).map(t -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", t.getId());
            item.put("title", t.getTaskName());
            item.put("priority", t.getPriority());
            item.put("priorityText", priorityText(t.getPriority()));
            item.put("deadline", t.getDeadline());
            item.put("status", t.getStatus());
            item.put("statusText", TaskService.statusName(t.getStatus()));
            item.put("overdue", t.getDeadline() != null && t.getDeadline().isBefore(today));
            return item;
        }).toList());

        LambdaQueryWrapper<UserMessage> msgWrapper = new LambdaQueryWrapper<>();
        msgWrapper.eq(UserMessage::getUserId, userId);
        msgWrapper.eq(UserMessage::getIsRead, 0);
        msgWrapper.orderByDesc(UserMessage::getCreateTime);
        List<UserMessage> unreadMessages = userMessageMapper.selectList(msgWrapper);

        Map<String, Object> msgGroup = new HashMap<>();
        msgGroup.put("type", "MESSAGE");
        msgGroup.put("name", "未读消息");
        msgGroup.put("count", unreadMessages.size());
        msgGroup.put("urgent", 0);
        msgGroup.put("items", unreadMessages.stream().limit(5).map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("title", m.getTitle());
            item.put("type", m.getMessageType());
            item.put("createTime", m.getCreateTime());
            return item;
        }).toList());

        return R.ok(Map.of("groups", List.of(taskGroup, msgGroup)));
    }

    @GetMapping("/stats")
    @Operation(summary = "数据概览")
    public R<Map<String, Object>> stats() {
        LoginUser loginUser = LoginUser.current();
        Long userId = loginUser != null ? loginUser.getUserId() : null;
        LocalDate today = LocalDate.now();

        // 全局任务统计（部门视角）
        List<Task> allTasks = taskMapper.selectList(new LambdaQueryWrapper<>());
        long taskTotal = allTasks.size();
        long taskCompleted = allTasks.stream().filter(t -> t.getStatus() != null && t.getStatus() == 3).count();
        long taskInProgress = allTasks.stream().filter(t -> t.getStatus() != null && t.getStatus() == 1).count();
        long taskOverdue = allTasks.stream()
                .filter(t -> t.getDeadline() != null && t.getDeadline().isBefore(today)
                        && t.getStatus() != null && t.getStatus() < 3)
                .count();
        double completionRate = taskTotal > 0 ? (double) taskCompleted / taskTotal * 100 : 0;

        Map<String, Object> taskStats = new HashMap<>();
        taskStats.put("total", taskTotal);
        taskStats.put("completed", taskCompleted);
        taskStats.put("inProgress", taskInProgress);
        taskStats.put("overdue", taskOverdue);
        taskStats.put("completionRate", Math.round(completionRate * 10) / 10.0);

        // 个人任务统计（我的视角）
        if (userId != null) {
            List<Task> myTasks = allTasks.stream()
                    .filter(t -> userId.equals(t.getAssigneeId()))
                    .toList();
            long myTotal = myTasks.size();
            long myCompleted = myTasks.stream().filter(t -> t.getStatus() != null && t.getStatus() == 3).count();
            long myInProgress = myTasks.stream().filter(t -> t.getStatus() != null && t.getStatus() == 1).count();
            long myOverdue = myTasks.stream()
                    .filter(t -> t.getDeadline() != null && t.getDeadline().isBefore(today)
                            && t.getStatus() != null && t.getStatus() < 3)
                    .count();
            Map<String, Object> myTaskStats = new HashMap<>();
            myTaskStats.put("total", myTotal);
            myTaskStats.put("completed", myCompleted);
            myTaskStats.put("inProgress", myInProgress);
            myTaskStats.put("overdue", myOverdue);
            myTaskStats.put("completionRate", myTotal > 0 ? Math.round((double) myCompleted / myTotal * 1000) / 10.0 : 0);
            taskStats.put("my", myTaskStats);
        }

        long projectTotal = projectMapper.selectCount(null);
        LambdaQueryWrapper<Project> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.eq(Project::getStatus, 1);
        long projectActive = projectMapper.selectCount(activeWrapper);
        LambdaQueryWrapper<Project> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(Project::getStatus, 2);
        long projectCompleted = projectMapper.selectCount(completedWrapper);

        Map<String, Object> projectStats = new HashMap<>();
        projectStats.put("total", projectTotal);
        projectStats.put("active", projectActive);
        projectStats.put("completed", projectCompleted);
        projectStats.put("healthScore", 0);

        Map<String, Object> result = new HashMap<>();
        result.put("taskStats", taskStats);
        result.put("projectStats", projectStats);
        return R.ok(result);
    }

    @GetMapping("/notifications")
    @Operation(summary = "最新消息")
    public R<List<Map<String, Object>>> notifications(@RequestParam(defaultValue = "10") Integer limit) {
        LoginUser loginUser = LoginUser.current();
        if (loginUser == null) {
            return R.ok(List.of());
        }
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, loginUser.getUserId());
        wrapper.orderByDesc(UserMessage::getCreateTime);
        wrapper.last("LIMIT " + Math.min(limit, 50));
        List<UserMessage> messages = userMessageMapper.selectList(wrapper);

        return R.ok(messages.stream().map(m -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("type", m.getMessageType());
            item.put("title", m.getTitle());
            item.put("content", m.getContent());
            item.put("isRead", m.getIsRead() != null && m.getIsRead() == 1);
            item.put("createTime", m.getCreateTime());
            return item;
        }).toList());
    }

    @GetMapping("/quick-actions")
    @Operation(summary = "快捷入口")
    public R<List<Map<String, Object>>> quickActions() {
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(action(1, "创建任务", "Plus", "/task/list", 1));
        actions.add(action(2, "新建项目", "FolderAdd", "/project/list", 2));
        actions.add(action(3, "我的待办", "List", "/dashboard", 3));
        actions.add(action(4, "任务列表", "Document", "/task/list", 4));
        return R.ok(actions);
    }

    private Map<String, Object> action(int id, String name, String icon, String path, int sort) {
        Map<String, Object> a = new HashMap<>();
        a.put("id", id);
        a.put("name", name);
        a.put("icon", icon);
        a.put("path", path);
        a.put("sort", sort);
        return a;
    }

    private String priorityText(Integer priority) {
        if (priority == null) return "中";
        return switch (priority) {
            case 1 -> "高";
            case 2 -> "中";
            case 3 -> "低";
            default -> "中";
        };
    }
}
