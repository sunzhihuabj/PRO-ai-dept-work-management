<template>
  <div class="hermes-card">
    <div class="hermes-head">
      <el-icon :size="15" class="hermes-icon"><Cpu /></el-icon>
      <span class="hermes-title">AI Agent 员工 · Hermes</span>
      <el-tag :type="stateType" size="small" effect="light" round>{{ stateLabel }}</el-tag>
      <span class="hermes-run">run {{ shortRunId }}</span>
    </div>

    <!-- 正文（后端卡片文案 / 结果 Markdown，去除 runId 内嵌标记） -->
    <div class="hermes-body markdown-body" v-html="bodyHtml"></div>

    <!-- 执行泳道图（V2.0：ECharts 时间轴泳道，工具时段块 + 流程事件标记） -->
    <el-collapse v-if="events.length" class="hermes-timeline-collapse" @change="onCollapseChange">
      <el-collapse-item name="lane" title="执行泳道图">
        <HermesLaneChart v-if="laneVisible" ref="laneChartRef" :events="events" />
      </el-collapse-item>
      <el-collapse-item name="timeline" :title="`执行时间线（${events.length} 条事件）`">
        <div class="hermes-timeline">
          <div v-for="ev in events" :key="ev.id" class="tl-row" :class="{ err: ev.isError === 1 }">
            <el-icon :size="13" class="tl-icon"><component :is="eventIcon(ev.eventType)" /></el-icon>
            <span class="tl-type">{{ eventTypeLabel(ev.eventType) }}</span>
            <span class="tl-tool" v-if="ev.tool">{{ ev.tool }}</span>
            <span class="tl-duration" v-if="ev.durationMs != null">{{ (ev.durationMs / 1000).toFixed(1) }}s</span>
            <div class="tl-preview" v-if="ev.preview">{{ ev.preview }}</div>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Cpu, Loading, CircleCheck, CircleClose, Lock, InfoFilled } from '@element-plus/icons-vue'
import { hermesRunEventsApi, type HermesRunEvent } from '@/api/aiws'
import { renderMarkdown } from '@/utils/sanitize'
import HermesLaneChart from './HermesLaneChart.vue'

const props = defineProps<{ content: string }>()

// 泳道图懒渲染：collapse 展开时挂载（容器有尺寸后才 init ECharts）
const laneVisible = ref(false)
const laneChartRef = ref<InstanceType<typeof HermesLaneChart>>()
function onCollapseChange(names: unknown) {
  const arr = Array.isArray(names) ? names : [String(names)]
  if (arr.map(String).includes('lane')) {
    laneVisible.value = true
    requestAnimationFrame(() => laneChartRef.value?.rerender?.())
  }
}

/** 从消息 content 头部提取 runId 内嵌标记（<!-- hermes-run:xxx -->） */
const RUN_MARKER = /^<!-- hermes-run:([a-zA-Z0-9_-]+) -->/

const runId = computed(() => props.content.match(RUN_MARKER)?.[1] || '')
const bodyHtml = computed(() => renderMarkdown(props.content.replace(RUN_MARKER, '')))
const shortRunId = computed(() => runId.value.slice(0, 18))

// ---- 事件时间线轮询（B3 语义：终态事件出现即停；无事件时短暂轮询后静默退出） ----
const events = ref<HermesRunEvent[]>([])
let pollTimer: ReturnType<typeof setTimeout> | null = null
let polls = 0
/** run.* 终态事件（与后端 STORED_EVENT_TYPES 对齐） */
const TERMINAL_EVENTS = ['run.completed', 'run.failed', 'run.cancelled', 'run.interrupted']
const MAX_POLLS = 300 // 300 × 2s = 10 分钟兜底（超时 stopRun 后事件流关闭）

/** 终态推断：优先终态事件；无事件流时（如 Hermes 降级/重启）靠轮询次数兜底 */
const finished = computed(() =>
  events.value.some(ev => TERMINAL_EVENTS.includes(ev.eventType)))

const stateLabel = computed(() => {
  if (events.value.some(ev => ev.eventType === 'run.completed')) return '已完成'
  if (events.value.some(ev => ev.eventType === 'run.failed')) return '已失败'
  if (events.value.some(ev => ev.eventType === 'run.cancelled' || ev.eventType === 'run.interrupted')) return '已终止'
  if (events.value.some(ev => ev.eventType === 'approval.request' || ev.eventType === 'run.steered')) return '待授权'
  if (!runId.value) return '已受理'
  return finished.value ? '已结束' : '执行中'
})
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const stateType = computed<TagType>(() => {
  const label = stateLabel.value
  if (label === '已完成') return 'success'
  if (label === '已失败') return 'danger'
  if (label === '待授权') return 'warning'
  if (label === '执行中') return 'primary'
  return 'info'
})

const EVENT_LABELS: Record<string, string> = {
  'tool.started': '开始调用工具',
  'tool.completed': '工具执行完成',
  'approval.request': '请求敏感操作授权',
  'message.interim': '中间输出',
  'subagent.start': '子任务开始',
  'subagent.complete': '子任务完成',
  'run.completed': '执行完成',
  'run.failed': '执行失败',
  'run.cancelled': '执行终止',
  'run.interrupted': '执行中断',
  'run.steered': '已注入引导'
}
function eventTypeLabel(t: string) { return EVENT_LABELS[t] || t }
function eventIcon(t: string) {
  if (t === 'run.completed' || t === 'tool.completed') return CircleCheck
  if (t === 'run.failed') return CircleClose
  if (t === 'approval.request' || t === 'run.steered') return Lock
  if (t === 'run.cancelled' || t === 'run.interrupted') return InfoFilled
  return Loading
}

async function load() {
  if (!runId.value) return
  try {
    const res = await hermesRunEventsApi(runId.value)
    events.value = res.data || []
    polls++
    if (!finished.value && polls < MAX_POLLS) {
      pollTimer = setTimeout(load, 2000)
    }
  } catch {
    // 404（非本人/未知 run）或服务异常：静默停止，正文仍可见
  }
}

onMounted(load)
onUnmounted(() => { if (pollTimer) clearTimeout(pollTimer) })
</script>

<style scoped>
.hermes-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 12px 14px;
  background: var(--el-fill-color-extra-light);
  max-width: 100%;
}
.hermes-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.hermes-icon { color: var(--el-color-primary); }
.hermes-title { font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); }
.hermes-run { font-size: 12px; color: var(--el-text-color-secondary); font-family: monospace; }
.hermes-body { margin-top: 10px; font-size: 13px; line-height: 1.65; word-break: break-word; overflow-wrap: anywhere; }
.hermes-body :deep(p) { margin: 0 0 8px; }
.hermes-body :deep(ul), .hermes-body :deep(ol) { margin: 0 0 8px; padding-left: 20px; }
.hermes-body :deep(code) { background: var(--el-fill-color); padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.hermes-body :deep(pre) { background: var(--el-fill-color); padding: 10px; border-radius: 6px; overflow-x: auto; }
.hermes-body :deep(table) { border-collapse: collapse; }
.hermes-body :deep(th), .hermes-body :deep(td) { border: 1px solid var(--el-border-color-lighter); padding: 4px 8px; font-size: 12px; }
.hermes-timeline-collapse { margin-top: 10px; border-top: 1px dashed var(--el-border-color-lighter); }
.hermes-timeline { display: flex; flex-direction: column; gap: 6px; max-height: 220px; overflow-y: auto; }
.tl-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 12px; color: var(--el-text-color-regular); }
.tl-row.err { color: var(--el-color-danger); }
.tl-icon { flex-shrink: 0; color: var(--el-color-primary); }
.tl-type { font-weight: 500; }
.tl-tool { font-family: monospace; font-size: 11px; background: var(--el-fill-color); padding: 1px 5px; border-radius: 4px; }
.tl-duration { color: var(--el-text-color-secondary); }
.tl-preview { flex-basis: 100%; color: var(--el-text-color-secondary); font-size: 12px; white-space: pre-wrap; word-break: break-word; padding-left: 21px; }
</style>
