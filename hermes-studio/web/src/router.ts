import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { title: '登录', public: true } },
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'dashboard', component: () => import('./views/DashboardView.vue'), meta: { title: '工作台' } },
  { path: '/chat', name: 'chat', component: () => import('./views/ChatView.vue'), meta: { title: '聊天控制台' } },
  { path: '/sessions', name: 'sessions', component: () => import('./views/SessionsView.vue'), meta: { title: '会话管理' } },
  { path: '/runs', name: 'runs', component: () => import('./views/RunsView.vue'), meta: { title: '委托任务' } },
  { path: '/approvals', name: 'approvals', component: () => import('./views/ApprovalsView.vue'), meta: { title: '审批中心' } },
  { path: '/workflows', name: 'workflows', component: () => import('./views/WorkflowsView.vue'), meta: { title: '可视化工作流' } },
  { path: '/cron', name: 'cron', component: () => import('./views/CronView.vue'), meta: { title: '定时任务' } },
  { path: '/models', name: 'models', component: () => import('./views/ModelsView.vue'), meta: { title: '模型与技能' } },
  { path: '/settings', name: 'settings', component: () => import('./views/SettingsView.vue'), meta: { title: '设置' } },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

export const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  document.title = `${to.meta.title || 'Hermes Studio'} · Hermes Studio`
  const auth = useAuthStore()
  if (!to.meta.public && !auth.token) return { name: 'login' }
  if (to.name === 'login' && auth.token) return { name: 'dashboard' }
  return true
})
