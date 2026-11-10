import { get, put } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface UserMessage {
  id: number
  userId: number
  messageType?: string
  title?: string
  content?: string
  relatedId?: number
  isRead?: number
  readTime?: string
  createTime?: string
}

export function listMessagesApi(params: { pageNum?: number; pageSize?: number; isRead?: number }) {
  return get<PageResult<UserMessage>>('/v1/messages', params)
}

export function unreadCountApi() {
  return get<{ count: number }>('/v1/messages/unread-count')
}

export function markReadApi(id: number) {
  return put(`/v1/messages/${id}/read`)
}

export function markAllReadApi() {
  return put<{ count: number }>('/v1/messages/read-all')
}
