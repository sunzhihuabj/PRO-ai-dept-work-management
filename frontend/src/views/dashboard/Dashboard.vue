<template>
  <div class="page-container">
    <!-- 数据概览卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <div class="stat-card">
          <div class="stat-icon stat-icon--primary"><el-icon><Document /></el-icon></div>
          <div class="stat-meta">
            <div class="stat-value">{{ stats?.taskStats?.total ?? 0 }}</div>
            <div class="stat-label">任务总数</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card">
          <div class="stat-icon stat-icon--success"><el-icon><CircleCheck /></el-icon></div>
          <div class="stat-meta">
            <div class="stat-value">{{ stats?.taskStats?.completed ?? 0 }}</div>
            <div class="stat-label">已完成</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card">
          <div class="stat-icon stat-icon--warning"><el-icon><Loading /></el-icon></div>
          <div class="stat-meta">
            <div class="stat-value">{{ stats?.taskStats?.inProgress ?? 0 }}</div>
            <div class="stat-label">进行中</div>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-card">
          <div class="stat-icon stat-icon--danger"><el-icon><Warning /></el-icon></div>
          <div class="stat-meta">
            <div class="stat-value">{{ stats?.taskStats?.overdue ?? 0 }}</div>
            <div class="stat-label">已逾期</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- 待办列表 -->
      <el-col :xs="24" :lg="16">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>我的待办</span>
              <el-tag type="info">{{ todoTotal }}</el-tag>
            </div>
          </template>
          <div v-loading="todoLoading">
            <div v-for="group in todoGroups" :key="group.type" class="todo-group">
              <div class="group-header">
                <span>{{ group.name }}</span>
                <el-badge :value="group.count" :max="99" />
              </div>
              <div v-if="group.items?.length">
                <div
                  v-for="item in group.items"
                  :key="item.id"
                  class="todo-item"
                  :class="{ overdue: item.overdue }"
                  role="button"
                  tabindex="0"
                  @click="handleTodoClick(item)"
                  @keydown.enter.prevent="handleTodoClick(item)"
                >
                  <div class="todo-title">
                    <el-tag :type="priorityTag(item.priority)" size="small">{{ item.priorityText }}</el-tag>
                    <span>{{ item.title }}</span>
                  </div>
                  <div class="todo-meta">
                    <span v-if="item.deadline">截止: {{ item.deadline }}</span>
                    <el-tag size="small">{{ item.statusText }}</el-tag>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无待办" :image-size="60" />
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 快捷入口 + 消息 -->
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover" class="mb-16">
          <template #header><span>快捷入口</span></template>
          <div class="quick-actions">
            <div
              v-for="action in quickActions"
              :key="action.id"
              class="action-item"
              role="button"
              tabindex="0"
              @click="$router.push(action.path)"
              @keydown.enter.prevent="$router.push(action.path)"
            >
              <el-icon :size="24" color="#409eff"><component :is="action.icon" /></el-icon>
              <span>{{ action.name }}</span>
            </div>
            <el-empty v-if="!quickActions.length && !msgLoading" description="暂无快捷入口" :image-size="60" />
          </div>
        </el-card>

        <el-card shadow="hover">
          <template #header><span>最新消息</span></template>
          <div v-loading="msgLoading">
            <div v-for="msg in notifications" :key="msg.id" class="msg-item">
              <div class="msg-title">
                <el-badge v-if="!msg.isRead" is-dot>
                  <span>{{ msg.title }}</span>
                </el-badge>
                <span v-else>{{ msg.title }}</span>
              </div>
              <span class="msg-time">{{ msg.createTime }}</span>
            </div>
            <el-empty v-if="!notifications.length" description="暂无消息" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Document, CircleCheck, Loading, Warning } from '@element-plus/icons-vue'
import { getStatsApi, getTodoApi, getNotificationsApi, getQuickActionsApi } from '@/api/dashboard'

const router = useRouter()

const stats = ref<any>(null)
const todoData = ref<any>(null)
const todoLoading = ref(false)
const notifications = ref<any[]>([])
const msgLoading = ref(false)
const quickActions = ref<any[]>([])

const todoGroups = computed(() => todoData.value?.groups || [])
const todoTotal = computed(() => todoGroups.value.reduce((sum: number, g: any) => sum + (g.count || 0), 0))

function priorityTag(priority: number) {
  if (priority === 1) return 'danger'
  if (priority === 2) return 'warning'
  return 'info'
}

function handleTodoClick(item: any) {
  if (item.id) {
    router.push(`/task/detail/${item.id}`)
  }
}

async function loadData() {
  todoLoading.value = true
  msgLoading.value = true
  // allSettled：单接口失败不连坐清空整个工作台（如待办失败时快捷入口仍可用）
  const [statsR, todoR, msgR, actionR] = await Promise.allSettled([
    getStatsApi(),
    getTodoApi(),
    getNotificationsApi(10),
    getQuickActionsApi()
  ])
  if (statsR.status === 'fulfilled') stats.value = statsR.value.data
  if (todoR.status === 'fulfilled') todoData.value = todoR.value.data
  if (msgR.status === 'fulfilled') notifications.value = msgR.value.data || []
  if (actionR.status === 'fulfilled') quickActions.value = actionR.value.data || []
  if (statsR.status === 'rejected' || todoR.status === 'rejected' || msgR.status === 'rejected' || actionR.status === 'rejected') {
    ElMessage.error('部分工作台数据加载失败，请稍后重试')
  }
  todoLoading.value = false
  msgLoading.value = false
}

// 页面被 keep-alive 缓存，使用 onActivated 在每次进入时刷新数据
onActivated(loadData)
</script>

<style lang="scss" scoped>
.todo-group {
  margin-bottom: 16px;
  .group-header {
    display: flex; align-items: center; gap: 8px;
    font-weight: bold; color: #303133;
    margin-bottom: 8px; padding-bottom: 4px;
    border-bottom: 1px solid #ebeef5;
  }
}

.todo-item {
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  display: flex; justify-content: space-between; align-items: center;
  &:hover { background: #f5f7fa; }
  &.overdue { background: #fef0f0; }

  .todo-title { display: flex; align-items: center; gap: 8px; }
  .todo-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--dm-muted); }
}

.mb-16 { margin-bottom: 16px; }

.quick-actions {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px;
  .action-item {
    display: flex; flex-direction: column; align-items: center; gap: 8px;
    padding: 12px; border-radius: 4px; cursor: pointer;
    &:hover { background: #f5f7fa; }
    span { font-size: 13px; color: #606266; }
  }
}

.msg-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
  &:last-child { border-bottom: none; }
  .msg-title { margin-bottom: 4px; font-size: 13px; }
  .msg-time { font-size: 12px; color: var(--dm-muted); }
}
</style>
