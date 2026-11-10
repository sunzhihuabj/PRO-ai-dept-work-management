import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface ProjectBonus {
  id: number
  projectId: number
  userId: number
  bonusType?: number
  projectPeriod?: number
  baseAmount?: number
  coefficient?: number
  monthlyAmount?: number
  yearlyAmount?: number
  paidMonthly?: number
  paidYearly?: number
  status?: number
  createTime?: string
}

export interface BonusRecord {
  id: number
  bonusId: number
  projectId: number
  userId: number
  payType?: number
  payAmount?: number
  payPeriod?: string
  payTime?: string
  remark?: string
}

export interface BonusSaveDTO {
  projectId: number
  userId: number
  bonusType: number
  projectPeriod?: number
  baseAmount: number
  coefficient?: number
}

export function listBonusApi(params: {
  pageNum?: number
  pageSize?: number
  projectId?: number
  userId?: number
  bonusType?: number
  status?: number
}) {
  return get<PageResult<ProjectBonus>>('/v1/bonus', params)
}

export function createBonusApi(data: BonusSaveDTO) {
  return post<number>('/v1/bonus', data)
}

export function updateBonusApi(id: number, data: BonusSaveDTO) {
  return put(`/v1/bonus/${id}`, data)
}

export function deleteBonusApi(id: number) {
  return del(`/v1/bonus/${id}`)
}

export function applyKpiApi(id: number, period: string) {
  return put<ProjectBonus>(`/v1/bonus/${id}/apply-kpi?period=${encodeURIComponent(period)}`)
}

export function payBonusApi(id: number, data: { payType: number; payPeriod: string; remark?: string }) {
  return post<BonusRecord>(`/v1/bonus/${id}/pay`, data)
}

export function listBonusRecordsApi(params: {
  pageNum?: number
  pageSize?: number
  projectId?: number
  userId?: number
  payType?: number
  payPeriod?: string
}) {
  return get<PageResult<BonusRecord>>('/v1/bonus/records', params)
}
