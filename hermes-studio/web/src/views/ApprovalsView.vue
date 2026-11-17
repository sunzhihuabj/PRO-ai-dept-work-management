<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRun, resolveRunApproval } from '../api/hermes'
import ApprovalCard from '../components/ApprovalCard.vue'
import StatusTag from '../components/StatusTag.vue'
import { useRuntimeStore, PendingApproval } from '../stores/runtime'

const runtime = useRuntimeStore()
const attachId = ref('')
const attaching = ref(false)

// 说明：/v1/runs 无全量列表端点（调研结论），待审批池由三处来源汇聚：
// ① 聊天页/委托页/工作流运行时捕获的 approval.request 事件 ② 手动接管 runId
async function attach() {
  const id = attachId.value.trim()
  if (!id) return
  attaching.value = true
  try {
    const s = await getRun(id)
    if (s.status === 'waiting_for_approval' && s.approval) {
      runtime.upsertApproval({
        runId: id,
        requestId: s.approval.request_id || '',
        command: s.approval.command || '',
        description: s.approval.description || '',
        choices: s.approval.choices || ['once', 'deny'],
        source: 'manual',
        createdAt: Date.now()
      })
      ElMessage.success('已接入待审批 run')
    } else {
      ElMessage.info(`该 run 当前状态：${s.status}（无待审批）`)
    }
    attachId.value = ''
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    attaching.value = false
  }
}

async function manualResolve(a: PendingApproval, choice: string) {
  await resolveRunApproval(a.runId, { choice, request_id: a.requestId || undefined })
  runtime.markResolved(a.runId, a.requestId, choice)
  ElMessage.success(`已提交 ${choice}`)
}

async function gotoRun() {
  const { value } = await ElMessageBox.prompt('输入 run_id 查看状态', 'Run 状态', {})
  if (!value) return
  const s = await getRun(value.trim())
  ElMessageBox.alert(JSON.stringify(s, null, 2), `Run ${value} 状态`, { customStyle: { maxWidth: '700px' } })
}

onMounted(() => {
  if (!runtime.capabilities) runtime.refreshCapabilities()
})
</script>

<template>
  <div>
    <div class="hs-card" style="display: flex; gap: 10px; align-items: center">
      <el-icon><Stamp /></el-icon>
      <b>待审批池</b>
      <span class="muted">Hermes 的 /v1/runs 无全量列表端点；待审批由聊天/委托/工作流页实时捕获，或在此手动接管 run_id</span>
      <el-input v-model="attachId" placeholder="run_id" style="width: 320px; margin-left: auto" class="mono" @keyup.enter="attach" />
      <el-button type="primary" :loading="attaching" @click="attach">接入</el-button>
      <el-button @click="gotoRun">查 run 状态</el-button>
    </div>

    <div v-for="a in runtime.pendingApprovals" :key="a.runId + a.requestId" class="hs-card">
      <div class="hs-card-title">
        <el-tag size="small" type="warning" effect="dark">{{ a.source }}</el-tag>
        <span class="mono">{{ a.runId }}</span>
        <el-tag v-if="a.resolved" size="small" :type="a.resolved === 'deny' ? 'danger' : 'success'" effect="plain">
          已处理：{{ a.resolved }}
        </el-tag>
      </div>
      <ApprovalCard v-if="!a.resolved" :run-id="a.runId" :payload="a" />
      <div v-else class="muted">处理完毕</div>
    </div>

    <el-empty v-if="!runtime.pendingApprovals.length" description="暂无待审批 · 委托涉及危险命令时 approval.request 会自动出现在这里" />
  </div>
</template>
