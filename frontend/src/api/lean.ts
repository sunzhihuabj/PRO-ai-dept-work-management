import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface ImprovementProposal {
  id: number
  projectId?: number
  title: string
  proposalType?: string
  proposerId?: number
  currentState?: string
  targetState?: string
  proposalContent?: string
  status?: number
  reviewResult?: number
  reviewComment?: string
  createTime?: string
}

export interface RootCauseAnalysis {
  id: number
  projectId?: number
  problemDesc: string
  analysisMethod?: string
  findings?: string
  rootCause?: string
  correctiveAction?: string
  preventiveAction?: string
  status?: number
  createBy?: string
  createTime?: string
}

export interface LessonsLearned {
  id: number
  projectId: number
  phaseId?: number
  title: string
  category?: string
  content?: string
  impactLevel?: string
  keywords?: string
  createBy?: string
  createTime?: string
}

export const PROPOSAL_STATUS = ['待审核', '审核通过', '实施中', '已完成', '已驳回'] as const
export const RCA_STATUS = ['分析中', '已完成', '已关闭'] as const
export const RCA_METHODS = ['5Why', '鱼骨图', '故障树', 'FMEA'] as const
export const LESSON_CATEGORIES = ['成功经验', '失败教训', '改进建议'] as const

export function listProposalsApi(params: {
  pageNum?: number; pageSize?: number; projectId?: number
  status?: number; proposalType?: string; keyword?: string
}) {
  return get<PageResult<ImprovementProposal>>('/v1/lean/proposals', params)
}

export function createProposalApi(data: Partial<ImprovementProposal>) {
  return post<number>('/v1/lean/proposals', data)
}

export function updateProposalApi(id: number, data: Partial<ImprovementProposal>) {
  return put(`/v1/lean/proposals/${id}`, data)
}

export function deleteProposalApi(id: number) {
  return del(`/v1/lean/proposals/${id}`)
}

export function reviewProposalApi(id: number, approved: boolean, comment?: string) {
  return post(`/v1/lean/proposals/${id}/review`, { approved, comment })
}

export function advanceProposalApi(id: number) {
  return post(`/v1/lean/proposals/${id}/advance`)
}

export function listRcasApi(params: {
  pageNum?: number; pageSize?: number; projectId?: number
  analysisMethod?: string; status?: number
}) {
  return get<PageResult<RootCauseAnalysis>>('/v1/lean/rcas', params)
}

export function createRcaApi(data: Partial<RootCauseAnalysis>) {
  return post<number>('/v1/lean/rcas', data)
}

export function updateRcaApi(id: number, data: Partial<RootCauseAnalysis>) {
  return put(`/v1/lean/rcas/${id}`, data)
}

export function advanceRcaApi(id: number, targetStatus: number) {
  return post(`/v1/lean/rcas/${id}/advance?targetStatus=${targetStatus}`)
}

export function deleteRcaApi(id: number) {
  return del(`/v1/lean/rcas/${id}`)
}

export function listLessonsApi(params: {
  pageNum?: number; pageSize?: number; projectId?: number
  category?: string; keyword?: string
}) {
  return get<PageResult<LessonsLearned>>('/v1/lean/lessons', params)
}

export function createLessonApi(data: Partial<LessonsLearned>) {
  return post<number>('/v1/lean/lessons', data)
}

export function updateLessonApi(id: number, data: Partial<LessonsLearned>) {
  return put(`/v1/lean/lessons/${id}`, data)
}

export function deleteLessonApi(id: number) {
  return del(`/v1/lean/lessons/${id}`)
}

// ---------- A3 报告 ----------
export interface A3Report {
  id: number
  projectId?: number
  proposalId?: number
  title: string
  background?: string
  currentCondition?: string
  goal?: string
  rootCause?: string
  countermeasures?: string
  checkResult?: string
  followUp?: string
  status?: number
  createBy?: string
  createTime?: string
}

export const A3_STATUS = ['草稿', '进行中', '已完成', '已关闭'] as const

export function listA3Api(params: { pageNum?: number; pageSize?: number; projectId?: number; status?: number }) {
  return get<PageResult<A3Report>>('/v1/lean/a3-reports', params)
}
export function createA3Api(data: Partial<A3Report>) {
  return post<number>('/v1/lean/a3-reports', data)
}
export function updateA3Api(id: number, data: Partial<A3Report>) {
  return put(`/v1/lean/a3-reports/${id}`, data)
}
export function advanceA3Api(id: number, targetStatus: number) {
  return post(`/v1/lean/a3-reports/${id}/advance?targetStatus=${targetStatus}`)
}
export function deleteA3Api(id: number) {
  return del(`/v1/lean/a3-reports/${id}`)
}

// ---------- 价值流记录 ----------
export interface ValueStreamRecord {
  id: number
  projectId: number
  phaseId?: number
  recordDate?: string
  processName?: string
  processType?: string
  cycleTime?: number
  valueAddedTime?: number
  nonValueAddedTime?: number
  leadTime?: number
  efficiencyRate?: number
  description?: string
}

export function listValueStreamApi(params: { pageNum?: number; pageSize?: number; projectId?: number }) {
  return get<PageResult<ValueStreamRecord>>('/v1/lean/value-streams', params)
}
export function saveValueStreamApi(data: Partial<ValueStreamRecord>) {
  return post<number>('/v1/lean/value-streams', data)
}
export function deleteValueStreamApi(id: number) {
  return del(`/v1/lean/value-streams/${id}`)
}

// ---------- 浪费记录 ----------
export interface WasteRecord {
  id: number
  projectId: number
  wasteType?: string
  wasteDesc?: string
  wasteSource?: string
  severity?: number
  status?: number
  improvementAction?: string
  handlerId?: number
  createTime?: string
}

export const WASTE_TYPES = ['等待', '缺陷', '搬运', '过度加工', '库存', '动作', '过量生产'] as const
export const WASTE_STATUS = ['未处理', '处理中', '已消除', '已关闭'] as const

export function listWasteApi(params: {
  pageNum?: number; pageSize?: number; projectId?: number
  wasteType?: string; status?: number
}) {
  return get<PageResult<WasteRecord>>('/v1/lean/wastes', params)
}
export function saveWasteApi(data: Partial<WasteRecord>) {
  return post<number>('/v1/lean/wastes', data)
}
export function advanceWasteApi(id: number, targetStatus: number) {
  return post(`/v1/lean/wastes/${id}/advance?targetStatus=${targetStatus}`)
}
export function deleteWasteApi(id: number) {
  return del(`/v1/lean/wastes/${id}`)
}
