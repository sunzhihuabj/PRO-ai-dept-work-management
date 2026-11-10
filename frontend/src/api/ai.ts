import { post } from '@/utils/request'

export interface AiSummaryParams {
  text: string
  type?: 'daily' | 'weekly' | 'meeting' | 'generic'
}

export interface AiSummaryResult {
  summary: string
}

/** 生成摘要 */
export function summarizeApi(data: AiSummaryParams) {
  return post<AiSummaryResult>('/v1/ai/summary', data)
}
