<template>
  <el-container class="app-layout">
    <el-aside :width="appStore.sidebarWidth" class="app-sidebar">
      <div class="logo">
        <el-icon :size="24" color="#fff"><Avatar /></el-icon>
        <span v-show="!appStore.sidebarCollapsed" class="logo-text">部门管理平台</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        background-color="#001529"
        text-color="rgba(255,255,255,0.85)"
        active-text-color="#fff"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><HomeFilled /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-menu-item index="/task">
          <el-icon><Document /></el-icon>
          <span>任务管理</span>
        </el-menu-item>
        <el-menu-item index="/project">
          <el-icon><Folder /></el-icon>
          <span>项目管理</span>
        </el-menu-item>
        <el-menu-item index="/file/list">
          <el-icon><Files /></el-icon>
          <span>档案归档</span>
        </el-menu-item>
        <el-menu-item index="/event">
          <el-icon><Bell /></el-icon>
          <span>事件审批</span>
        </el-menu-item>
        <el-menu-item index="/kpi">
          <el-icon><DataAnalysis /></el-icon>
          <span>绩效管理</span>
        </el-menu-item>
        <el-menu-item index="/ai">
          <el-icon><MagicStick /></el-icon>
          <span>AI助手</span>
        </el-menu-item>
        <el-menu-item index="/lean">
          <el-icon><Aim /></el-icon>
          <span>精益改进</span>
        </el-menu-item>
        <el-menu-item index="/lean/stream">
          <el-icon><DataLine /></el-icon>
          <span>价值流分析</span>
        </el-menu-item>
        <el-menu-item index="/message">
          <el-icon><Message /></el-icon>
          <span>消息中心</span>
          <el-badge v-if="unreadCount > 0" :value="unreadCount" :max="99" class="unread-badge" />
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/system/user">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/system/dept">
          <el-icon><OfficeBuilding /></el-icon>
          <span>部门管理</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/system/notice">
          <el-icon><BellFilled /></el-icon>
          <span>通知公告</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/system/log">
          <el-icon><Tickets /></el-icon>
          <span>操作日志</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/system/ai-config">
          <el-icon><MagicStick /></el-icon>
          <span>AI 服务配置</span>
        </el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/bonus">
          <el-icon><Money /></el-icon>
          <span>奖金管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title && route.path !== '/dashboard'">
              {{ route.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :icon="UserFilled" />
              <span class="username">{{ userStore.userName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <keep-alive>
              <component :is="Component" />
            </keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { ElMessageBox } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { onMounted, onUnmounted } from 'vue'
import { unreadCountApi } from '@/api/message'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

// ---------- 未读消息角标 ----------
const unreadCount = ref(0)
let unreadTimer: ReturnType<typeof setInterval> | null = null

async function refreshUnread() {
  try {
    if (!userStore.token) return
    const res = await unreadCountApi()
    unreadCount.value = res?.data?.count ?? 0
  } catch (e) {}
}

onMounted(() => {
  refreshUnread()
  unreadTimer = setInterval(refreshUnread, 60000)
})
onUnmounted(() => {
  if (unreadTimer) clearInterval(unreadTimer)
})
// 路由切换时同步
route && watch(() => route.fullPath, () => refreshUnread())

async function handleCommand(command: string) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await userStore.logout()
    } catch (e) {
      // 取消
    }
  }
}
</script>

<style lang="scss" scoped>
.app-layout { height: 100vh; }

.app-sidebar {
  background-color: #001529;
  overflow-x: hidden;
  transition: width 0.3s;

  .logo {
    height: 50px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: #fff;
    font-size: 16px;
    font-weight: bold;
    .logo-text { white-space: nowrap; }
  }

  :deep(.el-menu) {
    border-right: none;
  }
}

.app-header {
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 50px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      &:hover { color: var(--el-color-primary); }
    }
  }

  .header-right {
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      .username { font-size: 14px; color: #303133; }
    }
  }
}

.app-main {
  background: #f0f2f5;
  padding: 16px;
  overflow-y: auto;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
.unread-badge {
  margin-left: 4px;
  transform: translateY(-2px);
}
</style>
