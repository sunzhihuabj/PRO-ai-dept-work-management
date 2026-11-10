import type { Router } from 'vue-router'
import router from './index'
import { useUserStore } from '@/store/user'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'

const whiteList = ['/login', '/404']

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
