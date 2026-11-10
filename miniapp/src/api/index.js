import http, { getToken, setToken, getUserInfo, setUserInfo, logout } from '../utils/request'

// 会话工具方法 re-export（页面统一从 @/api 导入）
export { getToken, setToken, getUserInfo, setUserInfo, logout }

// ---------- 认证 ----------
// 小程序端：wx.login 取 code → 微信授权登录（未绑定 4011 → 账密绑定）
export const wxLoginApi = (code) => http.post('/v1/auth/wx-login', { code })
export const wxBindApi = (code, username, password) =>
  http.post('/v1/auth/wx-bind', { code, username, password })
// H5 开发模式直接账密登录
export const loginApi = (username, password) => http.post('/v1/auth/login', { username, password })
export const getMyInfoApi = () => http.get('/v1/auth/info')

// ---------- 任务 ----------
export const getMyTodoApi = () => http.get('/v1/tasks/my-todo')
export const getTaskListApi = (params) => http.get('/v1/tasks', params)
export const getTaskDetailApi = (id) => http.get(`/v1/tasks/${id}`)
export const changeTaskStatusApi = (id, targetStatus, comment) =>
  http.put(`/v1/tasks/${id}/status`, { targetStatus, comment })
export const addWorkHourApi = (id, data) => http.post(`/v1/tasks/${id}/hours`, data)

// ---------- 事件 ----------
export const getEventListApi = (params) => http.get('/v1/events', params)
export const createEventApi = (data) => http.post('/v1/events', data)
export const getEventDetailApi = (id) => http.get(`/v1/events/${id}`)
export const handleEventApi = (id, data) => http.post(`/v1/events/${id}/handle`, data)
export const getMyPendingEventsApi = () => http.get('/v1/events/my-pending')
export const getMyCreatedEventsApi = () => http.get('/v1/events/my-created')

// ---------- 消息 ----------
export const listMessagesApi = (params) => http.get('/v1/messages', params)
export const unreadCountApi = () => http.get('/v1/messages/unread-count')
export const markReadApi = (id) => http.put(`/v1/messages/${id}/read`)
export const markAllReadApi = () => http.put('/v1/messages/read-all')

// ---------- 工作台 ----------
export const getStatsApi = () => http.get('/v1/dashboard/stats')

// ---------- 基础数据 ----------
export const getProjectListApi = (params) => http.get('/v1/projects', params)
