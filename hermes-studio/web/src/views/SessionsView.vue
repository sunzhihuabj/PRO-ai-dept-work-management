<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  deleteSession, forkSession, getSessionMessages, listSessions, lockSessionModel, patchSession, HermesSession
} from '../api/hermes'

const router = useRouter()
const loading = ref(false)
const sessions = ref<HermesSession[]>([])
const query = reactive({ source: '', title: '' })

const drawer = ref(false)
const detailSession = ref<HermesSession | null>(null)
const messages = ref<any[]>([])
const messagesLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await listSessions({
      limit: 200,
      source: query.source || undefined,
      title: query.title || undefined
    })
    sessions.value = data?.data || []
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function fmt(ts?: number) {
  if (!ts) return '-'
  const ms = ts < 1e12 ? ts * 1000 : ts
  return new Date(ms).toLocaleString('zh-CN', { hour12: false })
}

async function openDetail(s: HermesSession) {
  detailSession.value = s
  drawer.value = true
  messagesLoading.value = true
  try {
    const data = await getSessionMessages(s.id, { limit: 300, order: 'oldest' })
    messages.value = data?.data || []
  } catch (e: any) {
    ElMessage.error(e.message)
    messages.value = []
  } finally {
    messagesLoading.value = false
  }
}

function msgText(m: any): string {
  if (typeof m.content === 'string') return m.content
  return JSON.stringify(m.content, null, 2)
}

async function rename(s: HermesSession) {
  const { value } = await ElMessageBox.prompt('新标题', '重命名会话', { inputValue: s.title || '' })
  await patchSession(s.id, { title: value })
  ElMessage.success('已改名')
  load()
}

async function fork(s: HermesSession) {
  const created = await forkSession(s.id, {})
  ElMessage.success(`已分支 → ${created?.id || created?.session_id || created?.session?.id || ''}`)
  load()
}

async function lockModel(s: HermesSession) {
  const { value } = await ElMessageBox.prompt('模型名（如 hermes-agent / 具体模型 ID；留空取消锁）', '会话模型锁', {
    inputValue: s.model || ''
  })
  await lockSessionModel(s.id, { model: value })
  ElMessage.success('模型锁已设置')
  load()
}

async function remove(s: HermesSession) {
  await ElMessageBox.confirm(`硬删除会话 ${s.id} 与其全部消息？不可恢复`, '危险操作', { type: 'warning' })
  await deleteSession(s.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <div class="hs-card" style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap">
      <el-select v-model="query.source" placeholder="来源（全部）" clearable style="width: 180px" @change="load">
        <el-option label="studio" value="studio" />
        <el-option label="api_server" value="api_server" />
        <el-option label="api" value="api" />
        <el-option label="acp" value="acp" />
        <el-option label="cli" value="cli" />
      </el-select>
      <el-input v-model="query.title" placeholder="按标题精确过滤" clearable style="width: 240px" @keyup.enter="load" />
      <el-button type="primary" @click="load">查询</el-button>
      <span class="muted">共 {{ sessions.length }} 条（服务端上限 200/页）</span>
    </div>

    <div class="hs-card">
      <el-table :data="sessions" v-loading="loading" size="small" @row-click="openDetail">
        <el-table-column prop="id" label="会话 ID" min-width="220" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column label="标题/预览" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title || row.preview || '-' }}</template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="100" />
        <el-table-column prop="model" label="模型" width="130" show-overflow-tooltip />
        <el-table-column prop="message_count" label="消息" width="70" />
        <el-table-column prop="tool_call_count" label="工具" width="70" />
        <el-table-column label="tokens(入/出)" width="120">
          <template #default="{ row }">{{ row.input_tokens }}/{{ row.output_tokens }}</template>
        </el-table-column>
        <el-table-column label="开始时间" width="150">
          <template #default="{ row }">{{ fmt(row.started_at) }}</template>
        </el-table-column>
        <el-table-column label="最近活跃" width="150">
          <template #default="{ row }">{{ fmt(row.last_active) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="router.push({ path: '/chat', query: { session: row.id } })">续聊</el-button>
            <el-button link size="small" @click.stop="openDetail(row)">消息</el-button>
            <el-button link size="small" @click.stop="rename(row)">改名</el-button>
            <el-button link size="small" @click.stop="fork(row)">分支</el-button>
            <el-button link size="small" @click.stop="lockModel(row)">模型锁</el-button>
            <el-button link type="danger" size="small" @click.stop="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawer" :title="`会话消息 · ${detailSession?.id || ''}`" size="55%">
      <div v-loading="messagesLoading">
        <div v-for="(m, i) in messages" :key="i" class="msg-block">
          <el-tag size="small" :type="m.role === 'user' ? 'primary' : m.role === 'assistant' ? 'success' : 'info'" effect="plain">
            {{ m.role }}
          </el-tag>
          <pre class="mono msg-pre">{{ msgText(m) }}</pre>
        </div>
        <el-empty v-if="!messages.length && !messagesLoading" description="无消息" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.msg-block { margin-bottom: 14px; }
.msg-pre {
  margin: 6px 0 0;
  background: #f7f8fa;
  border: 1px solid var(--hs-border);
  border-radius: 6px;
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 300px;
  overflow: auto;
}
</style>
