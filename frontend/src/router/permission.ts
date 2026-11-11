import type { Router } from 'vue-router'
import router from './index'
import { useUserStore } from '@/store/user'
import { getPermVersionApi } from '@/api/finePerm'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'

const whiteList = ['/login', '/404']

/** T-404 权限版本轮询节流：30 秒内不重复查询 */
let lastVersionCheck = 0

export function setupRouterGuards(router: Router) {
  NProgress.configure({ showSpinner: false })

  router.beforeEach(async (to, _from, next) => {
    NProgress.start()

    const userStore = useUserStore()
    document.title = to.meta.title ? `${to.meta.title} - 部门工作管理平台` : '部门工作管理平台'

    if (userStore.token) {
      if (to.path === '/login') {
        next({ path: '/dashboard' })
        NProgress.done()
      } else {
        if (!userStore.userInfo) {
          try {
            await userStore.fetchUserInfo()
            next()
          } catch (error) {
            ElMessage.error('登录已过期，请重新登录')
            await userStore.logout()
            next({ path: '/login', query: { redirect: to.fullPath } })
            NProgress.done()
          }
        } else {
          // T-404 实时生效：路由切换时比对全局权限版本，变更即静默刷新权限
          const now = Date.now()
          if (now - lastVersionCheck > 30000) {
            lastVersionCheck = now
            try {
              const remote = await getPermVersionApi()
              if (remote.data !== userStore.permVersion) {
                await userStore.fetchUserInfo()
              }
            } catch (e) {
              // 版本检查失败不阻断导航
            }
          }
          next()
        }
      }
    } else {
      if (whiteList.includes(to.path)) {
        next()
      } else {
        next({ path: '/login', query: { redirect: to.fullPath } })
        NProgress.done()
      }
    }
  })

  router.afterEach(() => {
    NProgress.done()
  })
}

setupRouterGuards(router)
