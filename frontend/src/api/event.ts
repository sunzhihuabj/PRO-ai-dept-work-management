import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/utils/request'

/** 事件信息 */
export interface EventItem {
  id: number
  projectId: number
  eventType: number
  eventTitle: string
  urgency: number
  status: number
  applicantId?: number
  handlerId?: number
  description?: string
  closeTime?: string
  createTime?: string
}

/** 事件处理记录 */
export interface EventHandleItem {
  id: number
  eventId: number
  handleType: string
  handleDesc?: string
  handlerId?: number
  handleTime?: string
  handleResult?: string
}

/** 创建请求 */
export interface EventCreateDTO {
  projectId: number
  eventType: number
  eventTitle: string
  urgency?: number
  handlerId?: number
  description?: string
}

/** 处理请求 */
export interface EventHandleDTO {
  handleType: string
  handleDesc?: string
  handleResult?: string
  transferTo?: number
}

export interface EventListParams {
  pageNum?: number
  pageSize?: number
  projectId?: number
  eventType?: number
  status?: number
  urgency?: number
  handlerId?: number
  applicantId?: number
  keyword?: string
}

/** 状态/类型字典（与后端保持一致） */
export const EVENT_STATUS = ['待审批', '处理中', '已完成', '已关闭', '已驳回'] as const
export const EVENT_TYPE: Record<number, string> = { 1: '售后', 2: '变更', 3: '异常', 99: '其他' }
export const EVENT_URGENCY: Record<number, string> = { 1: '紧急', 2: '一般', 3: '低' }

export function getEventListApi(params: EventListParams) {
  return get<PageResult<EventItem>>('/v1/events', params)
}

export function getEventDetailApi(id: number) {
  return get<EventItem>(`/v1/events/${id}`)
}

export function createEventApi(data: EventCreateDTO) {
  return post<number>('/v1/events', data)
}

export function updateEventApi(id: number, data: EventCreateDTO) {
  return put(`/v1/events/${id}`, data)
}

export function deleteEventApi(id: number) {
  return del(`/v1/events/${id}`)
}

export function handleEventApi(id: number, data: EventHandleDTO) {
  return post(`/v1/events/${id}/handle`, data)
}

export function getEventHandlesApi(id: number) {
  return get<EventHandleItem[]>(`/v1/events/${id}/handles`)
}

/** 我待处理的事件（指派给我、未关闭） */
export function getMyPendingEventsApi() {
  return get<EventItem[]>('/v1/events/my-pending')
}

/** 我创建的事件 */
export function getMyCreatedEventsApi() {
  return get<EventItem[]>('/v1/events/my-created')
}
