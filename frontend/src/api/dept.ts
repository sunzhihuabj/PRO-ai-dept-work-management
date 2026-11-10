import { get, post, put, del } from '@/utils/request'

export interface SysDept {
  id: number
  parentId: number
  ancestors?: string
  deptName: string
  orderNum?: number
  leader?: string
  phone?: string
  email?: string
  status?: number
}

export function listDeptsApi() {
  return get<SysDept[]>('/v1/depts')
}

export function createDeptApi(data: Partial<SysDept>) {
  return post<number>('/v1/depts', data)
}

export function updateDeptApi(id: number, data: Partial<SysDept>) {
  return put(`/v1/depts/${id}`, data)
}

export function deleteDeptApi(id: number) {
  return del(`/v1/depts/${id}`)
}
