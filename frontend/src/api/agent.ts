import { get, post } from '@/utils/request'

export interface AgentTask {
  id: number
  taskNo: string
  capabilityCode: string
  instruction: string
  status: 'queued' | 'running' | 'pending_confirm' | 'done' | 'failed' | 'escalated' | 'cancelled' | 'rolled_back'
  intentConfidence?: number
  resultText?: string
  aiGenerated?: number
  resultFeedback?: string
  errorMsg?: string
  createTime: string
  finishedAt?: string
}

export interface TaskEvent {
  id: number
  fromStatus?: string
  toStatus: string
  operator: string
  detail?: string
  createTime: string
}

/** 提交指令（自动意图识别） */
export function submitInstructionApi(instruction: string, capabilityCode?: string) {
  return post('/v1/agent/tasks', { instruction, capabilityCode })
}

/** 我的任务列表 */
export function getMyTasksApi() {
  return get('/v1/agent/tasks')
}

/** 任务详情（含时间线） */
export function getTaskDetailApi(id: number) {
  return get(`/v1/agent/tasks/${id}`)
}

/** 确认执行 */
export function confirmTaskApi(id: number) {
  return post(`/v1/agent/tasks/${id}/confirm`, {})
}

/** 取消任务 */
export function cancelTaskApi(id: number) {
  return post(`/v1/agent/tasks/${id}/cancel`, {})
}

/** 结果三态反馈 */
export function feedbackTaskApi(id: number, feedback: 'ACCEPTED' | 'MODIFIED' | 'REJECTED') {
  return post(`/v1/agent/tasks/${id}/feedback`, { feedback })
}

/** 能力自主策略列表 */
export function getAutonomyPoliciesApi() {
  return get('/v1/agent/tasks/autonomy/policies')
}

/** 手动调整自主级别 */
export function adjustLevelApi(capabilityCode: string, level: number) {
  return post(`/v1/agent/tasks/autonomy/${capabilityCode}/level`, { level })
}

/** 手动触发升降级评估 */
export function evaluateAutonomyApi() {
  return post('/v1/agent/tasks/autonomy/evaluate', {})
}
