import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface ProjectItem {
  id: number
  projectCode: string
  projectName: string
  projectType: number
  customerName?: string
  projectLeader?: string
  status: number
  contractAmount?: number
  targetCost?: number
  planStartDate?: string
  planEndDate?: string
  description?: string
  createBy?: string
  createTime?: string
}

export interface ProjectListParams {
  pageNum?: number
  pageSize?: number
  name?: string
  status?: number
  projectType?: number
}

export interface ProjectDTO {
  projectName: string
  projectType: number
  customerName: string
  projectLeader?: string
  contractAmount?: number
  targetCost?: number
  planStartDate: string
  planEndDate: string
  description?: string
}

export interface ProjectPhase {
  id: number
  projectId: number
  phaseName: string
  phaseOrder: number
  phaseType?: number
  planStartDate?: string
  planEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  status: number
  description?: string
}

export function getProjectListApi(params: ProjectListParams) {
  return get<PageResult<ProjectItem>>('/v1/projects', params)
}

export function getProjectDetailApi(id: number) {
  return get<ProjectItem>(`/v1/projects/${id}`)
}

export function createProjectApi(data: ProjectDTO) {
  return post<number>('/v1/projects', data)
}

export function updateProjectApi(id: number, data: ProjectDTO) {
  return put(`/v1/projects/${id}`, data)
}

export function deleteProjectApi(id: number) {
  return del(`/v1/projects/${id}`)
}

export function getProjectPhasesApi(id: number) {
  return get<ProjectPhase[]>(`/v1/projects/${id}/phases`)
}

export function updatePhaseStatusApi(phaseId: number, status: number) {
  return put(`/v1/projects/phases/${phaseId}/status`, { status })
}

// ---------- 项目成员 ----------
export interface ProjectMember {
  id: number
  projectId: number
  userId: number
  roleType?: string
  joinTime?: string
  status?: number
}

export function listMembersApi(projectId: number) {
  return get<ProjectMember[]>(`/v1/projects/${projectId}/members`)
}

export function addMemberApi(projectId: number, userId: number, roleType?: string) {
  return post<ProjectMember>(`/v1/projects/${projectId}/members`, { userId, roleType })
}

export function removeMemberApi(memberId: number) {
  return del(`/v1/projects/members/${memberId}`)
}

// ---------- 里程碑 ----------
export interface Milestone {
  id: number
  projectId: number
  phaseId?: number
  milestoneName: string
  planDate?: string
  actualDate?: string
  status?: number
  description?: string
}

export function listMilestonesApi(projectId: number) {
  return get<Milestone[]>(`/v1/projects/${projectId}/milestones`)
}

export function createMilestoneApi(projectId: number, data: Partial<Milestone>) {
  return post<number>(`/v1/projects/${projectId}/milestones`, data)
}

export function updateMilestoneApi(id: number, data: Partial<Milestone>) {
  return put(`/v1/projects/milestones/${id}`, data)
}

export function markMilestoneApi(id: number, status: number) {
  return post(`/v1/projects/milestones/${id}/mark?status=${status}`)
}

export function deleteMilestoneApi(id: number) {
  return del(`/v1/projects/milestones/${id}`)
}

// ---------- 交付物 ----------
export interface DeliverableTemplate {
  id: number
  templateName: string
  projectType?: number
  phaseOrder?: number
  phaseName?: string
  fileType?: string
  required?: number
  description?: string
}

export interface DeliverableChecklist {
  id: number
  projectId: number
  phaseId?: number
  templateId?: number
  deliverableName: string
  required?: number
  fileType?: string
  uploaded?: number
  fileId?: number
  checked?: number
  checkTime?: string
  checkBy?: string
}

export function listDeliverableTemplatesApi(projectType?: number) {
  return get<DeliverableTemplate[]>('/v1/deliverables/templates', { projectType })
}

export function generateDeliverablesApi(projectId: number) {
  return post<{ created: number }>(`/v1/deliverables/projects/${projectId}/generate`)
}

export function listDeliverablesApi(projectId: number) {
  return get<DeliverableChecklist[]>(`/v1/deliverables/projects/${projectId}`)
}

export function deliverableStatsApi(projectId: number) {
  return get<{ total: number; uploaded: number; checked: number; requiredTotal: number; requiredMissing: number }>(
    `/v1/deliverables/projects/${projectId}/stats`
  )
}

export function bindDeliverableFileApi(id: number, fileId: number) {
  return put<DeliverableChecklist>(`/v1/deliverables/${id}/bind-file?fileId=${fileId}`)
}

export function checkDeliverableApi(id: number) {
  return put<DeliverableChecklist>(`/v1/deliverables/${id}/check`)
}

export function deleteDeliverableApi(id: number) {
  return del(`/v1/deliverables/${id}`)
}

// ---------- 阶段门禁 ----------
export interface PhaseGateItem {
  id: number
  projectId: number
  phaseId?: number
  gateName: string
  gateOrder?: number
  passCondition?: string
  auditResult?: number
  auditBy?: string
  auditTime?: string
  auditComment?: string
}

export function listGatesApi(projectId: number) {
  return get<PhaseGateItem[]>(`/v1/gates`, { projectId })
}

export function createGateApi(projectId: number, data: Partial<PhaseGateItem>) {
  return post<number>(`/v1/gates`, { ...data, projectId })
}

export function auditGateApi(id: number, approved: boolean, comment?: string) {
  return post<PhaseGateItem>(`/v1/gates/${id}/audit`, { approved, comment })
}

export function deleteGateApi(id: number) {
  return del(`/v1/gates/${id}`)
}
