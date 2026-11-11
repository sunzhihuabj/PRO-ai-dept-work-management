import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/utils/request'

/** 权限点类型 */
export type PermType = 'MENU' | 'BUTTON' | 'API'
/** 权限效果 */
export type PermEffect = 'GRANT' | 'DENY'

/** 权限点树节点 */
export interface PermPointNode {
  permCode: string
  permName: string
  permType: PermType
  children: PermPointNode[]
}

/** 权限分配记录 */
export interface PermAssignment {
  id: number
  subjectType: 'DEPT' | 'POSITION'
  subjectId: number
  permCode: string
  permType: PermType
  effect: PermEffect
}

/** 批量分配请求 */
export interface PermAssignParams {
  subjectType: 'DEPT' | 'POSITION'
  subjectId: number
  permCodes: string[]
  effect: PermEffect
  remark?: string
}

/** 维度来源明细 */
export interface DimensionEntry {
  dimension: 'DEPT' | 'FIXED' | 'TEMP'
  sourceName: string
  sourceId: number
  perms: string[]
}

/** 冲突明细 */
export interface ConflictEntry {
  permCode: string
  detail: string
  finalEffect: PermEffect
}

/** 权限预览结果 */
export interface FinePermPreview {
  userId: number
  username: string
  realName?: string
  deptName?: string
  finePermEnabled: boolean
  version: number
  dimensions: DimensionEntry[]
  conflicts: ConflictEntry[]
  grantedPerms: string[]
  grantedMenus: string[]
  roles: string[]
}

/** 职位 */
export interface Position {
  id: number
  positionCode: string
  positionName: string
  description?: string
  status: number
  createTime?: string
  holderCount: number
}

/** 用户职位关联 */
export interface UserPosition {
  id: number
  userId: number
  positionId: number
  positionName?: string
  positionCode?: string
  positionKind: 'FIXED' | 'TEMP'
  startTime?: string
  endTime?: string
  effective: boolean
}

/** 权限模板 */
export interface PermTemplate {
  id: number
  templateCode: string
  templateName: string
  description?: string
  createTime?: string
  items?: Array<{ permCode: string; permType: PermType; effect: PermEffect }>
}

/** 变更日志 */
export interface PermChangeLog {
  id: number
  operatorId: number
  operatorName: string
  subjectType: 'USER' | 'DEPT' | 'POSITION'
  subjectId: number
  subjectName: string
  dimension?: string
  operation: string
  permCodes?: string
  beforeJson?: string
  afterJson?: string
  remark?: string
  createTime: string
}

// ==================== 权限点与分配 ====================

export function getPermPointsApi() {
  return get<PermPointNode[]>('/v1/fine-perms/points')
}

export function getAssignmentsApi(subjectType: 'DEPT' | 'POSITION', subjectId: number) {
  return get<PermAssignment[]>('/v1/fine-perms/assignments', { subjectType, subjectId })
}

export function assignPermsApi(data: PermAssignParams) {
  return post<number>('/v1/fine-perms/assignments', data)
}

export function removePermsApi(data: PermAssignParams) {
  return put<number>('/v1/fine-perms/assignments/remove', data)
}

export function clearAssignmentsApi(subjectType: 'DEPT' | 'POSITION', subjectId: number, remark?: string) {
  return post<number>('/v1/fine-perms/assignments/clear', { subjectType, subjectId, remark })
}

// ==================== 权限预览 ====================

export function previewUserPermsApi(userId: number) {
  return get<FinePermPreview>('/v1/fine-perms/preview', { userId })
}

export function previewMyPermsApi() {
  return get<FinePermPreview>('/v1/fine-perms/preview/me')
}

export function getPermVersionApi() {
  return get<number>('/v1/fine-perms/version')
}

// ==================== 职位管理 ====================

export function getPositionsApi() {
  return get<Position[]>('/v1/fine-perms/positions')
}

export function createPositionApi(data: Partial<Position>) {
  return post<number>('/v1/fine-perms/positions', data)
}

export function updatePositionApi(id: number, data: Partial<Position>) {
  return put(`/v1/fine-perms/positions/${id}`, data)
}

export function deletePositionApi(id: number) {
  return del(`/v1/fine-perms/positions/${id}`)
}

export function getUserPositionsApi(userId: number) {
  return get<UserPosition[]>(`/v1/fine-perms/users/${userId}/positions`)
}

export function assignUserPositionApi(
  userId: number,
  data: { positionId: number; positionKind: 'FIXED' | 'TEMP'; startTime?: string; endTime?: string; remark?: string }
) {
  return post<number>(`/v1/fine-perms/users/${userId}/positions`, data)
}

export function removeUserPositionApi(id: number, remark?: string) {
  return del(`/v1/fine-perms/user-positions/${id}?remark=${encodeURIComponent(remark || '')}`)
}

// ==================== 权限模板 ====================

export function getTemplatesApi() {
  return get<PermTemplate[]>('/v1/fine-perms/templates')
}

export function createTemplateApi(data: Partial<PermTemplate>) {
  return post<number>('/v1/fine-perms/templates', data)
}

export function updateTemplateApi(id: number, data: Partial<PermTemplate>) {
  return put(`/v1/fine-perms/templates/${id}`, data)
}

export function deleteTemplateApi(id: number) {
  return del(`/v1/fine-perms/templates/${id}`)
}

export function applyTemplateApi(templateId: number, subjectType: 'DEPT' | 'POSITION', subjectId: number, remark?: string) {
  return post<number>(`/v1/fine-perms/templates/${templateId}/apply`, { subjectType, subjectId, remark })
}

// ==================== 变更日志 ====================

export interface ChangeLogQuery {
  subjectType?: 'USER' | 'DEPT' | 'POSITION'
  subjectId?: number
  operatorName?: string
  dimension?: string
  operation?: string
  startTime?: string
  endTime?: string
  pageNo?: number
  pageSize?: number
}

export function getChangeLogsApi(params: ChangeLogQuery) {
  return get<PageResult<PermChangeLog>>('/v1/fine-perms/change-logs', params)
}
