package com.dept.mgmt.service;

import com.dept.mgmt.common.exception.BusinessException;
import com.dept.mgmt.entity.Task;
import com.dept.mgmt.entity.TaskLog;
import com.dept.mgmt.mapper.TaskLogMapper;
import com.dept.mgmt.mapper.TaskMapper;
import com.dept.mgmt.mapper.WorkHourMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务状态机单元测试
 * 状态：0待办 1进行中 2待审核 3已完成 4已关闭
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;
    @Mock
    private TaskLogMapper taskLogMapper;
    @Mock
    private WorkHourMapper workHourMapper;
    @InjectMocks
    private TaskService taskService;

    private Task taskOf(int status) {
        Task t = new Task();
        t.setId(1L);
        t.setStatus(status);
        return t;
    }

    @Test
    void pendingToInProgressShouldSucceedAndLog() {
        when(taskMapper.selectById(1L)).thenReturn(taskOf(0));

        taskService.changeStatus(1L, 1, "go");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        verify(taskLogMapper).insert(any(TaskLog.class));
    }

    @Test
    void completedIsTerminal() {
        when(taskMapper.selectById(1L)).thenReturn(taskOf(3));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.changeStatus(1L, 1, ""));
        assertEquals(409, ex.getCode());
    }

    @Test
    void pendingToCompletedDirectlyRejected() {
        when(taskMapper.selectById(1L)).thenReturn(taskOf(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> taskService.changeStatus(1L, 3, ""));
        assertEquals(409, ex.getCode());
    }

    @Test
    void reviewPassedSetsProgressHundred() {
        Task t = taskOf(2);
        t.setProgress(80);
        when(taskMapper.selectById(1L)).thenReturn(t);

        taskService.changeStatus(1L, 3, "");

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals(3, captor.getValue().getStatus());
        assertEquals(100, captor.getValue().getProgress());
    }
}
