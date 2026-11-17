import { get, post } from '@/utils/request'

/** Agent 决策建议（阶段一多 Agent 协同） */
export interface AgentDecisionAdvice {
  id?: number
  scene: string
  sourceType: string
  sourceId: number
  sourceBizNo?: string
  agentId?: string
  agentRole?: string
  title: string
  contentJson: string
  confidence?: number
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH'
  requiresApproval?: number
  approvalAction?: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED'
  evidenceJson?: string
  targetUserId?: number
  responseDeadline?: string
  approverId?: number
  approverComment?: string
  approvedAt?: string
  createTime?: string
}

/** 分页查询决策建议 */
export function pageAdvicesApi(params: {
  pageNum?: number
  pageSize?: number
  status?: string
  scene?: string
}) {
  return get('/v1/agent-advices', params)
}

/** 采纳建议（仅部门领导，后端 HMAC 校验 + 动作执行） */
export function approveAdviceApi(id: number, comment?: string) {
  return post(`/v1/agent-advices/${id}/approve`, { comment: comment || '' })
}

/** 驳回建议（仅部门领导） */
export function rejectAdviceApi(id: number, comment?: string) {
  return post(`/v1/agent-advices/${id}/reject`, { comment: comment || '' })
}

/** 解析建议内容 JSON */
export function parseAdviceContent(advice: AgentDecisionAdvice): {
  type: string
  body: string
  structured: Record<string, any>
} {
  try {
    const parsed = JSON.parse(advice.contentJson || '{}')
    return {
      type: parsed.type || 'INFO',
      body: parsed.body || '',
      structured: parsed.structured || {}
    }
  } catch {
    return { type: 'INFO', body: advice.contentJson || '', structured: {} }
  }
}
