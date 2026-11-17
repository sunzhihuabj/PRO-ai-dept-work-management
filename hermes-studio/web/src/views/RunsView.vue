<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRun, resolveRunApproval, steerRun, stopRun, submitRun, listSessions } from '../api/hermes'
import { sseReconnect } from '../api/sse'
import StatusTag from '../components/StatusTag.vue'
import EventTimeline, { EvItem } from '../components/EventTimeline.vue'
import ApprovalCard from '../components/ApprovalCard.vue'
import { useRuntimeStore } from '../stores/runtime'

const runtime = useRuntimeStore()

interface RunItem {
  runId: string
  status: string
  input: string
  instructions?: string
  sessionId?: string
  output?: string
  error?: string
  usage?: any
  runtime?: any
  createdAt: number
  events: EvItem[]
  approval?: any
  lastSeq: number
  sseOpen: boolean
  sseState?: string
  polling?: number
}

const form = reactive({
  input: '',
  instructions: '',
  sessionId: '',
  reasoningEffort: '',
  idempotent: true
})
const submitting = ref(false)
const runs = ref<RunItem[]>([])
const sessions = ref<HermesSession[]>([])

const TERMINAL = ['completed', 'failed', 'cancelled', 'interrupted']
const isTerminal = (s: string) => TERMINAL.includes(s)

// ---------- 提交 ----------
async function submit() {
  if (!form.input.trim()) return ElMessage.warning('请输入任务内容')
  submitting.value = true
  try {
    const body: Record<string, any> = { input: form.input }
    if (form.instructions.trim()) body.instructions = form.instructions
    if (form.sessionId.trim()) body.session_id = form.sessionId.trim()
    if (form.reasoningEffort) body.model_options = { reasoning: { effort: form.reasoningEffort } }
    const idem = form.idempotent ? crypto.randomUUID() : undefined
    const resp = await submitRun(body, idem)
    const item: RunItem = {
      runId: resp.run_id,
      status: resp.status || 'queued',
      input: form.input,
      instructions: form.instructions,
      sessionId: body.session_id,
      createdAt: Date.now(),
      events: [],
      lastSeq: -1,
      sseOpen: false
    }
    runs.value.unshift(item)
    form.input = ''
    ElMessage.success(`已投递 run_id=${item.runId}`)
    watchRun(item)
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function loadSessions() {
  try {
    const d = await listSessions({ limit: 50 })
    sessions.value = d?.data || []
  } catch {
    /* 忽略 */
  }
}

// ---------- 监听：SSE 事件流（断线续传）+ 轮询兜底 ----------
function watchRun(item: RunItem) {
  // SSE（断线自动重连 + Last-Event-ID 续传，终态哨兵关闭后停止）
  if (!item.sseOpen) {
    item.sseOpen = true
    sseReconnect(`/api/hermes/v1/runs/${item.runId}/events`, {
      onFrame: (f) => {
        let p: any = null
        try {
          p = f.data ? JSON.parse(f.data) : null
        } catch {
          return
        }
        if (!p) return
        if (typeof p.seq === 'number' && p.seq <= item.lastSeq) return // 重放去重
        if (typeof p.seq === 'number') item.lastSeq = p.seq
        item.events.push(p)
        applyEvent(item, p)
      },
      onStatus: (s) => {
        item.sseState = s
        if (s === 'closed') item.sseOpen = false
      }
    }).catch(() => {
      item.sseOpen = false
      item.sseState = 'closed'
    })
  }
  // 轮询兜底（SSE 断线时状态仍能收敛）
  if (item.polling) window.clearInterval(item.polling)
  item.polling = window.setInterval(async () => {
    try {
      const s = await getRun(item.runId)
      item.status = s.status
      if (s.output) item.output = s.output
      if (s.error) item.error = s.error
      if (s.usage) item.usage = s.usage
      if (s.runtime) item.runtime = s.runtime
      if (s.status === 'waiting_for_approval' && s.approval) applyEvent(item, { event: 'approval.request', ...s.approval })
      if (isTerminal(item.status)) {
        window.clearInterval(item.polling)
        item.polling = undefined
        runtime.removeRunApprovals(item.runId)
      }
    } catch {
      /* 忽略轮询错误 */
    }
  }, 3000)
}

function applyEvent(item: RunItem, p: any) {
  if (p.event === 'approval.request') {
    item.approval = p
    item.status = 'waiting_for_approval'
    runtime.upsertApproval({
      runId: item.runId,
      requestId: p.request_id || '',
      command: p.command || '',
      description: p.description || '',
      choices: p.choices || ['once', 'deny'],
      source: 'run',
      createdAt: Date.now()
    })
  } else if (p.event === 'approval.responded') {
    item.approval = null
  } else if (p.event?.startsWith('run.')) {
    if (p.event === 'run.completed') {
      item.status = 'completed'
      if (p.output) item.output = p.output
      if (p.usage) item.usage = p.usage
    } else if (p.event === 'run.failed') {
      item.status = 'failed'
      item.error = p.error || '执行失败'
    } else if (p.event === 'run.cancelled' || p.event === 'run.interrupted') {
      item.status = p.event === 'run.interrupted' ? 'interrupted' : 'cancelled'
    }
  } else if (!p.event?.includes('delta') && p.status) {
    item.status = p.status
  }
}

// ---------- 操作 ----------
async function steer(item: RunItem) {
  const { value } = await ElMessageBox.prompt('注入引导文本（运行中生效）', 'Steer', {})
  await steerRun(item.runId, { input: value })
  item.events.push({ event: 'run.steered', preview: value })
  ElMessage.success('已注入')
}

async function stop(item: RunItem) {
  await ElMessageBox.confirm(`中断 run ${item.runId}？`, '确认', { type: 'warning' })
  await stopRun(item.runId)
  item.status = 'stopping'
  ElMessage.success('停止请求已发送')
}

async function onApprovalResolved(item: RunItem, choice: string, requestId?: string) {
  item.approval = null
  item.status = 'running'
  item.events.push({ event: 'approval.responded', choice, request_id: requestId })
}

async function manualWatch() {
  const { value } = await ElMessageBox.prompt('输入要接管的 run_id', '接管运行', {})
  if (!value?.trim()) return
  const item: RunItem = {
    runId: value.trim(),
    status: 'running',
    input: '（接管）',
    createdAt: Date.now(),
    events: [],
    lastSeq: -1,
    sseOpen: false
  }
  try {
    const s = await getRun(item.runId)
    item.status = s.status
    item.output = s.output
    item.error = s.error
  } catch (e: any) {
    ElMessage.error(e.message)
    return
  }
  runs.value.unshift(item)
  watchRun(item)
}

onUnmounted(() => {
  runs.value.forEach((r) => r.polling && window.clearInterval(r.polling))
})
loadSessions()
</script>

<template>
  <div>
    <div class="hs-card">
      <div class="hs-card-title"><el-icon><Promotion /></el-icon>投递异步委托（POST /v1/runs）</div>
      <el-input v-model="form.input" type="textarea" :rows="4" placeholder="任务书：交给 Hermes Agent 执行的任务描述（多步骤、需要工具调用的任务）" data-testid="run-input" />
      <el-row :gutter="12" style="margin-top: 10px">
        <el-col :span="8">
          <el-input v-model="form.instructions" placeholder="临时 system prompt（instructions，可选）" clearable />
        </el-col>
        <el-col :span="8">
          <el-select v-model="form.sessionId" placeholder="绑定既有会话（可选）" clearable filterable style="width: 100%">
            <el-option v-for="s in sessions" :key="s.id" :label="s.title || s.id" :value="s.id" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-select v-model="form.reasoningEffort" placeholder="推理档位（可选）" clearable style="width: 100%">
            <el-option label="low" value="low" />
            <el-option label="medium" value="medium" />
            <el-option label="high" value="high" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-checkbox v-model="form.idempotent">幂等键</el-checkbox>
        </el-col>
      </el-row>
      <div style="margin-top: 10px; display: flex; gap: 10px">
        <el-button type="primary" :loading="submitting" data-testid="run-submit" @click="submit">投递任务</el-button>
        <el-button @click="manualWatch">接管已有 run…</el-button>
        <span class="muted" style="align-self: center">202 立即返回 run_id → SSE 事件流 + 3s 轮询兜底</span>
      </div>
    </div>

    <div v-for="r in runs" :key="r.runId" class="hs-card">
      <div class="hs-card-title">
        <StatusTag :status="r.status" />
        <span class="mono" style="font-size: 13px">{{ r.runId }}</span>
        <span class="muted">{{ new Date(r.createdAt).toLocaleTimeString('zh-CN', { hour12: false }) }}</span>
        <el-tag v-if="r.sseState === 'reconnecting'" size="small" type="warning" effect="plain">事件流重连中…</el-tag>
        <el-space style="margin-left: auto">
          <el-button v-if="!isTerminal(r.status)" size="small" @click="steer(r)">引导 Steer</el-button>
          <el-button v-if="!isTerminal(r.status) && r.status !== 'stopping'" size="small" type="danger" plain @click="stop(r)">中断 Stop</el-button>
        </el-space>
      </div>
      <div class="muted" style="margin-bottom: 8px; white-space: pre-wrap">{{ r.input }}</div>
      <div v-if="r.instructions" class="muted mono" style="margin-bottom: 8px">instructions: {{ r.instructions }}</div>
      <ApprovalCard v-if="r.approval" :run-id="r.runId" :payload="r.approval" @resolved="(c, rid) => onApprovalResolved(r, c, rid)" />
      <el-row :gutter="16">
        <el-col :span="14">
          <EventTimeline :events="r.events" />
        </el-col>
        <el-col :span="10">
          <div v-if="r.output" class="run-output">
            <div class="hs-card-title"><el-icon><Document /></el-icon>最终输出</div>
            <pre class="mono out-pre">{{ r.output }}</pre>
          </div>
          <div v-if="r.error" class="run-output">
            <div class="hs-card-title"><el-icon><WarningFilled /></el-icon>错误</div>
            <pre class="mono out-pre err">{{ r.error }}</pre>
          </div>
          <div v-if="r.usage" class="muted">
            tokens：入 {{ r.usage.input_tokens ?? '-' }} / 出 {{ r.usage.output_tokens ?? '-' }} / 总 {{ r.usage.total_tokens ?? '-' }}
            <span v-if="r.runtime"> · {{ r.runtime.provider }}/{{ r.runtime.model }}</span>
          </div>
        </el-col>
      </el-row>
    </div>
    <el-empty v-if="!runs.length" description="暂无委托任务 · 在上方表单投递，或接管已有 run" />
  </div>
</template>

<style scoped>
.out-pre {
  margin: 0 0 8px;
  background: #f7f8fa;
  border: 1px solid var(--hs-border);
  border-radius: 6px;
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 220px;
  overflow: auto;
}
.out-pre.err { background: #fef0f0; border-color: #fbc4c4; }
</style>
