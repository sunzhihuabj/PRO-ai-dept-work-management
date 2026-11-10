import { get, post, put, del } from '@/utils/request'

export interface SysNotice {
  id: number
  noticeTitle: string
  noticeType?: number
  noticeContent?: string
  status?: number
  publishBy?: string
  publishTime?: string
  createTime?: string
}

export function listNoticesApi(params?: { noticeType?: number; status?: number }) {
  return get<SysNotice[]>('/v1/notices', params)
}

export function listPublishedNoticesApi() {
  return get<SysNotice[]>('/v1/notices/published')
}

export function createNoticeApi(data: Partial<SysNotice>) {
  return post<number>('/v1/notices', data)
}

export function updateNoticeApi(id: number, data: Partial<SysNotice>) {
  return put(`/v1/notices/${id}`, data)
}

export function publishNoticeApi(id: number) {
  return post<{ reached: number }>(`/v1/notices/${id}/publish`)
}

export function deleteNoticeApi(id: number) {
  return del(`/v1/notices/${id}`)
}
