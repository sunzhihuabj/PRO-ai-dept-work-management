import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import NProgress from 'nprogress'
import { useUserStore } from '@/store/user'
import router from '@/router'

/** 后端统一响应结构 */
export interface ApiResult<T = any> {
  code: number
  msg: string
  data: T
}

/** 分页结果 */
export interface PageResult<T = any> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    NProgress.start()
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    NProgress.done()
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    NProgress.done()
    const res = response.data

    if (response.config.responseType === 'blob') {
      return response as any
    }

    if (res.code === 200) {
      return res
    }

    if (res.code === 401) {
      ElMessage.error(res.msg || '登录已过期，请重新登录')
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
      return Promise.reject(new Error(res.msg || '未登录'))
    }

    if (res.code === 403) {
      ElMessage.error(res.msg || '无访问权限')
      return Promise.reject(new Error(res.msg))
    }

    ElMessage.error(res.msg || '请求失败')
    return Promise.reject(new Error(res.msg || '请求失败'))
  },
  (error) => {
    NProgress.done()
    const { response } = error
    if (response) {
      const status = response.status
      if (status === 401) {
        ElMessageBox.confirm('登录已过期，请重新登录', '提示', {
          confirmButtonText: '重新登录',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          const userStore = useUserStore()
          userStore.logout()
          router.push('/login')
        })
      } else if (status === 403) {
        ElMessage.error('无访问权限')
      } else if (status === 404) {
        ElMessage.error('请求资源不存在')
      } else if (status >= 500) {
        ElMessage.error('服务器内部错误')
      } else {
        ElMessage.error(response.data?.msg || `请求错误(${status})`)
      }
    } else {
      ElMessage.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export function request<T = any>(config: AxiosRequestConfig): Promise<ApiResult<T>> {
  return service(config) as unknown as Promise<ApiResult<T>>
}

export function get<T = any>(url: string, params?: any): Promise<ApiResult<T>> {
  return request({ method: 'GET', url, params })
}

export function post<T = any>(url: string, data?: any): Promise<ApiResult<T>> {
  return request({ method: 'POST', url, data })
}

export function put<T = any>(url: string, data?: any): Promise<ApiResult<T>> {
  return request({ method: 'PUT', url, data })
}

export function del<T = any>(url: string, params?: any): Promise<ApiResult<T>> {
  return request({ method: 'DELETE', url, params })
}

export default service
