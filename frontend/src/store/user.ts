import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, logoutApi, getUserInfoApi, type LoginParams, type UserInfo } from '@/api/auth'
import router from '@/router'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('dept_token') || '')
  const userInfo = ref<UserInfo | null>(null)
  const permissions = ref<string[]>([])
  const roles = ref<string[]>([])
  // T-404 精细权限：MENU/BUTTON/API 三级权限点 + 全局版本号（实时生效）
  const finePermissions = ref<string[]>([])
  const finePermEnabled = ref(false)
  const permVersion = ref(0)

  const isLoggedIn = computed(() => !!token.value)
  const userName = computed(() => userInfo.value?.realName || userInfo.value?.username || '')
  const userRole = computed(() => roles.value[0] || '')
  const isAdmin = computed(() => roles.value.includes('ROLE_DEPT_LEADER'))
  const isManager = computed(() =>
    roles.value.some(r => ['ROLE_DEPT_LEADER', 'ROLE_PROJECT_LEADER', 'ROLE_REFORM_LEADER'].includes(r))
  )

  /** 是否持有某精细权限点（未启用精细控制时视为持有——灰度原则） */
  function hasPerm(code: string): boolean {
    if (!finePermEnabled.value) return true
    return finePermissions.value.includes(code)
  }

  /** 菜单级过滤：MENU 权限点（code=路由 path）；未启用精细控制时不限制 */
  function canAccessMenu(path: string): boolean {
    if (!finePermEnabled.value) return true
    return finePermissions.value.includes(path)
  }

  async function login(params: LoginParams) {
    const res = await loginApi(params)
    token.value = res.data.token
    localStorage.setItem('dept_token', res.data.token)
    userInfo.value = res.data.user
    permissions.value = res.data.user.permissions || []
    roles.value = res.data.user.roles || []
    applyFinePerms(res.data.user as any)
    return res
  }

  async function fetchUserInfo() {
    const res = await getUserInfoApi()
    userInfo.value = res.data
    permissions.value = res.data.permissions || []
    roles.value = res.data.roles || []
    applyFinePerms(res.data as any)
    return res.data
  }

  /** 应用后端下发的精细权限信息 */
  function applyFinePerms(data: { finePermissions?: string[]; finePermEnabled?: boolean; permVersion?: number }) {
    finePermissions.value = data.finePermissions || []
    finePermEnabled.value = !!data.finePermEnabled
    permVersion.value = data.permVersion || 0
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
      finePermissions.value = []
      finePermEnabled.value = false
      permVersion.value = 0
      localStorage.removeItem('dept_token')
      router.push('/login')
    }
  }

  return {
    token,
    userInfo,
    permissions,
    roles,
    finePermissions,
    finePermEnabled,
    permVersion,
    isLoggedIn,
    userName,
    userRole,
    isAdmin,
    isManager,
    hasPerm,
    canAccessMenu,
    login,
    fetchUserInfo,
    logout
  }
})
