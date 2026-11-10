import { get } from '@/utils/request'

export function getTodoApi() {
  return get('/v1/dashboard/todo')
}

export function getStatsApi() {
  return get('/v1/dashboard/stats')
}

export function getNotificationsApi(limit = 10) {
  return get('/v1/dashboard/notifications', { limit })
}

export function getQuickActionsApi() {
  return get('/v1/dashboard/quick-actions')
}
