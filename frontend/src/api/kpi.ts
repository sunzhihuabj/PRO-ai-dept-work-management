import { get, post, put, del, request } from '@/utils/request'

export interface KpiIndicator {
  id: number
  indicatorName: string
  indicatorCode: string
  projectType?: number
  indicatorType?: string
  baseScore?: number
  unit?: string
  description?: string
  status?: number
}

export interface KpiScore {
  id: number
  userId: number
  period?: string
  indicatorId?: number
  baseScore?: number
  adjustScore?: number
  finalScore?: number
  adjustReason?: string
  level?: string
  status?: number
}

export interface KpiWeight {
  id: number
  projectType?: number
  indicatorId?: number
  weight?: number
  period?: string
}

export interface KpiReportRow {
  userId: number
  userName: string
  totalScore: number
  level: string
  detailCount: number
}

export interface KpiReport {
  period: string
  userCount: number
  levelCount: Record<string, number>
  avgScore: number
  rows: KpiReportRow[]
}

export function getKpiIndicatorsApi(params?: { projectType?: number; status?: number; keyword?: string }) {
  return get<KpiIndicator[]>('/v1/kpi/indicators', params)
}

export function createKpiIndicatorApi(data: Partial<KpiIndicator>) {
  return post<number>('/v1/kpi/indicators', data)
}

export function updateKpiIndicatorApi(id: number, data: Partial<KpiIndicator>) {
  return put(`/v1/kpi/indicators/${id}`, data)
}

export function deleteKpiIndicatorApi(id: number) {
  return del(`/v1/kpi/indicators/${id}`)
}

export function getKpiWeightsApi(params?: { projectType?: number; period?: string }) {
  return get<KpiWeight[]>('/v1/kpi/weights', params)
}

export function saveKpiWeightsApi(data: { projectType?: number; period?: string; weights: Record<number, number> }) {
  return post('/v1/kpi/weights', data)
}

export function getKpiScoresApi(params?: { period?: string; userId?: number; indicatorId?: number; status?: number }) {
  return get<KpiScore[]>('/v1/kpi/scores', params)
}

export function calculateKpiApi(period: string, projectType?: number) {
  const qs = new URLSearchParams({ period, ...(projectType != null ? { projectType: String(projectType) } : {}) }).toString()
  return post<{ created: number }>(`/v1/kpi/scores/calculate?${qs}`)
}

export function adjustKpiScoreApi(id: number, data: { adjustScore: number; adjustReason: string }) {
  return put<KpiScore>(`/v1/kpi/scores/${id}/adjust`, data)
}

export function confirmKpiPeriodApi(period: string) {
  return put<{ count: number }>(`/v1/kpi/scores/confirm?period=${encodeURIComponent(period)}`)
}

export function getKpiReportApi(period: string, projectType?: number) {
  return get<KpiReport>('/v1/kpi/report', { period, projectType })
}

/** 导出报表 CSV（Blob，带 Authorization） */
export function exportKpiReportApi(period: string) {
  return request({ method: 'GET', url: `/v1/kpi/report/export?period=${encodeURIComponent(period)}`, responseType: 'blob' })
}
