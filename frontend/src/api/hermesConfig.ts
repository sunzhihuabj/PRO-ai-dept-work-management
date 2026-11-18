import { get, put } from '@/utils/request'

/** Hermes Agent 运行参数项 */
export interface HermesConfigItem {
  value: string
  description: string
}

/** Hermes 健康状态 */
export interface HermesHealth {
  hermesAvailable: boolean
  checkedAt: string
}

/** 读取全部运行参数（含说明） */
export function getHermesConfigApi() {
  return get<Record<string, HermesConfigItem>>('/v1/ai/hermes/config')
}

/** 保存运行参数（白名单键，即时生效） */
export function saveHermesConfigApi(items: Record<string, string>) {
  return put('/v1/ai/hermes/config', { items })
}

/** Hermes 服务健康探活 */
export function getHermesHealthApi() {
  return get<HermesHealth>('/v1/ai/hermes/health')
}
