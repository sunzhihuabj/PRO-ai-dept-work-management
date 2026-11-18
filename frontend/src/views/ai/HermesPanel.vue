<template>
  <div class="page hermes-panel">
    <div class="panel-head">
      <h2>AI Agent 员工</h2>
      <span class="sub">Hermes 委托任务列表（状态/结果/执行时间线）</span>
      <el-button size="small" :loading="loading" @click="load" style="margin-left: auto">
        <el-icon><Refresh /></el-icon>&nbsp;刷新
      </el-button>
    </div>

    <el-table :data="rows" v-loading="loading" size="default" class="panel-table">
      <el-table-column label="Run" width="180">
        <template #default="{ row }">
          <span class="run-id" :title="row.runId">{{ String(row.runId).slice(0, 18) }}…</span>
        </template>
      </el-table-column>
      <el-table-column prop="conversationTitle" label="来源会话" min-width="160" show-overflow-tooltip />
      <el-table-column label="委托时间" width="160">
        <template #default="{ row }">{{ fmtTime(row.delegatedAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.status)" effect="light" round>{{ statusName(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审批" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.adviceId" size="small" :type="adviceTag(row.adviceStatus)" effect="plain">
            {{ adviceName(row.adviceStatus) }} #{{ row.adviceId }}
          </el-tag>
          <span v-else class="dim">—</span>
        </template>
      </el-table-column>
      <el-table-column label="结果摘要" min-width="260">
        <template #default="{ row }">
          <span class="result-preview">{{ row.resultPreview || (row.status === 'RUNNING' ? '执行中…' : '—') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openTimeline(row)">执行时间线</el-button>
          <el-button size="small" text @click="goConversation(row)">去会话</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无委托任务——在 AI 助手中发送「委托 AI Agent 员工…」类指令后在此查看</template>
    </el-table>

    <!-- 执行时间线抽屉（复用 B3 事件接口） -->
    <el-drawer v-model="timelineVisible" :title="`执行时间线 · ${currentRun.slice(0, 18)}…`" size="480px">
      <div v-if="timelineLoading" class="tl-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中…</div>
      <div v-else-if="!timeline.length" class="tl-loading">暂无过程事件（run 可能未产生工具调用或 Hermes 降级）</div>
      <div v-else class="tl-list">
        <div v-for="ev in timeline" :key="ev.id" class="tl-row" :class="{ err: ev.isError === 1 }">
          <div class="tl-head">
            <span class="tl-type">{{ ev.eventType }}</span>
            <span class="tl-tool" v-if="ev.tool">{{ ev.tool }}</span>
            <span class="tl-duration" v-if="ev.durationMs != null">{{ (ev.durationMs / 1000).toFixed(1) }}s</span>
          </div>
          <div class="tl-preview" v-if="ev.preview">{{ ev.preview }}</div>
          <div class="tl-time">{{ fmtTime(ev.createTime) }}</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Loading, Refresh } from '@element-plus/icons-vue'
import { hermesRunEventsApi, type HermesRunEvent } from '@/api/aiws'
import { get } from '@/utils/request'

interface DelegationRow {
  runId: string
  messageId: number
  conversationId: number
  conversationTitle?: string
  delegatorId?: number
  delegatedAt?: string
  status: string
  eventCount: number
  resultPreview?: string
  adviceId?: number
  adviceStatus?: string
}

const router = useRouter()
const rows = ref<DelegationRow[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await get<DelegationRow[]>('/v1/ai/ws/hermes/delegations?pageNum=1&pageSize=50')
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}

// ---- 时间线抽屉 ----
const timelineVisible = ref(false)
const timelineLoading = ref(false)
const timeline = ref<HermesRunEvent[]>([])
const currentRun = ref('')

/** 表格行类型（element-plus slot 默认 DefaultRow，交叉索引签名兼容） */
type Row = DelegationRow & Record<string, any>

async function openTimeline(row: any) {
  currentRun.value = row.runId
  timelineVisible.value = true
  timelineLoading.value = true
  try {
    const res = await hermesRunEventsApi(row.runId)
    timeline.value = res.data || []
  } finally {
    timelineLoading.value = false
  }
}

function goConversation(row: any) {
  router.push({ path: '/ai', query: { conv: row.conversationId } })
}

// ---- 展示辅助 ----
function fmtTime(t?: string) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}
function statusName(s: string) {
  return ({ RUNNING: '执行中', COMPLETED: '已完成', FAILED: '已失败',
    CANCELLED: '已终止', INTERRUPTED: '已中断' } as Record<string, string>)[s] || s
}
function statusTag(s: string) {
  return ({ RUNNING: 'primary', COMPLETED: 'success', FAILED: 'danger',
    CANCELLED: 'info', INTERRUPTED: 'warning' } as Record<string, any>)[s] || 'info'
}
function adviceName(s?: string) {
  return ({ PENDING: '待审批', APPROVED: '已采纳', REJECTED: '已驳回' } as Record<string, string>)[s || ''] || s
}
function adviceTag(s?: string) {
  return ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' } as Record<string, any>)[s || ''] || 'info'
}

onMounted(load)
</script>

<style scoped>
.hermes-panel { padding: 16px 20px; }
.panel-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 14px; }
.panel-head h2 { margin: 0; font-size: 18px; }
.panel-head .sub { font-size: 13px; color: var(--el-text-color-secondary); }
.panel-table { width: 100%; }
.run-id { font-family: monospace; font-size: 12px; }
.dim { color: var(--el-text-color-placeholder); }
.result-preview { font-size: 12px; color: var(--el-text-color-regular); }
.tl-loading { color: var(--el-text-color-secondary); font-size: 13px; padding: 12px 0; }
.tl-list { display: flex; flex-direction: column; gap: 10px; }
.tl-row { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 8px 10px; }
.tl-row.err { border-color: var(--el-color-danger-light-5); }
.tl-head { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.tl-type { font-size: 12px; font-weight: 600; }
.tl-row.err .tl-type { color: var(--el-color-danger); }
.tl-tool { font-family: monospace; font-size: 11px; background: var(--el-fill-color); padding: 1px 5px; border-radius: 4px; }
.tl-duration { font-size: 11px; color: var(--el-text-color-secondary); }
.tl-preview { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 4px; white-space: pre-wrap; word-break: break-word; }
.tl-time { font-size: 11px; color: var(--el-text-color-placeholder); margin-top: 4px; }
</style>
