<template>
  <div class="agent-workbench">
    <!-- 指令栏 -->
    <el-card class="command-bar">
      <div class="input-row">
        <el-input v-model="instruction" placeholder="输入指令，如：检索知识库里关于吊装风速的规定 / 帮我总结日报"
                  size="large" clearable @keyup.enter="handleSubmit">
          <template #prepend><el-icon><Promotion /></el-icon></template>
        </el-input>
        <el-button type="primary" size="large" :loading="submitting" @click="handleSubmit">执行</el-button>
      </div>
      <div class="scene-chips">
        <el-tag v-for="s in scenes" :key="s.text" class="chip" effect="plain" @click="instruction = s.text">{{ s.label }}</el-tag>
      </div>
    </el-card>

    <el-row :gutter="16">
      <!-- 左：任务列表 -->
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>我的任务（最近 50 条）</span>
              <el-button size="small" text @click="load">刷新</el-button>
            </div>
          </template>
          <div v-loading="loading" class="task-list">
            <div v-for="t in tasks" :key="t.id" class="task-card" :class="'status-' + t.status" @click="showDetail(t)">
              <div class="task-head">
                <span class="task-no">{{ t.taskNo }}</span>
                <el-tag :type="statusType(t.status)" size="small">{{ statusLabel(t.status) }}</el-tag>
                <el-tag v-if="t.aiGenerated === 1" type="warning" size="small" effect="plain">AI 生成</el-tag>
              </div>
              <div class="task-instruction">{{ t.instruction }}</div>
              <div v-if="t.resultText" class="task-result">{{ brief(t.resultText) }}</div>
              <div class="task-actions" @click.stop>
                <el-button v-if="t.status === 'pending_confirm'" size="small" type="success"
                           @click="doConfirm(t)">确认执行</el-button>
                <el-button v-if="['queued', 'pending_confirm'].includes(t.status)" size="small"
                           @click="doCancel(t)">取消</el-button>
                <template v-if="t.status === 'done' && !t.resultFeedback">
                  <el-button size="small" type="success" plain @click="doFeedback(t, 'ACCEPTED')">采纳</el-button>
                  <el-button size="small" type="warning" plain @click="doFeedback(t, 'MODIFIED')">修改</el-button>
                  <el-button size="small" type="danger" plain @click="doFeedback(t, 'REJECTED')">驳回</el-button>
                </template>
                <el-tag v-else-if="t.resultFeedback" size="small" effect="plain">
                  已反馈：{{ feedbackLabel(t.resultFeedback) }}
                </el-tag>
              </div>
              <div class="task-time">{{ t.createTime }}</div>
            </div>
            <el-empty v-if="!loading && tasks.length === 0" description="暂无任务，试试上方指令" />
          </div>
        </el-card>
      </el-col>

      <!-- 右：详情时间线 + 能力自主策略 -->
      <el-col :span="10">
        <el-card class="detail-card">
          <template #header><span>任务详情与流转时间线</span></template>
          <div v-if="detail">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="任务单号">{{ detail.task.taskNo }}</el-descriptions-item>
              <el-descriptions-item label="能力点">{{ detail.task.capabilityCode }}</el-descriptions-item>
              <el-descriptions-item label="状态">{{ statusLabel(detail.task.status) }}</el-descriptions-item>
              <el-descriptions-item label="意图置信度">
                {{ detail.task.intentConfidence != null ? (detail.task.intentConfidence * 100).toFixed(0) + '%' : '-' }}
              </el-descriptions-item>
            </el-descriptions>
            <div v-if="detail.task.resultText" class="detail-result">{{ detail.task.resultText }}</div>
            <el-timeline style="margin-top: 12px">
              <el-timeline-item v-for="e in detail.events" :key="e.id" :timestamp="e.createTime"
                                :type="e.toStatus === 'done' ? 'success' : e.toStatus === 'failed' ? 'danger' : 'primary'">
                {{ statusLabel(e.toStatus) }} — {{ e.detail }}（{{ e.operator }}）
              </el-timeline-item>
            </el-timeline>
          </div>
          <el-empty v-else description="点击左侧任务查看详情" :image-size="60" />
        </el-card>

        <el-card class="policy-card" v-if="userStore.isAdmin">
          <template #header>
            <div class="card-header">
              <span>能力自主策略（渐进自主）</span>
              <el-button size="small" text @click="evaluate">手动评估</el-button>
            </div>
          </template>
          <el-table :data="policies" size="small">
            <el-table-column prop="capabilityCode" label="能力" min-width="150" />
            <el-table-column label="级别" width="110">
              <template #default="{ row }">
                <el-select :model-value="row.autonomyLevel" size="small" @change="(v: number) => changeLevel(row, v)">
                  <el-option label="禁用" :value="0" />
                  <el-option label="建议级" :value="1" />
                  <el-option label="自动级" :value="2" />
                  <el-option label="批量自动级" :value="3" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="采纳/修改/驳回" width="130">
              <template #default="{ row }">{{ row.acceptCount }}/{{ row.modifyCount }}/{{ row.rejectCount }}</template>
            </el-table-column>
            <el-table-column prop="levelChangeNote" label="最近变化" min-width="140" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  submitInstructionApi, getMyTasksApi, getTaskDetailApi, confirmTaskApi,
  cancelTaskApi, feedbackTaskApi, getAutonomyPoliciesApi, adjustLevelApi, evaluateAutonomyApi,
  type AgentTask, type TaskEvent
} from '@/api/agent'

const userStore = useUserStore()
const instruction = ref('')
const submitting = ref(false)
const loading = ref(false)
const tasks = ref<AgentTask[]>([])
const policies = ref<any[]>([])
const detail = ref<{ task: AgentTask; events: TaskEvent[] } | null>(null)
const testingId = ref<number | null>(null)
let pollTimer: number | undefined

const scenes = [
  { label: '查知识库', text: '检索知识库：现场施工安全交底要求' },
  { label: '写日报', text: '总结今天的工作：完成塔筒法兰盘采购对账，推进 BOM 校验问题闭环' },
  { label: '写周报', text: '生成本周周报：完成合同预付款申请，处理现场缺料问题两项' }
]

const STATUS_MAP: Record<string, { label: string; type: any }> = {
  queued: { label: '排队中', type: 'info' },
  running: { label: '执行中', type: 'primary' },
  pending_confirm: { label: '待确认', type: 'warning' },
  done: { label: '已完成', type: 'success' },
  failed: { label: '失败', type: 'danger' },
  escalated: { label: '已升级人工', type: 'danger' },
  cancelled: { label: '已取消', type: 'info' },
  rolled_back: { label: '已回滚', type: 'info' }
}

function statusLabel(s: string) {
  return STATUS_MAP[s]?.label || s
}

function statusType(s: string) {
  return STATUS_MAP[s]?.type || 'info'
}

function feedbackLabel(f: string) {
  return f === 'ACCEPTED' ? '采纳' : f === 'MODIFIED' ? '修改' : '驳回'
}

function brief(text: string) {
  return text.length > 80 ? text.slice(0, 80) + '…' : text
}

async function load() {
  loading.value = true
  try {
    const res = await getMyTasksApi()
    tasks.value = (res.data as any) || []
  } finally {
    loading.value = false
  }
  if (userStore.isAdmin) {
    const p = await getAutonomyPoliciesApi()
    policies.value = (p.data as any) || []
  }
}

async function handleSubmit() {
  if (!instruction.value.trim()) {
    ElMessage.warning('请输入指令')
    return
  }
  submitting.value = true
  try {
    const res = await submitInstructionApi(instruction.value.trim())
    const task = res.data as any
    ElMessage.success(`已受理：${task.taskNo}`)
    instruction.value = ''
    await load()
  } finally {
    submitting.value = false
  }
}

async function showDetail(t: AgentTask) {
  const res = await getTaskDetailApi(t.id)
  detail.value = res.data as any
}

async function doConfirm(t: AgentTask) {
  await confirmTaskApi(t.id)
  ElMessage.success('已确认执行')
  await load()
}

async function doCancel(t: AgentTask) {
  await cancelTaskApi(t.id)
  ElMessage.success('已取消')
  await load()
}

async function doFeedback(t: AgentTask, feedback: 'ACCEPTED' | 'MODIFIED' | 'REJECTED') {
  await feedbackTaskApi(t.id, feedback)
  ElMessage.success('反馈已记录，将影响该能力的自主级别演进')
  await load()
}

async function changeLevel(row: any, level: number) {
  try {
    await adjustLevelApi(row.capabilityCode, level)
    ElMessage.success('级别已调整')
  } finally {
    await load()
  }
}

async function evaluate() {
  const res = await evaluateAutonomyApi()
  ElMessage.success(`评估完成，${(res.data as any)?.changed ?? 0} 个能力点级别变化`)
  await load()
}

onMounted(() => {
  load()
  // 10s 轮询刷新任务状态（queued/running 任务有进展时自动更新）
  pollTimer = window.setInterval(load, 10000)
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})
</script>

<style scoped>
.command-bar { margin-bottom: 16px; }
.input-row { display: flex; gap: 12px; }
.scene-chips { margin-top: 10px; display: flex; gap: 8px; }
.chip { cursor: pointer; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.task-list { max-height: 620px; overflow-y: auto; }
.task-card { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; margin-bottom: 10px; cursor: pointer; transition: box-shadow .2s; }
.task-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.status-pending_confirm { border-left: 3px solid #e6a23c; }
.status-done { border-left: 3px solid #67c23a; }
.status-failed, .status-escalated { border-left: 3px solid #f56c6c; }
.task-head { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
.task-no { font-weight: 600; font-size: 13px; color: #303133; }
.task-instruction { font-size: 13px; color: #606266; margin-bottom: 6px; }
.task-result { font-size: 12px; color: #909399; background: #f5f7fa; border-radius: 4px; padding: 6px 8px; margin-bottom: 6px; }
.task-actions { display: flex; gap: 6px; align-items: center; }
.task-time { font-size: 11px; color: #c0c4cc; margin-top: 6px; }
.detail-card { margin-bottom: 16px; }
.detail-result { margin-top: 10px; background: #f5f7fa; border-radius: 6px; padding: 10px; font-size: 13px; white-space: pre-wrap; max-height: 240px; overflow-y: auto; }
.policy-card { margin-bottom: 16px; }
</style>
