<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resolveRunApproval, runEventsSse, submitRun } from '../api/hermes'
import {
  studioConfig, studioDeleteWorkflow, studioSaveWorkflow, studioWorkflows
} from '../api/hermes'

// 步骤类型：prompt=委托 /v1/runs；approval=人工审批门；delay=延时
interface WfStep {
  type: 'prompt' | 'approval' | 'delay'
  input?: string
  instructions?: string
  message?: string
  seconds?: number
}

interface WfDoc {
  id: string
  name: string
  description: string
  steps: WfStep[]
  createdAt?: number
  updatedAt?: number
}

interface RunLog {
  stepIndex: number
  stepType: string
  status: 'pending' | 'running' | 'done' | 'failed' | 'waiting'
  runId?: string
  output?: string
  error?: string
  events: any[]
}

const workflows = ref<WfDoc[]>([])
const current = ref<WfDoc | null>(null)
const logs = ref<RunLog[]>([])
const running = ref(false)
const paused = ref(false)
const saving = ref(false)
const serverCfg = ref<any>(null)

const canRun = computed(() => !!current.value?.steps.length && !running.value)

async function loadList() {
  workflows.value = await studioWorkflows()
}

async function createNew() {
  const { value } = await ElMessageBox.prompt('工作流名称', '新建', { inputValue: '新工作流' })
  const doc = await studioSaveWorkflow({ name: value || '新工作流', description: '', steps: [] })
  await loadList()
  current.value = doc
}

async function open(w: WfDoc) {
  if (running.value) return ElMessage.warning('运行中不可切换')
  current.value = w
  logs.value = []
}

async function save() {
  if (!current.value) return
  saving.value = true
  try {
    current.value = await studioSaveWorkflow(current.value)
    await loadList()
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function remove(w: WfDoc) {
  await ElMessageBox.confirm(`删除工作流「${w.name}」？`, '确认', { type: 'warning' })
  await studioDeleteWorkflow(w.id)
  if (current.value?.id === w.id) current.value = null
  await loadList()
}

// ---------- 步骤编辑 ----------
function addStep(type: WfStep['type']) {
  if (!current.value) return
  const step: WfStep = type === 'prompt' ? { type, input: '' } : type === 'approval' ? { type, message: '请审批后继续' } : { type, seconds: 5 }
  current.value.steps.push(step)
}
function removeStep(i: number) {
  current.value?.steps.splice(i, 1)
}
function moveStep(i: number, dir: -1 | 1) {
  const steps = current.value?.steps
  if (!steps) return
  const j = i + dir
  if (j < 0 || j >= steps.length) return
  ;[steps[i], steps[j]] = [steps[j], steps[i]]
}

const typeLabel: Record<string, string> = { prompt: 'AI 执行', approval: '审批门', delay: '延时' }
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

// ---------- 运行引擎（浏览器端顺序执行） ----------
async function waitForContinue(): Promise<void> {
  paused.value = true
  await new Promise<void>((resolve) => {
    const timer = window.setInterval(() => {
      if (!paused.value) {
        window.clearInterval(timer)
        resolve()
      }
    }, 300)
  })
}

async function run() {
  if (!current.value) return
  running.value = true
  logs.value = []
  try {
    for (let i = 0; i < current.value.steps.length; i++) {
      const step = current.value.steps[i]
      const log: RunLog = reactive({ stepIndex: i, stepType: step.type, status: 'running', events: [] })
      logs.value.push(log)

      if (step.type === 'delay') {
        await sleep((step.seconds || 1) * 1000)
        log.status = 'done'
        continue
      }

      if (step.type === 'approval') {
        log.status = 'waiting'
        await waitForContinue() // 用户点「继续执行」恢复
        log.status = 'done'
        continue
      }

      // prompt 步骤：投递 /v1/runs + SSE 监听至终态
      const resp = await submitRun({ input: step.input || '', instructions: step.instructions || undefined }, crypto.randomUUID())
      log.runId = resp.run_id
      let settled = false
      await new Promise<void>((resolve) => {
        runEventsSse(resp.run_id, (frame) => {
          let p: any = null
          try {
            p = frame.data ? JSON.parse(frame.data) : null
          } catch {
            return
          }
          if (!p?.event) return
          log.events.push(p)
          if (p.event === 'approval.request') {
            log.status = 'waiting'
            resolveRunApproval(resp.run_id, { choice: 'once', request_id: p.request_id }) // 工作流内默认放行一次
            log.events.push({ event: 'approval.responded', choice: 'once' })
          }
          if (p.event === 'run.completed') {
            log.status = 'done'
            log.output = p.output
            settled = true
            resolve()
          } else if (p.event === 'run.failed' || p.event === 'run.cancelled' || p.event === 'run.interrupted') {
            log.status = 'failed'
            log.error = p.error || p.event
            settled = true
            resolve()
          }
        }).catch((e) => {
          log.status = 'failed'
          log.error = e.message
          resolve()
        })
        // 终态兜底：10 分钟超时
        setTimeout(() => {
          if (!settled) {
            log.status = 'failed'
            log.error = '等待终态超时(10min)'
            resolve()
          }
        }, 600000)
      })
      if (log.status === 'failed') break // 失败即停
    }
    ElMessage.success('工作流执行结束')
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    running.value = false
    paused.value = false
  }
}

function logCls(s: string) {
  return s === 'done' ? 'success' : s === 'failed' ? 'danger' : s === 'waiting' ? 'warning' : 'primary'
}

onMounted(async () => {
  serverCfg.value = await studioConfig().catch(() => null)
  await loadList()
})
</script>

<template>
  <div class="wf-wrap">
    <aside class="hs-card wf-list">
      <el-button type="primary" style="width: 100%; margin-bottom: 10px" @click="createNew">新建工作流</el-button>
      <div v-for="w in workflows" :key="w.id" class="wf-item" :class="{ active: current?.id === w.id }" @click="open(w)">
        <div style="font-weight: 600">{{ w.name }}</div>
        <div class="muted">{{ w.steps?.length || 0 }} 步骤</div>
        <el-icon class="wf-del" @click.stop="remove(w)"><Delete /></el-icon>
      </div>
      <el-empty v-if="!workflows.length" description="暂无工作流" :image-size="50" />
    </aside>

    <section style="flex: 1; min-width: 0">
      <template v-if="current">
        <div class="hs-card">
          <div class="hs-card-title" style="justify-content: space-between">
            <span style="display: flex; gap: 10px; align-items: center; flex: 1">
              <el-input v-model="current.name" style="width: 220px" placeholder="工作流名" />
              <el-input v-model="current.description" style="flex: 1" placeholder="描述" />
            </span>
            <span style="display: flex; gap: 8px">
              <el-button :loading="saving" @click="save">保存</el-button>
              <el-button type="primary" :disabled="!canRun" @click="run">{{ running ? '运行中…' : '▶ 运行' }}</el-button>
              <el-button v-if="paused" type="warning" @click="paused = false">继续执行</el-button>
            </span>
          </div>
          <div class="steps">
            <div v-for="(s, i) in current.steps" :key="i" class="step-card" :class="s.type">
              <div class="step-head">
                <el-tag size="small" :type="s.type === 'approval' ? 'warning' : s.type === 'delay' ? 'info' : 'primary'" effect="dark">
                  {{ i + 1 }} · {{ typeLabel[s.type] }}
                </el-tag>
                <span style="flex: 1"></span>
                <el-button link size="small" :disabled="i === 0" @click="moveStep(i, -1)"><el-icon><Top /></el-icon></el-button>
                <el-button link size="small" @click="moveStep(i, 1)"><el-icon><Bottom /></el-icon></el-button>
                <el-button link type="danger" size="small" @click="removeStep(i)"><el-icon><Delete /></el-icon></el-button>
              </div>
              <el-input
                v-if="s.type === 'prompt'"
                v-model="s.input"
                type="textarea"
                :rows="2"
                placeholder="任务书（每次执行以新 run 投递，支持引用上一步输出请人工编排）"
              />
              <el-input v-if="s.type === 'prompt'" v-model="s.instructions" placeholder="instructions（可选）" style="margin-top: 6px" />
              <el-input v-if="s.type === 'approval'" v-model="s.message" placeholder="审批门提示语" />
              <el-input-number v-if="s.type === 'delay'" v-model="s.seconds" :min="1" :max="3600" placeholder="秒" />
            </div>
          </div>
          <div style="margin-top: 10px; display: flex; gap: 8px">
            <el-button size="small" @click="addStep('prompt')"><el-icon><Plus /></el-icon>AI 执行步骤</el-button>
            <el-button size="small" type="warning" plain @click="addStep('approval')"><el-icon><Stamp /></el-icon>审批门</el-button>
            <el-button size="small" type="info" plain @click="addStep('delay')"><el-icon><Timer /></el-icon>延时</el-button>
            <span class="muted" style="align-self: center">审批门运行到此处会暂停，点「继续执行」放行；AI 步骤遇 approval.request 默认放行一次（once）</span>
          </div>
        </div>

        <div class="hs-card">
          <div class="hs-card-title"><el-icon><VideoPlay /></el-icon>运行日志</div>
          <div v-for="l in logs" :key="l.stepIndex" class="log-row">
            <el-tag size="small" :type="logCls(l.status) as any" effect="dark">
              步骤 {{ l.stepIndex + 1 }} · {{ typeLabel[l.stepType] }} · {{ l.status }}
            </el-tag>
            <span v-if="l.runId" class="mono muted" style="margin-left: 8px">{{ l.runId }}</span>
            <details v-if="l.events?.length" style="margin-top: 6px">
              <summary class="muted">{{ l.events.length }} 条事件</summary>
              <pre class="mono log-pre">{{ JSON.stringify(l.events.map((e) => ({ e: e.event, tool: e.tool, seq: e.seq })), null, 1) }}</pre>
            </details>
            <pre v-if="l.output" class="mono log-pre">{{ l.output }}</pre>
            <el-alert v-if="l.error" :title="l.error" type="error" :closable="false" style="margin-top: 6px" />
          </div>
          <el-empty v-if="!logs.length" description="点击 ▶ 运行后这里展示每一步的执行日志" :image-size="50" />
        </div>
      </template>
      <el-empty v-else description="从左侧选择或新建工作流：AI 执行（/v1/runs）+ 审批门 + 延时 的顺序编排" />
    </section>
  </div>
</template>

<style scoped>
.wf-wrap { display: flex; gap: 16px; align-items: flex-start; }
.wf-list { width: 250px; flex-shrink: 0; }
.wf-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; position: relative; margin-bottom: 6px; border: 1px solid var(--hs-border); }
.wf-item.active { border-color: var(--hs-primary); background: #ecf5ff; }
.wf-del { position: absolute; right: 10px; top: 10px; color: #c0c4cc; }
.wf-del:hover { color: #f56c6c; }
.step-card { border: 1px solid var(--hs-border); border-radius: 8px; padding: 10px 12px; margin-bottom: 8px; }
.step-card.approval { border-left: 4px solid #e6a23c; }
.step-card.prompt { border-left: 4px solid #409eff; }
.step-card.delay { border-left: 4px solid #909399; }
.step-head { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.log-row { border-bottom: 1px dashed var(--hs-border); padding: 8px 0; }
.log-pre { margin: 6px 0 0; background: #f7f8fa; padding: 8px; border-radius: 6px; max-height: 160px; overflow: auto; }
</style>
