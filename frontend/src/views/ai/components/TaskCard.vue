<template>
  <div class="task-card">
    <div class="task-head">
      <el-icon :size="15" class="task-icon"><Cpu /></el-icon>
      <span class="task-no">Agent 任务 · {{ task?.taskNo || `#${taskId}` }}</span>
      <el-tag :type="STATUS_TYPE[task?.status || ''] || 'info'" size="small" effect="light" round>
        {{ STATUS_LABEL[task?.status || ''] || (task ? '' : '查询中…') }}
      </el-tag>
      <span class="task-cap" v-if="task?.capabilityCode">{{ task.capabilityCode }}</span>
    </div>

    <!-- 指令 -->
    <div class="task-instruction" v-if="task?.instruction">{{ task.instruction }}</div>

    <!-- 待确认操作条 -->
    <div class="task-actions" v-if="task?.status === 'pending_confirm'">
      <el-alert type="warning" :closable="false" show-icon class="confirm-alert"
                title="该能力为建议级，需确认后执行" />
      <div class="action-row">
        <el-button size="small" @click="cancel">取消</el-button>
        <el-button size="small" type="primary" :loading="acting" @click="confirm">确认执行</el-button>
      </div>
    </div>

    <!-- 执行中状态 -->
    <div class="task-running" v-else-if="task && ['queued', 'running'].includes(task.status)">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ task.status === 'queued' ? '排队中…' : 'AI 正在执行…' }}</span>
    </div>

    <!-- 结果 -->
    <template v-else-if="task?.status === 'done'">
      <div class="task-result markdown-body" v-html="resultHtml"></div>
      <div class="action-row feedback-row">
        <span class="feedback-tip">结果反馈：</span>
        <el-button size="small" :type="task.resultFeedback === 'ACCEPTED' ? 'success' : ''"
                   :disabled="!!task.resultFeedback" @click="feedback('ACCEPTED')">采纳</el-button>
        <el-button size="small" :type="task.resultFeedback === 'MODIFIED' ? 'warning' : ''"
                   :disabled="!!task.resultFeedback" @click="feedback('MODIFIED')">已修改</el-button>
        <el-button size="small" :type="task.resultFeedback === 'REJECTED' ? 'danger' : ''"
                   :disabled="!!task.resultFeedback" @click="feedback('REJECTED')">拒绝</el-button>
      </div>
    </template>

    <!-- 失败/终止 -->
    <div class="task-error" v-else-if="task && ['failed', 'cancelled', 'escalated'].includes(task.status)">
      <el-text type="danger" size="small">{{ task.errorMsg || STATUS_LABEL[task.status] }}</el-text>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Cpu, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getTaskDetailApi, confirmTaskApi, cancelTaskApi, feedbackTaskApi, type AgentTask } from '@/api/agent'
import { renderMarkdown } from '@/utils/sanitize'

const props = defineProps<{ taskId: number }>()

const task = ref<AgentTask | null>(null)
const acting = ref(false)
let pollTimer: ReturnType<typeof setTimeout> | null = null

const STATUS_LABEL: Record<string, string> = {
  queued: '排队中', running: '执行中', pending_confirm: '待确认', done: '已完成',
  failed: '失败', escalated: '已升级', cancelled: '已取消', rolled_back: '已回滚'
}
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const STATUS_TYPE: Record<string, TagType> = {
  queued: 'info', running: 'primary', pending_confirm: 'warning', done: 'success',
  failed: 'danger', escalated: 'warning', cancelled: 'info', rolled_back: 'info'
}

/** 终态不再轮询 */
const TERMINAL = ['done', 'failed', 'cancelled', 'escalated', 'rolled_back']

const resultHtml = computed(() => {
  if (!task.value?.resultText) return ''
  try {
    // Agent 结果为 JSON 时提取 resultText 字段友好展示
    return renderMarkdown(extractDisplay(task.value.resultText))
  } catch {
    return renderMarkdown(task.value.resultText)
  }
})

/** 结构化结果提取：JSON 内 result/summary/content 字段优先，否则原文展示 */
function extractDisplay(raw: string): string {
  const t = raw.trim()
  if (t.startsWith('{') && t.endsWith('}')) {
    try {
      const obj = JSON.parse(t)
      for (const key of ['result', 'summary', 'content', 'text', 'answer']) {
        if (typeof obj[key] === 'string' && obj[key]) return obj[key]
      }
      return '```json\n' + JSON.stringify(obj, null, 2) + '\n```'
    } catch { /* 非 JSON 按原文 */ }
  }
  return raw
}

async function load() {
  try {
    const res = await getTaskDetailApi(props.taskId)
    task.value = res.data?.task || null
    if (task.value && !TERMINAL.includes(task.value.status)) {
      pollTimer = setTimeout(load, 2000)
    }
  } catch {
    task.value = null
  }
}

async function confirm() {
  acting.value = true
  try {
    await confirmTaskApi(props.taskId)
    ElMessage.success('已确认，开始执行')
    load()
  } finally {
    acting.value = false
  }
}

async function cancel() {
  acting.value = true
  try {
    await cancelTaskApi(props.taskId)
    ElMessage.info('任务已取消')
    load()
  } finally {
    acting.value = false
  }
}

async function feedback(f: 'ACCEPTED' | 'MODIFIED' | 'REJECTED') {
  try {
    await feedbackTaskApi(props.taskId, f)
    ElMessage.success('反馈已提交（用于能力自主级别评估）')
    load()
  } catch { /* 错误提示由拦截器统一处理 */ }
}

onMounted(load)
onUnmounted(() => { if (pollTimer) clearTimeout(pollTimer) })
</script>

<style scoped>
.task-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--el-fill-color-extra-light);
  max-width: 100%;
}
.task-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.task-icon { color: var(--el-color-primary); }
.task-no { font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); }
.task-cap { font-size: 12px; color: var(--el-text-color-secondary); }
.task-instruction {
  margin-top: 8px; font-size: 13px; color: var(--el-text-color-regular);
  background: var(--el-fill-color); border-radius: 6px; padding: 6px 10px;
  white-space: pre-wrap; word-break: break-word;
}
.task-running { display: flex; align-items: center; gap: 8px; margin-top: 10px; font-size: 13px; color: var(--el-color-primary); }
.task-result { margin-top: 10px; font-size: 13px; line-height: 1.65; word-break: break-word; overflow-wrap: anywhere; }
.task-error { margin-top: 10px; }
.action-row { display: flex; gap: 8px; margin-top: 10px; align-items: center; }
.feedback-row { border-top: 1px dashed var(--el-border-color-lighter); padding-top: 8px; }
.feedback-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.confirm-alert { padding: 6px 10px; }
/* 任务卡内 Markdown 基础排版 */
.task-result :deep(p) { margin: 0 0 8px; }
.task-result :deep(ul), .task-result :deep(ol) { margin: 0 0 8px; padding-left: 20px; }
.task-result :deep(code) { background: var(--el-fill-color); padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.task-result :deep(pre) { background: var(--el-fill-color); padding: 10px; border-radius: 6px; overflow-x: auto; }
.task-result :deep(table) { border-collapse: collapse; }
.task-result :deep(th), .task-result :deep(td) { border: 1px solid var(--el-border-color-lighter); padding: 4px 8px; font-size: 12px; }
</style>
