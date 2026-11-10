import { request } from '@/utils/request'

// 简单的 TypeScript 类型声明，供编译期检查使用
interface FileInfo {
  id: number
  projectId: number
  phaseId: number
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  version: string
  versionNumber: number
  isArchive: number
  description: string
  createBy: string
  createTime: string
}

interface FileAudit {
  id: number
  fileId: number
  auditorId: number
  status: string
  comment?: string
  auditTime: string
}


/**
 * 文件上传接口（multipart/form-data）
 * @param formData 包含 projectId、phaseId、fileType、description、file 等字段的 FormData
 */
export function uploadFileApi(formData: FormData) {
  return request({
    method: 'POST',
    url: '/v1/files/upload',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * 查询文件列表（分页，可按项目/阶段/关键字过滤）
 */
export function listFilesApi(params: { pageNum?: number; pageSize?: number; projectId?: number; phaseId?: number; taskId?: number; keyword?: string }) {
  return request<{ records: FileInfo[]; total: number }>({ method: 'GET', url: '/v1/files', params })
}

/**
 * AI 分析当前版本与上一版本差异（文本类文件）
 */
export function diffFileApi(id: number) {
  return request<{ prevVersion: string; curVersion: string; analysis: string }>({
    method: 'POST',
    url: `/v1/files/${id}/diff`
  })
}

/**
 * 下载文件（返回 Blob）
 */
export function downloadFileApi(id: number) {
  return request({ method: 'GET', url: `/v1/files/${id}/download`, responseType: 'blob' })
}

/**
 * 获取文件详情
 */
export function getFileDetailApi(id: number) {
  return request<FileInfo>({ method: 'GET', url: `/v1/files/${id}` })
}

/**
 * 文件审核（通过/驳回）
 */
export function auditFileApi(id: number, data: { status: string; comment?: string }) {
  return request({ method: 'PUT', url: `/v1/files/${id}/audit`, data })
}

/**
 * 归档文件（标记为已归档）
 */
export function archiveFileApi(id: number) {
  return request({ method: 'PUT', url: `/v1/files/${id}/archive` })
}

/**
 * 获取文件的审核记录
 */
export function listFileAuditsApi(id: number) {
  return request<FileAudit[]>({ method: 'GET', url: `/v1/files/${id}/audits` })
}

// ---------- 版本历史 ----------
export interface FileVersion {
  id: number
  fileId: number
  versionNumber: number
  versionLabel: string
  filePath: string
  fileSize: number
  changeDesc?: string
  createBy?: string
  createTime?: string
}

export function listFileVersionsApi(id: number) {
  return request<FileVersion[]>({ method: 'GET', url: `/v1/files/${id}/versions` })
}

/** 版本下载 URL（直接拼 href 使用，走 /api 代理） */
export function fileVersionDownloadUrl(versionId: number) {
  return `/api/v1/files/versions/${versionId}/download`
}

/** 下载指定历史版本（Blob，带 Token） */
export function downloadFileVersionApi(versionId: number) {
  return request({ method: 'GET', url: `/v1/files/versions/${versionId}/download`, responseType: 'blob' })
}

// ---------- 归档台账 ----------
export interface FileArchiveRecord {
  id: number
  projectId?: number
  fileId: number
  archiveType?: string
  archiveStatus?: number
  archiveBy?: string
  archiveTime?: string
  description?: string
}

export function listArchiveRecordsApi(params: { projectId?: number; fileId?: number }) {
  return request<FileArchiveRecord[]>({ method: 'GET', url: '/v1/files/archive-records', params })
}

// ---------- 文件分类 ----------
export interface FileCategory {
  id: number
  parentId: number
  categoryName: string
  categoryCode?: string
  orderNum?: number
  status?: number
}

export function listFileCategoriesApi() {
  return request<FileCategory[]>({ method: 'GET', url: '/v1/file-categories' })
}

export function createFileCategoryApi(data: Partial<FileCategory>) {
  return request({ method: 'POST', url: '/v1/file-categories', data })
}

export function updateFileCategoryApi(id: number, data: Partial<FileCategory>) {
  return request({ method: 'PUT', url: `/v1/file-categories/${id}`, data })
}

export function deleteFileCategoryApi(id: number) {
  return request({ method: 'DELETE', url: `/v1/file-categories/${id}` })
}

