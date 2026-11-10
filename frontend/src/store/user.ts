import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, logoutApi, getUserInfoApi, type LoginParams, type UserInfo } from '@/api/auth'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('dept_token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<string[]>([])
  const roles = ref<string[]>([])

  const isLoggedIn = computed(() => !!token.value)
  const userName = computed(() => userInfo.value?.realName || userInfo.value?.username || '')
  const userRole = computed(() => roles.value[0] || '')
  const isAdmin = computed(() => roles.value.includes('ROLE_DEPT_LEADER'))
  const isManager = computed(() =>
    roles.value.some(r => ['ROLE_DEPT_LEADER', 'ROLE_PROJECT_LEADER', 'ROLE_REFORM_LEADER'].includes(r))
  )

  async function login(params: LoginParams) {
    const res = await loginApi(params)
    token.value = res.data.token
    localStorage.setItem('dept_token', res.data.token)
    userInfo.value = res.data.user
    permissions.value = res.data.user.permissions || []
    roles.value = res.data.user.roles || []
    return res
  }

  async function fetchUserInfo() {
    const res = await getUserInfoApi()
    userInfo.value = res.data
    permissions.value = res.data.permissions || []
    roles.value = res.data.roles || []
    return res.data
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (e) {
      // 忽略登出失败
    } finally {
      token.value = ''
      userInfo.value = null
      permissions.value = []
      roles.value = []
      localStorage.removeItem('dept_token')
      router.push('/login')
    }
  }

  return {
    token,
    userInfo,
    permissions,
    roles,
    isLoggedIn,
    userName,
    userRole,
    isAdmin,
    isManager,
    login,
    fetchUserInfo,
    logout
  }
})
