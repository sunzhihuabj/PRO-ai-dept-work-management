import { get, post, put, del } from '@/utils/request'

/** 采集平台（凭据永不回显，仅 authConfigured 标识） */
export interface TenderPlatform {
  id?: number
  platformName: string
  loginUrl?: string
  listUrl: string
  detailUrlPrefix?: string
  authType: 'NONE' | 'FORM' | 'COOKIE'
  authParams?: Record<string, string>
  collectRules: string
  requestIntervalMs?: number
  jitterMs?: number
  maxPages?: number
  enabled?: number
  remark?: string
  ext?: Record<string, any>
}

/** 采集运行监控记录 */
export interface TenderCollectRun {
  id: number
  platformId: number
  platformName: string
  triggerType: string
  status: string
  loginStatus: string
  pagesFetched: number
  itemsFound: number
  itemsCollected: number
  extractOk: number
  extractRepaired: number
  extractFailed: number
  registerOk: number
  registerDup: number
  checkpoint?: string
  lastErrorType?: string
  errorSnapshot?: string
  startedAt?: string
  finishedAt?: string
}

export function pagePlatformsApi(params: { pageNum?: number; pageSize?: number; keyword?: string }) {
  return get('/v1/tender-platforms', params)
}

export function enabledPlatformsApi() {
  return get('/v1/tender-platforms/enabled')
}

export function createPlatformApi(data: TenderPlatform) {
  return post('/v1/tender-platforms', data)
}

export function updatePlatformApi(id: number, data: TenderPlatform) {
  return put(`/v1/tender-platforms/${id}`, data)
}

export function deletePlatformApi(id: number) {
  return del(`/v1/tender-platforms/${id}`)
}

export function togglePlatformApi(id: number, enabled: number) {
  return put(`/v1/tender-platforms/${id}/toggle?enabled=${enabled}`)
}

export function testLoginApi(id: number) {
  return post(`/v1/tender-platforms/${id}/test-login`)
}

export function triggerCollectApi(platformId: number, resume = false) {
  return post('/v1/tender-collect/run', { platformId, resume })
}

export function pageRunsApi(params: { pageNum?: number; pageSize?: number; platformId?: number; status?: string }) {
  return get('/v1/tender-collect/runs', params)
}

export function runDetailApi(id: number) {
  return get(`/v1/tender-collect/runs/${id}`)
}
