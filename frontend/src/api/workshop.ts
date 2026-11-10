import { get, post } from '@/utils/request'
import type { PageResult } from '@/utils/request'

// ===== W4 样机域（T-201~T-203）=====

export interface WorkOrder {
  id: number
  woNo: string
  projectId: number
  orderType: 'PROTOTYPE' | 'BATCH'
  qty: number
  status: 'DRAFT' | 'STARTED' | 'CLOSED'
  startedAt?: string
  closedAt?: string
  remark?: string
  createTime?: string
}

export interface KittingCheck {
  id: number
  projectId: number
  kitRate: number
  result: 'PASS' | 'BLOCK'
  detailJson?: string
  checker?: string
  createTime?: string
}

export interface PurchaseOrder {
  id: number
  poNo: string
  projectId: number
  materialCode: string
  materialName?: string
  qty: number
  arrivedQty: number
  supplier?: string
  expectedDate?: string
  status: 'DRAFT' | 'SUBMITTED' | 'CLOSED'
  createTime?: string
}

export interface DisclosureRecord {
  id: number
  projectId: number
  workorderId?: number
  discloser: string
  receivers?: string
  signed: number
  content?: string
  createTime?: string
}

/** 工单分页 */
export function listWorkOrdersApi(pageNum: number, pageSize: number, projectId?: number, status?: string) {
  return get('/v1/workshop/work-orders', { pageNum, pageSize, projectId, status })
}

export function createWorkOrderApi(data: { projectId: number; orderType?: string; qty?: number; remark?: string }) {
  return post('/v1/workshop/work-orders', data)
}

export function startWorkOrderApi(id: number) {
  return post(`/v1/workshop/work-orders/${id}/start`, {})
}

export function closeWorkOrderApi(id: number) {
  return post(`/v1/workshop/work-orders/${id}/close`, {})
}

/** 齐套审查历史 */
export function listKittingApi(projectId: number) {
  return get('/v1/workshop/kitting', { projectId })
}

/** PO 分页 */
export function listPosApi(pageNum: number, pageSize: number, projectId?: number) {
  return get('/v1/workshop/pos', { pageNum, pageSize, projectId })
}

export function createPoApi(data: {
  projectId: number
  materialCode: string
  materialName?: string
  qty: number
  supplier?: string
  expectedDate?: string
}) {
  return post('/v1/workshop/pos', data)
}

/** 到货登记 */
export function registerArrivalApi(poId: number, qty: number, remark?: string) {
  return post(`/v1/workshop/pos/${poId}/arrivals`, { qty, remark })
}

/** 交底记录 */
export function listDisclosuresApi(projectId: number) {
  return get('/v1/workshop/disclosures', { projectId })
}

export function createDisclosureApi(data: DisclosureRecord) {
  return post('/v1/workshop/disclosures', data)
}

export function signDisclosureApi(id: number) {
  return post(`/v1/workshop/disclosures/${id}/sign`, {})
}

/** 验收登记 */
export function completeAcceptanceApi(data: { workorderId: number; result: string; acceptor?: string; comment?: string }) {
  return post('/v1/workshop/acceptances', data)
}

// ===== 扫码过账（T-204）=====

export interface InventoryRecord {
  id: number
  recordNo: string
  projectId: number
  workorderId?: number
  poId?: number
  materialCode: string
  materialName?: string
  direction: 'IN' | 'OUT'
  qty: number
  barcode?: string
  source: string
  operator?: string
  scannedAt?: string
  tplusStatus: 'PENDING' | 'PUSHED' | 'FAILED'
  tplusPushTime?: string
  tplusVoucherNo?: string
}

export interface TplusPushQueueItem {
  id: number
  recordId: number
  recordNo: string
  status: 'PENDING' | 'PUSHED' | 'FAILED'
  retryCount: number
  pushedAt?: string
  errorMsg?: string
  createTime?: string
}

export interface TplusReconciliation {
  id: number
  batchNo: string
  reconcileDate: string
  localCount: number
  tplusCount: number
  matchedCount: number
  diffCount: number
  diffJson?: string
  createTime?: string
}

/** 扫码过账 */
export function scanPostingApi(data: {
  poId?: number
  workorderId?: number
  materialCode: string
  materialName?: string
  direction: 'IN' | 'OUT'
  qty: number
  barcode?: string
  operator?: string
  remark?: string
}) {
  return post('/v1/inventory/scan', data)
}

/** 出入库记录分页 */
export function listInventoryRecordsApi(pageNum: number, pageSize: number, projectId?: number, direction?: string, tplusStatus?: string) {
  return get('/v1/inventory/records', { pageNum, pageSize, projectId, direction, tplusStatus })
}

/** 处理 T+ 推送队列 */
export function processQueueApi() {
  return post('/v1/inventory/queue/process', {})
}

/** 推送队列分页 */
export function listQueueApi(pageNum: number, pageSize: number, status?: string) {
  return get('/v1/inventory/queue', { pageNum, pageSize, status })
}

/** T+ 对账 */
export function reconcileApi() {
  return post('/v1/inventory/reconcile', {})
}

/** 对账批次列表 */
export function listReconciliationsApi() {
  return get('/v1/inventory/reconciliations')
}
