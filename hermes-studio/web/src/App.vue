<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useRuntimeStore } from './stores/runtime'

const auth = useAuthStore()
const runtime = useRuntimeStore()
const route = useRoute()
const router = useRouter()

const menus = [
  { path: '/dashboard', title: '工作台', icon: 'Odometer' },
  { path: '/chat', title: '聊天控制台', icon: 'ChatDotRound' },
  { path: '/sessions', title: '会话管理', icon: 'Files' },
  { path: '/runs', title: '委托任务', icon: 'Promotion' },
  { path: '/approvals', title: '审批中心', icon: 'Stamp' },
  { path: '/workflows', title: '可视化工作流', icon: 'Share' },
  { path: '/cron', title: '定时任务', icon: 'Timer' },
  { path: '/models', title: '模型与技能', icon: 'Cpu' },
  { path: '/settings', title: '设置', icon: 'Setting' }
]

const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'dashboard'))
const pageTitle = computed(() => (route.meta.title as string) || 'Hermes Studio')
let timer: number | undefined

onMounted(() => {
  runtime.refreshHealth()
  runtime.refreshCapabilities()
  timer = window.setInterval(() => runtime.refreshHealth(), 30000)
})
onUnmounted(() => window.clearInterval(timer))

function logout() {
  auth.logout()
  router.push('/login')
}
const collapsing = ref(false)
</script>

<template>
  <div v-if="route.name === 'login'" class="hs-content" style="padding: 0">
    <router-view />
  </div>
  <div v-else class="hs-layout">
    <aside class="hs-aside">
      <div class="hs-logo"><span class="logo-dot"></span>Hermes Studio</div>
      <el-menu class="hs-menu" :default-active="activeMenu" router background-color="#1d2530" text-color="#cfd8e3" active-text-color="#ffd700">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">
          <el-icon><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
          <el-badge v-if="m.path === '/approvals' && runtime.pendingCount > 0" :value="runtime.pendingCount" class="menu-badge" />
        </el-menu-item>
      </el-menu>
      <div class="muted" style="padding: 12px 16px; color: #7a8699">
        上游 {{ runtime.healthOk ? 'Hermes 在线' : 'Hermes 离线' }}
      </div>
    </aside>
    <div class="hs-main">
      <header class="hs-header">
        <div style="display: flex; align-items: center; gap: 10px">
          <el-tag :type="runtime.healthOk ? 'success' : 'danger'" size="small" effect="dark" round>
            {{ runtime.healthOk ? 'API Server 8642 在线' : 'API Server 8642 离线' }}
          </el-tag>
          <span style="font-weight: 600">{{ pageTitle }}</span>
        </div>
        <div style="display: flex; align-items: center; gap: 14px">
          <span class="muted">v{{ (runtime.health as any)?.version || '-' }} · Hermes Agent</span>
          <el-dropdown>
            <span style="cursor: pointer; display: flex; align-items: center; gap: 6px">
              <el-icon><User /></el-icon>{{ auth.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/settings')">设置</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <main class="hs-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.menu-badge { margin-left: 8px; }
</style>
