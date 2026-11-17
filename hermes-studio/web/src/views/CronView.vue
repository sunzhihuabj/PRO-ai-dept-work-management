<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createJob, deleteJob, listJobs, pauseJob, resumeJob, runJobNow } from '../api/hermes'
import { useRuntimeStore } from '../stores/runtime'

const runtime = useRuntimeStore()
const jobs = ref<any[]>([])
const loading = ref(false)

// Hermes 能力面声明：jobs_admin=false 时 /api/jobs 管理面不开放（实测 0.22.5 本机为 false）
const jobsAdmin = computed(() => runtime.feature('jobs_admin'))

const form = reactive({ name: '', schedule: '', prompt: '', skills: '' })

async function load() {
  loading.value = true
  try {
    const d = await listJobs(true)
    jobs.value = d?.jobs || d?.data || []
  } catch (e: any) {
    jobs.value = []
  } finally {
    loading.value = false
  }
}

async function add() {
  if (!form.name || !form.schedule) return ElMessage.warning('name 与 schedule 必填')
  await createJob({ name: form.name, schedule: form.schedule, prompt: form.prompt, skills: form.skills || undefined })
  ElMessage.success('已创建')
  Object.assign(form, { name: '', schedule: '', prompt: '', skills: '' })
  load()
}

async function toggle(j: any) {
  if (j.paused) await resumeJob(j.id)
  else await pauseJob(j.id)
  load()
}

async function runNow(j: any) {
  const { value } = await ElMessageBox.prompt('一次性 prompt 上下文（可留空）', `立即触发 ${j.name}`, {})
  await runJobNow(j.id, value || undefined)
  ElMessage.success('已触发')
}

async function remove(j: any) {
  await ElMessageBox.confirm(`删除定时任务 ${j.name}？`, '确认', { type: 'warning' })
  await deleteJob(j.id)
  load()
}

onMounted(async () => {
  if (!runtime.capabilities) await runtime.refreshCapabilities()
  if (jobsAdmin.value) load()
})
</script>

<template>
  <div>
    <el-alert
      v-if="!jobsAdmin"
      type="warning"
      :closable="false"
      title="当前 Hermes 实例未开放定时任务管理 API"
      description="/v1/capabilities 中 jobs_admin=false（本机 v0.22.5 实测）。API 端点（/api/jobs CRUD、pause/resume/run）在源码中存在，上游开放后本页自动可用。"
      style="margin-bottom: 16px"
    />

    <template v-if="jobsAdmin">
      <div class="hs-card">
        <div class="hs-card-title"><el-icon><Plus /></el-icon>新建定时任务</div>
        <el-row :gutter="12">
          <el-col :span="6"><el-input v-model="form.name" placeholder="任务名（必填，≤200）" /></el-col>
          <el-col :span="6"><el-input v-model="form.schedule" placeholder="schedule（自然语言或 cron，必填）" /></el-col>
          <el-col :span="9"><el-input v-model="form.prompt" placeholder="prompt（≤5000）" /></el-col>
          <el-col :span="3"><el-button type="primary" style="width: 100%" @click="add">创建</el-button></el-col>
        </el-row>
      </div>
      <div class="hs-card">
        <el-table :data="jobs" v-loading="loading" size="small">
          <el-table-column prop="name" label="名称" min-width="160" />
          <el-table-column prop="schedule" label="计划" min-width="160" show-overflow-tooltip />
          <el-table-column prop="prompt" label="Prompt" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.paused ? 'info' : 'success'">{{ row.paused ? '已暂停' : '启用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button link size="small" @click="toggle(row)">{{ row.paused ? '恢复' : '暂停' }}</el-button>
              <el-button link type="primary" size="small" @click="runNow(row)">立即触发</el-button>
              <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </div>
</template>
