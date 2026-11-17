<script setup lang="ts">
// Hermes 事件时间线：渲染 /v1/runs SSE 事件与落库事件数组
export interface EvItem {
  event: string
  seq?: number
  timestamp?: number
  tool?: string
  preview?: string
  text?: string
  duration?: number
  durationMs?: number
  error?: boolean | number
  choice?: string
  request_id?: string
  command?: string
  description?: string
  [k: string]: any
}

const props = defineProps<{ events: EvItem[] }>()

const cnNames: Record<string, string> = {
  'run.queued': '任务排队',
  'run.started': '开始执行',
  'message.delta': '输出增量',
  'message.interim': '中途发言',
  'message.started': '开始回复',
  'tool.started': '工具调用',
  'tool.completed': '工具完成',
  'tool.progress': '工具进度',
  'tool.failed': '工具失败',
  'reasoning.available': '思考内容',
  'assistant.delta': '回复增量',
  'assistant.commentary': '助手评论',
  'assistant.completed': '回复完成',
  'subagent.start': '子代理启动',
  'subagent.complete': '子代理完成',
  'approval.request': '⚠ 请求授权',
  'approval.responded': '授权已处理',
  'run.steered': '已注入引导',
  'run.completed': '✅ 执行完成',
  'run.failed': '❌ 执行失败',
  'run.cancelled': '已取消',
  'run.interrupted': '已中断',
  'replay.truncated': '回放截断',
  done: '流结束'
}

function cls(e: EvItem): string {
  if (e.event === 'approval.request' || e.event === 'approval.responded') return 'ev-approval'
  if (e.event?.startsWith('run.') || e.event?.startsWith('done')) return 'ev-run'
  if (e.event === 'tool.failed' || e.error) return 'ev-error'
  if (e.event?.startsWith('tool.')) return 'ev-tool'
  return ''
}

function fmtTime(ts?: number): string {
  if (!ts) return ''
  const ms = ts < 1e12 ? ts * 1000 : ts
  return new Date(ms).toLocaleTimeString('zh-CN', { hour12: false })
}

function dur(e: EvItem): string {
  if (e.durationMs) return `${(e.durationMs / 1000).toFixed(1)}s`
  if (e.duration) return `${Number(e.duration).toFixed(1)}s`
  return ''
}
</script>

<template>
  <div v-if="!props.events.length" class="muted">暂无事件</div>
  <div v-else>
    <div v-for="(e, i) in props.events" :key="i" class="ev-item" :class="cls(e)">
      <div class="ev-title">
        <span>{{ cnNames[e.event] || e.event }}</span>
        <el-tag v-if="e.tool" size="small" type="info" effect="plain" class="mono">{{ e.tool }}</el-tag>
        <el-tag v-if="e.choice" size="small" :type="e.choice === 'deny' ? 'danger' : 'success'" effect="plain">
          {{ e.choice === 'deny' ? '拒绝' : e.choice === 'once' ? '放行一次' : e.choice === 'session' ? '会话内放行' : '永久放行' }}
        </el-tag>
        <span v-if="dur(e)" class="muted">{{ dur(e) }}</span>
        <span class="muted mono">seq {{ e.seq ?? '-' }} · {{ fmtTime(e.timestamp) }}</span>
      </div>
      <div v-if="e.command" class="ev-preview mono">{{ e.command }}</div>
      <div v-else-if="e.description" class="ev-preview">{{ e.description }}</div>
      <div v-else-if="e.preview" class="ev-preview">{{ e.preview }}</div>
      <div v-else-if="e.text" class="ev-preview">{{ e.text }}</div>
    </div>
  </div>
</template>
