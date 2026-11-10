import { get, post } from '@/utils/request'
import type { PageResult } from '@/utils/request'

// ===== W6 交付域（T-206~T-207）=====

export interface Shipment {
  id: number
  shipmentNo: string
  projectId: number
  workorderId?: number
  batchNo?: string
  qty: number
  carrier?: string
  status: 'DRAFT' | 'SUBMITTED'
  shippedAt?: string
  remark?: string
  createTime?: string
}

export interface LogisticsRecord {
  id: number
  shipmentId: number
  eventType: 'DISPATCH' | 'TRANSIT' | 'ARRIVED' | 'INSTALL'
  eventTime?: string
  location?: string
  description?: string
  operator?: string
  createTime?: string
}

export interface ConstructionLog {
  id: number
  projectId: number
  logDate: string
  weather?: string
  personnelCount?: number
  workContent?: string
  progressPct: number
  issues?: string
  recorder?: string
  createTime?: string
}

export interface Acceptance {
  id: number
  projectId: number
  stage: 'FIRST' | 'MID' | 'LATE' | 'FINAL'
  status: 'TRIGGERED' | 'PENDING_SIGN' | 'PASS' | 'FAIL'
  progressPct?: number
  acceptorOwner?: string
  acceptorSupervisor?: string
  acceptorClient?: string
  signedOwner: number
  signedSupervisor: number
  signedClient: number
  acceptedAt?: string
  comment?: string
  triggeredBy?: string
  createTime?: string
}

/** 发货单分页 */
export function listShipmentsApi(pageNum: number, pageSize: number, projectId?: number, status?: string) {
  return get('/v1/delivery/shipments', { pageNum, pageSize, projectId, status })
}

export function createShipmentApi(data: {
  projectId: number
  workorderId?: number
  batchNo?: string
  qty?: number
  carrier?: string
  remark?: string
}) {
  return post('/v1/delivery/shipments', data)
}

/** 提交发运（BR-02：整机厂首次发货必须持有 PASS 的 FAI 报告） */
export function submitShipmentApi(id: number) {
  return post(`/v1/delivery/shipments/${id}/submit`, {})
}

/** 登记物流事件 */
export function addLogisticsApi(shipmentId: number, data: { eventType: string; location?: string; description?: string }) {
  return post(`/v1/delivery/shipments/${shipmentId}/logistics`, data)
}

export function listLogisticsApi(shipmentId: number) {
  return get('/v1/delivery/logistics', { shipmentId })
}

/** 录入施工日志（返回项目当前累计工程量） */
export function addConstructionLogApi(data: {
  projectId: number
  logDate?: string
  weather?: string
  personnelCount?: number
  workContent?: string
  progressPct: number
  issues?: string
  recorder?: string
}) {
  return post('/v1/delivery/construction-logs', data)
}

export function listConstructionLogsApi(projectId: number) {
  return get('/v1/delivery/construction-logs', { projectId })
}

export function getProgressApi(projectId: number) {
  return get('/v1/delivery/progress', { projectId })
}

/** 验收单列表 */
export function listAcceptancesApi(projectId?: number, stage?: string, status?: string) {
  return get('/v1/delivery/acceptances', { projectId, stage, status })
}

/** 手动创建验收单 */
export function createAcceptanceApi(data: { projectId: number; stage: string; progressPct?: number; comment?: string }) {
  return post('/v1/delivery/acceptances', data)
}

/** 工程量验收触发检查（BR-03 30% 中期 / BR-04 85% 后期） */
export function checkAcceptanceApi(projectId: number) {
  return post(`/v1/delivery/acceptances/check?projectId=${projectId}`, {})
}

/** 验收单签字（OWNER/SUPERVISOR/CLIENT，BR-05 三方齐备生效） */
export function signAcceptanceApi(id: number, data: { party: string; acceptor?: string; comment?: string }) {
  return post(`/v1/delivery/acceptances/${id}/sign`, data)
}
