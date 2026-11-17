<script setup lang="ts">
// 审批卡：渲染 approval.request 载荷，choices 来自 Hermes 事件（once/session/always/deny 子集）
import { computed } from 'vue'
import { resolveRunApproval } from '../api/hermes'
import { useRuntimeStore } from '../stores/runtime'

const props = defineProps<{
  runId: string
  payload: {
    request_id?: string
    command?: string
    description?: string
    choices?: string[]
    pattern_key?: string
    allow_session?: boolean
    allow_permanent?: boolean
  }
}>()

const emit = defineEmits<{ (e: 'resolved', choice: string, requestId?: string): void }>()
const runtime = useRuntimeStore()
const busy = computed(() => !!runtime.pendingApprovals.find((a) => a.runId === props.runId && a.resolved))

const choiceLabels: Record<string, string> = {
  once: '放行一次',
  session: '会话内放行',
  always: '永久放行',
  deny: '拒绝',
  approve: '同意',
  allow: '同意'
}

async function resolve(choice: string) {
  await resolveRunApproval(props.runId, { choice, request_id: props.payload.request_id })
  runtime.markResolved(props.runId, props.payload.request_id || '', choice)
  emit('resolved', choice, props.payload.request_id)
}
</script>

<template>
  <el-alert type="warning" :closable="false" class="approval-card">
    <template #title>
      <b>Hermes 请求授权</b>
      <span v-if="payload.request_id" class="muted mono" style="margin-left: 8px">request_id: {{ payload.request_id }}</span>
    </template>
    <div v-if="payload.command" class="mono" style="margin: 6px 0; word-break: break-all">
      <b>命令：</b>{{ payload.command }}
    </div>
    <div v-if="payload.description" style="margin: 6px 0; word-break: break-all">
      <b>说明：</b>{{ payload.description }}
    </div>
    <div style="margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap">
      <el-button
        v-for="c in payload.choices || ['once', 'deny']"
        :key="c"
        size="small"
        :type="c === 'deny' ? 'danger' : 'primary'"
        :disabled="busy"
        @click="resolve(c)"
      >
        {{ choiceLabels[c] || c }}
      </el-button>
    </div>
  </el-alert>
</template>

<style scoped>
.approval-card { margin: 8px 0; }
</style>
