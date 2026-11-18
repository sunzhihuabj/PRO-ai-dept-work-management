// Hermes API Server 类型化封装（端点契约见 docs/Hermes全量接口与嵌入深度调研报告.md）
import { hermes, api } from './client'
import { sseFetch, SseFrame } from './sse'

// ---------- 元信息 ----------
export const getHealth = () => hermes<any>('/health')
export const getHealthDetailed = () => hermes<any>('/health/detailed')
export const getCapabilities = () => hermes<any>('/v1/capabilities')
export const getModels = () => hermes<any>('/v1/models')
export const getModelOptions = (params: { refresh?: boolean; include_unconfigured?: boolean } = {}) => {
  const q = new URLSearchParams()
  if (params.refresh) q.set('refresh', 'true')
  if (params.include_unconfigured) q.set('include_unconfigured', 'true')
  const qs = q.toString()
  return hermes<any>(`/api/model/options${qs ? '?' + qs : ''}`)
}
export const getSkills = () => hermes<any>('/v1/skills')
export const getToolsets = () => hermes<any>('/v1/toolsets')

// ---------- 会话 ----------
export interface HermesSession {
  id: string
  source: string
  user_id: string
  model: string
  title: string | null
  started_at: number
  ended_at: number | null
  end_reason: string | null
  message_count: number
  tool_call_count: number
  input_tokens: number
  output_tokens: number
  estimated_cost_usd: number | null
  last_active: number
  preview: string | null
}

export const listSessions = (params: { limit?: number; offset?: number; source?: string; title?: string } = {}) => {
  const q = new URLSearchParams()
  if (params.limit) q.set('limit', String(params.limit))
  if (params.offset) q.set('offset', String(params.offset))
  if (params.source) q.set('source', params.source)
  if (params.title) q.set('title', params.title)
  const qs = q.toString()
  return hermes<{ object: string; data: HermesSession[]; has_more: boolean }>(`/api/sessions${qs ? '?' + qs : ''}`)
}
export const createSession = (body: { id?: string; title?: string; system_prompt?: string; source?: string; model?: string }) =>
  hermes<any>('/api/sessions', { method: 'POST', body })
export const getSession = (id: string) => hermes<any>(`/api/sessions/${id}`)
export const patchSession = (id: string, body: Record<string, any>) => hermes<any>(`/api/sessions/${id}`, { method: 'PATCH', body })
export const deleteSession = (id: string) => hermes<any>(`/api/sessions/${id}`, { method: 'DELETE' })
export const getSessionMessages = (id: string, params: { limit?: number; offset?: number; order?: 'oldest' | 'latest' } = {}) => {
  const q = new URLSearchParams()
  q.set('limit', String(params.limit ?? 200))
  q.set('order', params.order ?? 'oldest')
  if (params.offset) q.set('offset', String(params.offset))
  return hermes<any>(`/api/sessions/${id}/messages?${q}`)
}
export const forkSession = (id: string, body: Record<string, any> = {}) =>
  hermes<any>(`/api/sessions/${id}/fork`, { method: 'POST', body })
export const lockSessionModel = (id: string, body: { model: string }) =>
  hermes<any>(`/api/sessions/${id}/model`, { method: 'POST', body })

// 会话流式聊天：POST /api/sessions/{id}/chat/stream
// message 支持 string 或多模态部件数组（{type:"text"|"image_url",...}，Hermes 原生 OpenAI vision 形状）
export function chatStream(
  sessionId: string,
  body: { message: string | any[]; system_message?: string; model?: string },
  onFrame: (f: SseFrame) => void,
  signal?: AbortSignal
): Promise<void> {
  return sseFetch(`/api/hermes/api/sessions/${sessionId}/chat/stream`, {
    method: 'POST',
    body,
    signal,
    onFrame
  })
}

// ---------- 异步委托 /v1/runs ----------
export const submitRun = (body: Record<string, any>, idempotencyKey?: string) =>
  hermes<any>('/v1/runs', {
    method: 'POST',
    body,
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
  })
export const getRun = (runId: string) => hermes<any>(`/v1/runs/${runId}`)
export const resolveRunApproval = (runId: string, body: { choice: string; request_id?: string }) =>
  hermes<any>(`/v1/runs/${runId}/approval`, { method: 'POST', body })
export const steerRun = (runId: string, body: { input: string }) =>
  hermes<any>(`/v1/runs/${runId}/steer`, { method: 'POST', body })
export const stopRun = (runId: string) => hermes<any>(`/v1/runs/${runId}/stop`, { method: 'POST' })
export function runEventsSse(runId: string, onFrame: (f: SseFrame) => void, signal?: AbortSignal): Promise<void> {
  return sseFetch(`/api/hermes/v1/runs/${runId}/events`, { onFrame, signal })
}

// ---------- 定时任务 /api/jobs（jobs_admin 能力门控） ----------
export const listJobs = (includeDisabled = true) =>
  hermes<any>(`/api/jobs?include_disabled=${includeDisabled}`)
export const createJob = (body: Record<string, any>) => hermes<any>('/api/jobs', { method: 'POST', body })
export const patchJob = (id: string, body: Record<string, any>) => hermes<any>(`/api/jobs/${id}`, { method: 'PATCH', body })
export const deleteJob = (id: string) => hermes<any>(`/api/jobs/${id}`, { method: 'DELETE' })
export const pauseJob = (id: string) => hermes<any>(`/api/jobs/${id}/pause`, { method: 'POST', body: {} })
export const resumeJob = (id: string) => hermes<any>(`/api/jobs/${id}/resume`, { method: 'POST', body: {} })
export const runJobNow = (id: string, prompt?: string) =>
  hermes<any>(`/api/jobs/${id}/run`, { method: 'POST', body: prompt ? { prompt } : {} })

// ---------- Studio 自有 ----------
export const studioConfig = () => api<any>('/api/studio/config')
export const studioWorkflows = () => api<any[]>('/api/studio/workflows')
export const studioSaveWorkflow = (body: any) => api<any>('/api/studio/workflows', { method: 'POST', body })
export const studioDeleteWorkflow = (id: string) => api<any>(`/api/studio/workflows/${id}`, { method: 'DELETE' })
