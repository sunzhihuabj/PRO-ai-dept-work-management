import { get, post, put, del } from '@/utils/request'

export interface AiProviderConfig {
  id?: number
  providerCode: string
  providerName: string
  providerType: 'OPENAI_COMPAT' | 'MOCK' | 'OLLAMA'
  endpoint?: string
  model?: string
  apiKey?: string
  apiKeyHint?: string
  priority?: number
  enabled?: number
  rpmLimit?: number
  dailyLimit?: number
  remark?: string
}

export interface TestResult {
  success: boolean
  message: string
  replyPreview?: string
  durationMs?: number
}

/** 通道配置列表（Key 脱敏回显） */
export function getAiConfigsApi() {
  return get('/v1/ai-configs')
}

/** 创建配置（api_key 只写不读） */
export function createAiConfigApi(data: AiProviderConfig) {
  return post('/v1/ai-configs', data)
}

/** 更新配置（apiKey 留空 = 不修改） */
export function updateAiConfigApi(id: number, data: AiProviderConfig) {
  return put(`/v1/ai-configs/${id}`, data)
}

/** 删除配置 */
export function deleteAiConfigApi(id: number) {
  return del(`/v1/ai-configs/${id}`)
}

/** 测试连接 */
export function testAiConfigApi(id: number) {
  return post(`/v1/ai-configs/${id}/test`, {})
}

/** 探测结果（结构化契约，与网关 ModelProbeService 输出一致） */
export interface ProbeResult {
  success: boolean
  protocol?: string
  endpointUsed?: string
  count: number
  durationMs?: number
  models?: { id: string; ownedBy?: string; contextLength?: number; parameterSize?: string }[]
  cached?: boolean
  errorType?: string | null
  errorMessage?: string | null
  note?: string | null
}

/** 探测模型列表（未保存配置：按 API 地址 + Key 主动拉取可用模型） */
export function probeModelsApi(data: { providerType: string; endpoint: string; apiKey?: string }) {
  return post('/v1/ai-configs/probe-models', data)
}

/** 探测模型列表（已保存通道：服务端解密 Key） */
export function listChannelModelsApi(id: number) {
  return get(`/v1/ai-configs/${id}/models`)
}
