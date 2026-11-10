import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface TaskItem {
  id: number
  projectId: number
  phaseId?: number
  taskName: string
  taskType?: string
  valueType?: number
  assigneeId?: number
  priority: number
  status: number
  progress: number
  planHours?: number
  actualHours?: number
  startDate?: string
  deadline?: string
  description?: string
  createBy?: string
  createTime?: string
}

export interface TaskListParams {
  pageNum?: number
  pageSize?: number
  projectId?: number
  status?: number
  priority?: number
  assigneeId?: number
  keyword?: string
}

export interface TaskDTO {
  projectId: number
  phaseId?: number
  taskName: string
  taskType?: string
  valueType?: number
  assigneeId?: number
  priority?: number
  planHours?: number
  startDate?: string
  deadline?: string
  description?: string
}

export function getTaskListApi(params: TaskListParams) {
  return get<PageResult<TaskItem>>('/v1/tasks', params)
}

export function getTaskDetailApi(id: number) {
  return get<TaskItem>(`/v1/tasks/${id}`)
}

export function createTaskApi(data: TaskDTO) {
  return post<number>('/v1/tasks', data)
}

export function updateTaskApi(id: number, data: TaskDTO) {
  return put(`/v1/tasks/${id}`, data)
}

export function deleteTaskApi(id: number) {
  return del(`/v1/tasks/${id}`)
}

export function changeTaskStatusApi(id: number, targetStatus: number, comment?: string) {
  return put(`/v1/tasks/${id}/status`, { targetStatus, comment })
}

export function updateTaskProgressApi(id: number, progress: number) {
  return put(`/v1/tasks/${id}/progress`, { progress })
}

export function addWorkHourApi(id: number, data: { hours: number; workDate: string; description: string }) {
  return post(`/v1/tasks/${id}/hours`, data)
}

export function getWorkHoursApi(id: number) {
  return get(`/v1/tasks/${id}/hours`)
}

export function getTaskLogsApi(id: number) {
  return get(`/v1/tasks/${id}/logs`)
}

export function getMyTodoApi() {
  return get<TaskItem[]>('/v1/tasks/my-todo')
}

// ---------- 任务池 ----------
export interface TaskPoolItem {
  id: number
  taskName: string
  taskType?: string
  projectType?: number
  phaseOrder?: number
  valueType?: number
  planHours?: number
  description?: string
  status?: number
}

export function listTaskPoolApi(params?: { projectType?: number; status?: number }) {
  return get<TaskPoolItem[]>('/v1/gates/pool', params)
}

export function claimPoolTaskApi(poolId: number, projectId: number, assigneeId: number) {
  return post<number>(`/v1/gates/pool/${poolId}/claim?projectId=${projectId}&assigneeId=${assigneeId}`)
}
