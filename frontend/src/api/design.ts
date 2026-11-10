import { get, post } from '@/utils/request'

export interface DesignTemplate {
  id: number
  templateName: string
  scene: string
  templateText: string
  specRefs?: string
  enabled: number
}

export interface DesignSurvey {
  id?: number
  projectId: number
  siteName: string
  surveyDate?: string
  surveyor?: string
  windClass?: string
  content?: string
}

export interface DesignScheme {
  id?: number
  projectId: number
  contractId?: number
  versionNo?: number
  title: string
  content: string
  genType?: string
  status?: string
  releaseNote?: string
  releaseTime?: string
  releasedBy?: string
  createTime?: string
}

export interface BomItem {
  id?: number
  schemeId?: number
  materialCode: string
  materialName: string
  specModel?: string
  qty: number
  unit?: string
  remark?: string
}

export interface BomViolation {
  rule: string
  itemIndex: number
  materialCode: string
  message: string
}

/** 模板列表 */
export function listTemplatesApi(scene?: string) {
  return get('/v1/design/templates', { scene })
}

/** 勘测记录 */
export function listSurveysApi(projectId: number) {
  return get('/v1/design/surveys', { projectId })
}

export function createSurveyApi(data: DesignSurvey) {
  return post('/v1/design/surveys', data)
}

/** 方案分页 */
export function listSchemesApi(pageNum: number, pageSize: number, projectId?: number, status?: string) {
  return get('/v1/design/schemes', { pageNum, pageSize, projectId, status })
}

/** 方案详情（含 BOM + 校验记录） */
export function getSchemeDetailApi(id: number) {
  return get(`/v1/design/schemes/${id}`)
}

/** 受控发布 */
export function publishSchemeApi(id: number, releaseNote?: string) {
  return post(`/v1/design/schemes/${id}/publish`, { releaseNote })
}

/** 覆盖式录入 BOM */
export function replaceBomApi(schemeId: number, items: BomItem[]) {
  return post(`/v1/design/schemes/${schemeId}/bom`, items)
}

/** 人工触发 BOM 校验 */
export function checkBomApi(schemeId: number) {
  return post(`/v1/design/schemes/${schemeId}/check`, {})
}
