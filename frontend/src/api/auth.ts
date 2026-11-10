import { get, post, put } from '@/utils/request'

export interface LoginParams {
  username: string
  password: string
  rememberMe?: boolean
}

export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

export interface UserInfo {
  id: number
  username: string
  realName: string
  avatar?: string
  email?: string
  phone?: string
  roles: string[]
  permissions: string[]
}

export function loginApi(data: LoginParams) {
  return post<LoginResult>('/v1/auth/login', data)
}

export function logoutApi() {
  return post('/v1/auth/logout')
}

export function getUserInfoApi() {
  return get<UserInfo>('/v1/auth/info')
}

export function changePasswordApi(data: { oldPassword: string; newPassword: string; confirmPassword: string }) {
  return put('/v1/auth/password', data)
}
