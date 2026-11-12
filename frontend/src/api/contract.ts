import { get, post, request } from '@/utils/request'

export interface Contract {
  id?: number
  contractNo?: string
  name: string
  customerName: string
  amount: number
  signDate?: string
  effectiveDate?: string
  warrantyMonths?: number
  payRatioPrepay: number
  payRatioDelivery: number
  payRatioAccept: number
  payRatioWarranty: number
  status?: string
  extractedJson?: string
  riskLevel?: string
  riskSummary?: string
  remark?: string
  createTime?: string
}

export interface ContractApproval {
  id: number
  contractId: number
  node: string
  approverName?: string
  result: string
  comment?: string
  createTime: string
}

export interface ApprovalAction {
  result: 'APPROVED' | 'REJECTED'
  comment?: string
}

/** 合同-项目关联项（T-406：合同侧视图，实时项目状态） */
export interface ContractProjectItem {
  projectId: number
  projectName: string
  projectCode?: string
  projectType?: number
  projectStatus?: number
  projectLeader?: string
  bindBy?: string
  bindTime?: string
}

/** 合同-项目关联项（T-406：项目侧视图，实时合同状态） */
export interface ProjectContractItem {
  contractId: number
  contractNo?: string
  contractName: string
  customerName?: string
  amount?: number
  contractStatus?: string
  bindBy?: string
  bindTime?: string
}

/** 合同关联的项目列表 */
export function listContractProjectsApi(id: number) {
  return get(`/v1/contracts/${id}/projects`)
}

/** 绑定项目（仅已生效合同） */
export function bindContractProjectApi(id: number, projectId: number) {
  return post(`/v1/contracts/${id}/projects/${projectId}`, {})
}

/** 解除项目绑定 */
export function unbindContractProjectApi(id: number, projectId: number) {
  return request({ method: 'DELETE', url: `/v1/contracts/${id}/projects/${projectId}` })
}

/** 项目关联的合同列表 */
export function listProjectContractsApi(projectId: number) {
  return get(`/v1/contracts/project/${projectId}/contracts`)
}

/** 合同分页 */
export function listContractsApi(pageNum: number, pageSize: number, name?: string, status?: string) {
  return get('/v1/contracts', { pageNum, pageSize, name, status })
}

/** 合同详情（含审批记录） */
export function getContractDetailApi(id: number) {
  return get(`/v1/contracts/${id}`)
}

/** 创建合同草稿 */
export function createContractApi(data: Contract) {
  return post('/v1/contracts', data)
}

/** 提交会签（G1 门） */
export function submitCountersignApi(id: number) {
  return post(`/v1/contracts/${id}/submit-countersign`, {})
}

/** 会签 */
export function countersignApi(id: number, data: ApprovalAction) {
  return post(`/v1/contracts/${id}/countersign`, data)
}

/** 审批 */
export function approveContractApi(id: number, data: ApprovalAction) {
  return post(`/v1/contracts/${id}/approve`, data)
}

/** 用印登记（G2 门，生效） */
export function sealContractApi(id: number, comment?: string) {
  return post(`/v1/contracts/${id}/seal`, { comment })
}

/** 关闭/终止 */
export function closeContractApi(id: number, terminated = false) {
  return post(`/v1/contracts/${id}/close?terminated=${terminated}`, {})
}

/** 上传合同文档到知识库（AGT-06 提取源） */
export function uploadContractDocApi(file: File, title?: string) {
  const form = new FormData()
  form.append('file', file)
  if (title) form.append('title', title)
  return request({
    method: 'POST',
    url: '/v1/kb/documents',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}
