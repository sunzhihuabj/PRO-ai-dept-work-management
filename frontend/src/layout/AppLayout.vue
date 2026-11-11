<template>
  <el-container class="app-layout">
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobile" :width="appStore.sidebarWidth" class="app-sidebar">
      <div class="logo">
        <el-icon :size="24" color="#fff"><Avatar /></el-icon>
        <span v-show="!appStore.sidebarCollapsed" class="logo-text">部门管理平台</span>
      </div>
      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="route.path"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          background-color="#001529"
          text-color="rgba(255,255,255,0.85)"
          active-text-color="#fff"
          router
        >
          <template v-for="group in menuGroups" :key="group.title">
            <div v-if="!appStore.sidebarCollapsed" class="menu-group-title">{{ group.title }}</div>
            <template v-for="item in group.items" :key="item.index">
              <el-menu-item v-if="!item.admin || userStore.isAdmin" :index="item.index">
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
                <el-badge v-if="item.badge === 'unread' && unreadCount > 0" :value="unreadCount" :max="99" class="unread-badge" />
              </el-menu-item>
            </template>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <!-- 移动端抽屉侧边栏 -->
    <el-drawer v-model="mobileMenuOpen" direction="ltr" size="220px" :with-header="false" class="mobile-drawer">
      <div class="logo">
        <el-icon :size="24" color="#fff"><Avatar /></el-icon>
        <span class="logo-text">部门管理平台</span>
      </div>
      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="route.path"
          background-color="#001529"
          text-color="rgba(255,255,255,0.85)"
          active-text-color="#fff"
          router
          @select="mobileMenuOpen = false"
        >
          <template v-for="group in menuGroups" :key="group.title">
            <div class="menu-group-title">{{ group.title }}</div>
            <template v-for="item in group.items" :key="item.index">
              <el-menu-item v-if="!item.admin || userStore.isAdmin" :index="item.index">
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
                <el-badge v-if="item.badge === 'unread' && unreadCount > 0" :value="unreadCount" :max="99" class="unread-badge" />
              </el-menu-item>
            </template>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-drawer>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleSidebar">
            <Fold v-if="!appStore.sidebarCollapsed && !isMobile" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb v-if="!isMobile" separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title && route.path !== '/dashboard'">
              {{ route.meta.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
          <span v-else class="mobile-title">{{ route.meta.title || '部门管理平台' }}</span>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="30" :icon="UserFilled" />
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
import {
  UserFilled, HomeFilled, Document, Folder, Message, Promotion, Stamp, SetUp,
  Box, Van, RefreshRight, Coin, Service, Collection, Files, Bell, ChatDotRound,
  MagicStick, DataAnalysis, Aim, DataLine, User, OfficeBuilding, BellFilled, Tickets, Money
} from '@element-plus/icons-vue'
import { ref, watch, markRaw, onMounted, onUnmounted } from 'vue'
import { unreadCountApi } from '@/api/message'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

// ---------- 响应式：移动端侧栏抽屉化 ----------
const isMobile = ref(window.innerWidth < 768)
const mobileMenuOpen = ref(false)
function onResize() {
  const mobile = window.innerWidth < 768
  if (mobile !== isMobile.value) {
    isMobile.value = mobile
    mobileMenuOpen.value = false
  }
}
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

function toggleSidebar() {
  if (isMobile.value) {
    mobileMenuOpen.value = !mobileMenuOpen.value
  } else {
    appStore.toggleSidebar()
  }
}

// ---------- 菜单分组数据源（补全业务域入口） ----------
interface MenuItem { index: string; label: string; icon: any; admin?: boolean; badge?: string }
interface MenuGroup { title: string; items: MenuItem[] }

const menuGroups: MenuGroup[] = [
  {
    title: '日常工作',
    items: [
      { index: '/dashboard', label: '工作台', icon: markRaw(HomeFilled) },
      { index: '/task', label: '任务管理', icon: markRaw(Document) },
      { index: '/project', label: '项目管理', icon: markRaw(Folder) },
      { index: '/message', label: '消息中心', icon: markRaw(Message), badge: 'unread' }
    ]
  },
  {
    title: '业务域',
    items: [
      { index: '/tender', label: '投标管理', icon: markRaw(Promotion) },
      { index: '/contract', label: '合同管理', icon: markRaw(Stamp) },
      { index: '/design', label: '设计管理', icon: markRaw(SetUp) },
      { index: '/workshop', label: '样机管理', icon: markRaw(Box) },
      { index: '/delivery', label: '交付管理', icon: markRaw(Van) },
      { index: '/ecn', label: '变更管理', icon: markRaw(RefreshRight) },
      { index: '/cost', label: '成本管理', icon: markRaw(Coin) },
      { index: '/aftermarket', label: '售后管理', icon: markRaw(Service) },
      { index: '/archive', label: '档案管理', icon: markRaw(Collection) }
    ]
  },
  {
    title: '协同与智能',
    items: [
      { index: '/file/list', label: '文件归档', icon: markRaw(Files) },
      { index: '/event', label: '事件审批', icon: markRaw(Bell) },
      { index: '/agent', label: 'Agent 工作台', icon: markRaw(ChatDotRound) },
      { index: '/ai', label: 'AI助手', icon: markRaw(MagicStick) },
      { index: '/kpi', label: '绩效管理', icon: markRaw(DataAnalysis) },
      { index: '/lean', label: '精益改进', icon: markRaw(Aim) },
      { index: '/lean/stream', label: '价值流分析', icon: markRaw(DataLine) }
    ]
  },
  {
    title: '系统管理',
    items: [
      { index: '/system/user', label: '用户管理', icon: markRaw(User), admin: true },
      { index: '/system/dept', label: '组织架构', icon: markRaw(OfficeBuilding), admin: true },
      { index: '/system/notice', label: '通知公告', icon: markRaw(BellFilled), admin: true },
      { index: '/system/log', label: '操作日志', icon: markRaw(Tickets), admin: true },
      { index: '/system/ai-config', label: 'AI 服务配置', icon: markRaw(MagicStick), admin: true },
      { index: '/bonus', label: '奖金管理', icon: markRaw(Money), admin: true }
    ]
  }
]

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

  .menu-scroll { height: calc(100% - 50px); }

  :deep(.el-menu) {
    border-right: none;
  }
}

.menu-group-title {
  padding: 14px 20px 6px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 1px;
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
    min-width: 0;

    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      &:hover { color: var(--el-color-primary); }
    }
    .mobile-title {
      font-size: 15px;
      font-weight: 600;
      color: #303133;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
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
  @media (max-width: 768px) { padding: 10px; }
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

<style lang="scss">
/* 移动端抽屉（全局样式：抽屉内容在 body 下） */
.mobile-drawer {
  .el-drawer__body { padding: 0; background: #001529; }
  .logo {
    height: 50px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: #fff;
    font-size: 16px;
    font-weight: bold;
  }
  .menu-group-title {
    padding: 14px 20px 6px;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.4);
    letter-spacing: 1px;
  }
  .el-menu { border-right: none; }
}
</style>
