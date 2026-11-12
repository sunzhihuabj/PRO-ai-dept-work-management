import { get, post, put, del } from '@/utils/request'

// ==================== 类型 ====================

export interface AiConversation {
  id: number
  convNo: string
  title: string
  userId: number
  pinned: number
  lastMsgTime: string
  createTime: string
}

export interface AiMessage {
  id: number
  conversationId: number
  role: 'user' | 'assistant'
  content: string
  msgType: 'text' | 'task' | 'workflow'
  refTaskId?: number
  refWorkflowId?: number
  createTime: string
}

export interface WfNode {
  key: string
  name: string
  type: 'start' | 'agent' | 'approval' | 'notification'
  x: number
  y: number
  config?: {
    capabilityCode?: string
    instruction?: string
    assignee?: string
    target?: string
    title?: string
    content?: string
  }
}

export interface WfEdge {
  from: string
  to: string
  label?: string
}

export interface WfGraph {
  nodes: WfNode[]
  edges: WfEdge[]
}

export interface WfWorkflow {
  id: number
  wfCode: string
  name: string
  description?: string
  graph?: string
  status: 'draft' | 'published' | 'offline'
  version: number
  createBy?: string
  updateTime?: string
}

export interface WfInstance {
  id: number
  instanceNo: string
  workflowId: number
  workflowName?: string
  graphSnapshot?: string
  status: 'running' | 'completed' | 'failed' | 'cancelled'
  startedBy?: string
  startTime?: string
  endTime?: string
}

export interface WfInstanceNode {
  id: number
  instanceId: number
  nodeKey: string
  nodeName?: string
  nodeType: 'start' | 'agent' | 'approval' | 'notification'
  config?: string
  status: 'pending' | 'running' | 'waiting_approval' | 'done' | 'failed' | 'skipped'
  agentTaskId?: number
  assigneeUserId?: number
  approvalComment?: string
  errorMsg?: string
  startTime?: string
  finishTime?: string
}

// ==================== 对话 ====================

export function createConversationApi(title?: string) {
  return post<AiConversation>('/v1/ai/ws/conversations', { title })
}

export function listConversationsApi() {
  return get<AiConversation[]>('/v1/ai/ws/conversations')
}

export function renameConversationApi(id: number, title: string) {
  return put<void>(`/v1/ai/ws/conversations/${id}`, { title })
}

export function deleteConversationApi(id: number) {
  return del<void>(`/v1/ai/ws/conversations/${id}`)
}

export function listMessagesApi(conversationId: number) {
  return get<AiMessage[]>(`/v1/ai/ws/conversations/${conversationId}/messages`)
}

export function chatApi(conversationId: number, content: string) {
  return post<{ userMessage: AiMessage; assistantMessage: AiMessage }>(
    `/v1/ai/ws/conversations/${conversationId}/chat`, { content })
}

// ==================== 流程定义 ====================

export function createWorkflowApi(data: { name: string; description?: string; graph?: string }) {
  return post<WfWorkflow>('/v1/ai/ws/workflows', data)
}

export function listWorkflowsApi(mine?: boolean, status?: string) {
  const params = new URLSearchParams()
  if (mine !== undefined) params.set('mine', String(mine))
  if (status) params.set('status', status)
  return get<WfWorkflow[]>(`/v1/ai/ws/workflows?${params.toString()}`)
}

export function getWorkflowApi(id: number) {
  return get<WfWorkflow>(`/v1/ai/ws/workflows/${id}`)
}

export function updateWorkflowApi(id: number, data: { name?: string; description?: string; graph?: string }) {
  return put<void>(`/v1/ai/ws/workflows/${id}`, data)
}

export function deleteWorkflowApi(id: number) {
  return del<void>(`/v1/ai/ws/workflows/${id}`)
}

export function publishWorkflowApi(id: number) {
  return post<void>(`/v1/ai/ws/workflows/${id}/publish`, {})
}

export function offlineWorkflowApi(id: number) {
  return post<void>(`/v1/ai/ws/workflows/${id}/offline`, {})
}

// ==================== 流程实例 ====================

export function startInstanceApi(workflowId: number) {
  return post<WfInstance>(`/v1/ai/ws/workflows/${workflowId}/start`, {})
}

export function listInstancesApi(workflowId?: number, status?: string) {
  const params = new URLSearchParams()
  if (workflowId) params.set('workflowId', String(workflowId))
  if (status) params.set('status', status)
  return get<WfInstance[]>(`/v1/ai/ws/instances?${params.toString()}`)
}

export function getInstanceApi(id: number) {
  return get<{ instance: WfInstance; nodes: WfInstanceNode[] }>(`/v1/ai/ws/instances/${id}`)
}

export function approveNodeApi(instanceId: number, nodeKey: string, pass: boolean, comment?: string) {
  return post<void>(`/v1/ai/ws/instances/${instanceId}/nodes/${nodeKey}/approve`, { pass, comment })
}

export function retryNodeApi(instanceId: number, nodeKey: string) {
  return post<void>(`/v1/ai/ws/instances/${instanceId}/nodes/${nodeKey}/retry`, {})
}

export function cancelInstanceApi(instanceId: number) {
  return post<void>(`/v1/ai/ws/instances/${instanceId}/cancel`, {})
}
