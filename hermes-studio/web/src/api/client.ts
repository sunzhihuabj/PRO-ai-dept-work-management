// 统一 fetch 封装：携带 Studio 令牌；401 自动回登录页
export const TOKEN_KEY = 'hermes-studio-token'
export const USER_KEY = 'hermes-studio-user'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string, username: string) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, username)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export class ApiError extends Error {
  status: number
  code: string
  constructor(status: number, message: string, code = '') {
    super(message)
    this.status = status
    this.code = code
  }
}

export async function api<T = any>(path: string, opts: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    Authorization: `Bearer ${getToken()}`,
    ...((opts.headers as Record<string, string>) || {})
  }
  if (opts.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json'
  const resp = await fetch(path, { ...opts, headers })
  if (resp.status === 401) {
    clearToken()
    window.location.hash = ''
    window.location.href = '/login'
    throw new ApiError(401, '登录态失效')
  }
  const text = await resp.text()
  let data: any = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = text
  }
  if (!resp.ok) {
    const err = data?.error || {}
    throw new ApiError(resp.status, err.message || `请求失败(${resp.status})`, err.code || '')
  }
  return data as T
}

// Hermes 透传前缀
export function hermes<T = any>(path: string, opts: RequestInit = {}): Promise<T> {
  return api<T>(`/api/hermes${path}`, opts)
}
