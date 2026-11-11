import { get, post } from '@/utils/request'
import type { PageResult } from '@/utils/request'

// ===== W1 投标域（T-301）=====

export interface TenderNotice {
  id: number
  tenderNo: string
  title: string
  platform?: string
  budget?: number
  deposit?: number
  qualificationReq?: string
  deadline?: string
  publishDate?: string
  status: string
  analysisJson?: string
  importSource?: string
  rawText?: string
  remark?: string
  createTime?: string
}

export interface QualificationCheck {
  id?: number
  tenderId: number
  requirement: string
  companyStatus: 'SATISFIED' | 'NOT_SATISFIED' | 'UNCERTAIN'
  evidence?: string
  checkedBy?: string
}

export interface TenderBid {
  id: number
  tenderId: number
  price?: number
  costEstimate?: number
  grossMargin?: number
  strategyText?: string
  openDate?: string
  openResult?: string
}

export interface TenderCompetitor {
  id?: number
  tenderId: number
  competitorName: string
  price?: number
  note?: string
}

export const TENDER_STATUS_LABEL: Record<string, string> = {
  NEW: '新标讯', FOLLOWED: '跟进中', APPLIED: '已报名', PREPARED: '投标准备',
  DECIDED: '已决策', WON: '中标', LOST: '未中标'
}
export const TENDER_STATUS_TYPE: Record<string, any> = {
  NEW: 'warning', FOLLOWED: 'primary', APPLIED: 'primary', PREPARED: 'primary',
  DECIDED: 'info', WON: 'success', LOST: 'danger'
}

/** 标讯分页 */
export function listTendersApi(pageNum: number, pageSize: number, status?: string, keyword?: string) {
  return get('/v1/tender/notices', { pageNum, pageSize, status, keyword })
}

export function getTenderApi(id: number) {
  return get(`/v1/tender/notices/${id}`)
}

/** 手工建档标讯（AGT-01 自动解析走 Agent 工作台） */
export function createTenderApi(data: {
  title: string
  platform?: string
  budget?: number
  deposit?: number
  qualificationReq?: string
  deadline?: string
  rawText?: string
}) {
  return post('/v1/tender/notices', data)
}

/** 标讯状态推进（WON 触发 tender.won 事件） */
export function updateTenderStatusApi(id: number, status: string, remark?: string) {
  return post(`/v1/tender/notices/${id}/status`, { status, remark })
}

/** 资格比对（AGT-02 确定性引擎同步入口） */
export function checkQualificationApi(id: number) {
  return post(`/v1/tender/notices/${id}/qualification-check`, {})
}

export function listChecksApi(id: number) {
  return get(`/v1/tender/notices/${id}/qualification-check`)
}

/** 报价建议（AGT-04 同步入口） */
export function suggestPriceApi(id: number) {
  return post(`/v1/tender/notices/${id}/price-suggestion`, {})
}

/** 竞品分析（AGT-05 同步入口） */
export function analyzeCompetitorsApi(id: number) {
  return post(`/v1/tender/notices/${id}/competitor-analysis`, {})
}

/** 报价登记 */
export function saveBidApi(data: {
  tenderId: number
  price?: number
  costEstimate?: number
  strategyText?: string
  openDate?: string
  openResult?: string
}) {
  return post('/v1/tender/bids', data)
}

export function listBidsApi(tenderId?: number) {
  return get('/v1/tender/bids', { tenderId })
}

/** 竞品录入 */
export function addCompetitorApi(data: { tenderId: number; competitorName: string; price?: number; note?: string }) {
  return post('/v1/tender/competitors', data)
}

export function listCompetitorsApi(tenderId: number) {
  return get('/v1/tender/competitors', { tenderId })
}

/** AGT-01 标讯解析（Agent 任务，params 携带原文） */
export function importTenderApi(rawText: string) {
  return post('/v1/agent/tasks', { instruction: '解析标讯原文并建档', capabilityCode: 'AGT-01', params: { rawText } })
}
