import { get } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface SysOperLog {
  id: number
  title?: string
  businessType?: number
  method?: string
  requestMethod?: string
  operName?: string
  operUrl?: string
  operIp?: string
  operParam?: string
  jsonResult?: string
  status?: number
  errorMsg?: string
  operTime?: string
  costTime?: number
  createTime?: string
}

export function listOperLogsApi(params: {
  pageNum?: number
  pageSize?: number
  title?: string
  operName?: string
  status?: number
}) {
  return get<PageResult<SysOperLog>>('/v1/oper-logs', params)
}
