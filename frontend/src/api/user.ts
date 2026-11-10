import { get, post, put, del } from '@/utils/request'
import type { PageResult } from '@/utils/request'

export interface SysUser {
  id: number
  username: string
  realName?: string
  phone?: string
  email?: string
  avatar?: string
  roleCodes?: string
  status: number
  createTime?: string
}

export interface UserListParams {
  pageNum?: number
  pageSize?: number
  username?: string
  realName?: string
  status?: number
}

export interface UserDTO {
  username?: string
  password?: string
  realName: string
  phone?: string
  email?: string
  avatar?: string
  roleCodes?: string
  status?: number
}

export function getUserListApi(params: UserListParams) {
  return get<PageResult<SysUser>>('/v1/users', params)
}

export function getUserOptionsApi() {
  return get<SysUser[]>('/v1/users/options')
}

export function getUserDetailApi(id: number) {
  return get<SysUser>(`/v1/users/${id}`)
}

export function createUserApi(data: UserDTO) {
  return post<number>('/v1/users', data)
}

export function updateUserApi(id: number, data: UserDTO) {
  return put(`/v1/users/${id}`, data)
}

export function deleteUserApi(id: number) {
  return del(`/v1/users/${id}`)
}

export function resetPasswordApi(id: number, newPassword: string) {
  return put(`/v1/users/${id}/reset-password`, { newPassword })
}

export function toggleUserStatusApi(id: number, status: number) {
  return put(`/v1/users/${id}/status`, { status })
}
