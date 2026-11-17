<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: string }>()

const map: Record<string, { label: string; type: string }> = {
  queued: { label: '排队中', type: 'info' },
  running: { label: '运行中', type: 'primary' },
  waiting_for_approval: { label: '待审批', type: 'warning' },
  stopping: { label: '停止中', type: 'warning' },
  completed: { label: '已完成', type: 'success' },
  failed: { label: '失败', type: 'danger' },
  cancelled: { label: '已取消', type: 'info' },
  interrupted: { label: '已中断', type: 'info' }
}

const info = computed(() => map[props.status] || { label: props.status || '未知', type: 'info' })
</script>

<template>
  <el-tag :type="info.type as any" size="small" effect="plain">{{ info.label }}</el-tag>
</template>
