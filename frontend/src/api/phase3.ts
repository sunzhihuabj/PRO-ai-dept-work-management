import { get, post, request } from '@/utils/request'
import type { PageResult } from '@/utils/request'

// ===== Phase 3 四域 API（T-310 统一补齐）=====

// ===== W5 变更域（T-302）=====
export interface EcnChange {
  id: number
  ecnNo: string
  projectId: number
  title: string
  reason?: string
  changeType: string
  impactLevel: string
  status: string
  applicant?: string
  approver?: string
  approvedAt?: string
  implementedAt?: string
  remark?: string
  createTime?: string
}
export interface EcnRecallTask {
  id: number
  ecnId: number
  projectId: number
  itemType: string
  itemDesc: string
  owner?: string
  status: string
  dueDate?: string
  recalledAt?: string
}

export const ECN_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿', PENDING_APPROVAL: '待审批', APPROVED: '已批准',
  REJECTED: '已驳回', IMPLEMENTED: '已实施', CLOSED: '已关闭'
}
export const ECN_STATUS_TYPE: Record<string, any> = {
  DRAFT: 'info', PENDING_APPROVAL: 'warning', APPROVED: 'primary',
  REJECTED: 'danger', IMPLEMENTED: 'success', CLOSED: 'info'
}

export const ECN_RECALL_LABEL: Record<string, string> = {
  PENDING: '待回收', RECALLED: '已回收', CONFIRMED: '已确认'
}

export function listEcnsApi(pageNum: number, pageSize: number, projectId?: number, status?: string) {
  return get<PageResult<EcnChange>>('/v1/ecn', { pageNum, pageSize, projectId, status })
}
export function createEcnApi(data: { projectId: number; title: string; reason?: string; changeType?: string; impactLevel?: string; originSchemeId?: number }) {
  return post<number>('/v1/ecn', data)
}
export function submitEcnApi(id: number) {
  return post(`/v1/ecn/${id}/submit`, {})
}
export function approveEcnApi(id: number, approved: boolean, approver?: string, remark?: string) {
  return post(`/v1/ecn/${id}/approve`, { approved, approver, remark })
}
export function implementEcnApi(id: number, data: { newSchemeId?: number; recallOwner?: string; recallDueDays?: number }) {
  return post<Record<string, any>>(`/v1/ecn/${id}/implement`, data)
}
export function listRecallTasksApi(ecnId?: number, status?: string) {
  return get<EcnRecallTask[]>('/v1/ecn/recall-tasks', { ecnId, status })
}
export function confirmRecallApi(id: number) {
  return post(`/v1/ecn/recall-tasks/${id}/confirm`, {})
}

// ===== W7 成本域（T-303）=====
export interface CostLedger {
  id: number
  projectId: number
  costType: string
  sourceType: string
  sourceId?: number
  amount: number
  occurredDate?: string
  remark?: string
}
export interface PriceModel {
  id: number
  projectType: number
  avgMargin: number
  minMargin: number
  sampleCount: number
  version: number
  updatedAt?: string
}

export const COST_TYPE_LABEL: Record<string, string> = {
  MATERIAL: '物料', LABOR: '人工', EXPENSE: '费用'
}

export function setStandardPriceApi(materialCode: string, price: number) {
  return post('/v1/cost/standard-price', { materialCode, price })
}
export function collectCostApi(projectId: number) {
  return request<Record<string, any>>({ method: 'POST', url: '/v1/cost/collect', params: { projectId } })
}
export function addCostApi(data: { projectId: number; costType: string; amount: number; remark?: string }) {
  return post<number>('/v1/cost/ledger', data)
}
export function listLedgerApi(projectId: number) {
  return get<CostLedger[]>('/v1/cost/ledger', { projectId })
}
export function settleApi(projectId: number) {
  return request<Record<string, any>>({ method: 'POST', url: '/v1/cost/settle', params: { projectId } })
}
export function confirmSettlementApi(id: number) {
  return post(`/v1/cost/settlements/${id}/confirm`, {})
}
export function refreshPriceModelApi() {
  return post<PriceModel[]>('/v1/cost/price-model/refresh', {})
}
export function listPriceModelApi() {
  return get<PriceModel[]>('/v1/cost/price-model')
}

// ===== W8 售后域（T-304）=====
export interface RepairTicket {
  id: number
  ticketNo: string
  projectId: number
  faultDesc: string
  faultTime?: string
  level: string
  responseSla?: string
  status: string
  reporter?: string
  handler?: string
  rootCause?: string
  solution?: string
  closedAt?: string
}
export interface InspectionRecord {
  id: number
  projectId: number
  inspectionType: string
  inspectionDate?: string
  inspector?: string
  content?: string
  issues?: string
}
export interface WarrantyInfo {
  id: number
  projectId: number
  warrantyEnd: string
  depositAmount?: number
  reminded?: number
  status: string
}

export const REPAIR_LEVEL_LABEL: Record<string, string> = {
  EMERGENCY: '紧急', GENERAL: '一般', INQUIRY: '咨询'
}
export const REPAIR_LEVEL_TYPE: Record<string, any> = {
  EMERGENCY: 'danger', GENERAL: 'warning', INQUIRY: 'info'
}
export const REPAIR_STATUS_LABEL: Record<string, string> = {
  OPEN: '待响应', PROCESSING: '处理中', CLOSED: '已闭环'
}

export function createRepairApi(data: { projectId: number; faultDesc: string; reporter?: string }) {
  return post<RepairTicket>('/v1/aftermarket/repairs', data)
}
export function listRepairsApi(projectId?: number, level?: string, status?: string) {
  return get<PageResult<RepairTicket>>('/v1/aftermarket/repairs', { projectId, level, status })
}
export function startRepairApi(id: number, handler: string) {
  return post(`/v1/aftermarket/repairs/${id}/start`, { handler })
}
export function closeRepairApi(id: number, data: { rootCause: string; solution: string }) {
  return post(`/v1/aftermarket/repairs/${id}/close`, data)
}
export function diagnosisApi(id: number) {
  return get<Record<string, any>>(`/v1/aftermarket/repairs/${id}/diagnosis`)
}
export function classifyApi(faultDesc: string) {
  return get<Record<string, any>>('/v1/aftermarket/classify', { faultDesc })
}
export function addInspectionApi(data: { projectId: number; inspectionType: string; inspector: string; content?: string; issues?: string }) {
  return post<InspectionRecord>('/v1/aftermarket/inspections', data)
}
export function listInspectionsApi(projectId?: number) {
  return get<PageResult<InspectionRecord>>('/v1/aftermarket/inspections', { projectId })
}
export function addWarrantyApi(data: { projectId: number; warrantyEnd: string; depositAmount?: number }) {
  return post<WarrantyInfo>('/v1/aftermarket/warranties', data)
}
export function listWarrantiesApi(projectId?: number) {
  return get<PageResult<WarrantyInfo>>('/v1/aftermarket/warranties', { projectId })
}
export function monthlyReportApi(month: string) {
  return get<Record<string, any>>('/v1/aftermarket/monthly-report', { month })
}

// ===== M3 档案域（T-305）=====
export interface ArchiveInfo {
  id: number
  archiveNo: string
  projectId: number
  archiveType: string
  title: string
  medium: string
  location?: string
  status: string
  remark?: string
  createTime?: string
}
export interface ArchiveBorrow {
  id: number
  archiveId: number
  borrower: string
  purpose?: string
  borrowDate: string
  dueDate: string
  returnDate?: string
  status: string
  approveBy?: string
}

export const ARCHIVE_STATUS_LABEL: Record<string, string> = {
  NORMAL: '在库', BORROWED: '借出', DESTROYED: '已销毁'
}
export const ARCHIVE_STATUS_TYPE: Record<string, any> = {
  NORMAL: 'success', BORROWED: 'warning', DESTROYED: 'info'
}
export const BORROW_STATUS_LABEL: Record<string, string> = {
  BORROWED: '借出', RETURNED: '已归还', OVERDUE: '逾期'
}
export const MEDIUM_LABEL: Record<string, string> = {
  ELECTRONIC: '电子', PAPER: '纸质', DUAL: '双轨'
}

export function registerArchiveApi(data: { projectId: number; archiveType: string; title: string; medium: string; location?: string; remark?: string }) {
  return post<number>('/v1/archive', data)
}
export function listArchivesApi(pageNum: number, pageSize: number, projectId?: number, status?: string) {
  return get<PageResult<ArchiveInfo>>('/v1/archive', { pageNum, pageSize, projectId, status })
}
export function borrowArchiveApi(id: number, data: { borrower: string; purpose?: string; dueDays?: number; approveBy?: string }) {
  return post<number>(`/v1/archive/${id}/borrow`, data)
}
export function returnArchiveApi(borrowId: number) {
  return post(`/v1/archive/borrows/${borrowId}/return`, {})
}
export function listBorrowsApi(archiveId: number) {
  return get<ArchiveBorrow[]>('/v1/archive/borrows', { archiveId })
}
export function overdueBorrowsApi() {
  return get<ArchiveBorrow[]>('/v1/archive/borrows/overdue')
}
export function destroyArchiveApi(id: number, remark: string) {
  return post(`/v1/archive/${id}/destroy`, { remark })
}
