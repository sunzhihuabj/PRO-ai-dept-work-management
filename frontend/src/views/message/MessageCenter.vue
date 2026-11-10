<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">消息中心</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <div class="search-bar">
        <el-radio-group v-model="query.isRead" @change="loadData">
          <el-radio-button :value="undefined">全部</el-radio-button>
          <el-radio-button :value="0">未读</el-radio-button>
          <el-radio-button :value="1">已读</el-radio-button>
        </el-radio-group>
        <el-button type="primary" plain :loading="readingAll" @click="readAll">全部已读</el-button>
      </div>

      <div v-loading="loading">
        <div v-for="msg in messages" :key="msg.id" class="msg-item" :class="{ unread: msg.isRead === 0 }">
          <div class="msg-main">
            <div class="msg-title">
              <el-badge v-if="msg.isRead === 0" is-dot class="dot" />
              <span>{{ msg.title }}</span>
              <el-tag size="small" style="margin-left: 8px;" type="info">{{ msg.messageType || '通知' }}</el-tag>
            </div>
            <div class="msg-content">{{ msg.content || '（无内容）' }}</div>
            <div class="msg-time">{{ msg.createTime }}</div>
          </div>
          <div class="msg-actions">
            <el-button v-if="msg.isRead === 0" link type="primary" size="small" @click="readOne(msg)">标记已读</el-button>
          </div>
        </div>
        <el-empty v-if="!messages.length && !loading" description="暂无消息" />
      </div>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
        style="margin-top: 16px; justify-content: flex-end; display: flex;"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listMessagesApi, markReadApi, markAllReadApi, type UserMessage } from '@/api/message'

const loading = ref(false)
const readingAll = ref(false)
const messages = ref<UserMessage[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, isRead: undefined as number | undefined })

async function loadData() {
  loading.value = true
  try {
    const res = await listMessagesApi(query)
    messages.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function readOne(msg: UserMessage) {
  await markReadApi(msg.id)
  ElMessage.success('已标记已读')
  loadData()
}

async function readAll() {
  readingAll.value = true
  try {
    const res = await markAllReadApi()
    ElMessage.success(`已读 ${res.data?.count ?? 0} 条`)
    loadData()
  } catch (e) {
  } finally {
    readingAll.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
.search-bar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; }
.msg-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 8px;
  border-bottom: 1px solid #ebeef5;
  &.unread { background: #f0f7ff; }
  .msg-title { font-weight: 600; display: flex; align-items: center; }
  .msg-content { color: #606266; font-size: 13px; margin-top: 4px; }
  .msg-time { color: #909399; font-size: 12px; margin-top: 4px; }
}
.dot { margin-right: 6px; }
</style>
