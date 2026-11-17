<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getHealthDetailed, getModels, listSessions, getToolsets, getSkills } from '../api/hermes'
import { useRuntimeStore } from '../stores/runtime'

const runtime = useRuntimeStore()
const router = useRouter()
const detailed = ref<any>(null)
const models = ref<any[]>([])
const sessions = ref<any[]>([])
const toolsets = ref<any>(null)
const skills = ref<any>(null)
const loading = ref(true)

const features = computed(() => {
  const f = runtime.capabilities?.features || {}
  const cn: Record<string, string> = {
    chat_completions: 'Chat Completions',
    chat_completions_streaming: '对话流式',
    responses_api: 'Responses API',
    run_submission: '异步委托',
    run_status: 'Run 状态查询',
    run_events_sse: 'Run 事件 SSE',
    run_stop: '运行中断',
    run_steer: '运行中引导',
    run_approval_response: '审批回调',
    approval_events: '审批事件',
    session_resources: '会话管理',
    session_chat: '会话聊天',
    session_chat_streaming: '会话流式聊天',
    session_fork: '会话分支',
    session_model_lock: '会话模型锁',
    model_options: '模型选项',
    skills_api: '技能 API',
    jobs_admin: '定时任务管理'
  }
  return Object.entries(f)
    .filter(([, v]) => typeof v === 'boolean')
    .map(([k, v]) => ({ key: k, name: cn[k] || k, on: v as boolean }))
})

onMounted(async () => {
  try {
    const [d, m, s, t, sk] = await Promise.allSettled([
      getHealthDetailed(),
      getModels(),
      listSessions({ limit: 200 }),
      getToolsets(),
      getSkills()
    ])
    if (d.status === 'fulfilled') detailed.value = d.value
    if (m.status === 'fulfilled') models.value = m.value?.data || []
    if (s.status === 'fulfilled') sessions.value = s.value?.data || []
    if (t.status === 'fulfilled') toolsets.value = t.value
    if (sk.status === 'fulfilled') skills.value = sk.value
  } finally {
    loading.value = false
  }
})

function fmtTime(ts?: number) {
  if (!ts) return '-'
  const ms = ts < 1e12 ? ts * 1000 : ts
  return new Date(ms).toLocaleString('zh-CN', { hour12: false })
}
</script>

<template>
  <div v-loading="loading">
    <el-row :gutter="16">
      <el-col :span="6">
        <div class="hs-card stat">
          <div class="muted">上游服务</div>
          <div class="stat-num" :style="{ color: runtime.healthOk ? '#67c23a' : '#f56c6c' }">
            {{ runtime.healthOk ? '在线' : '离线' }}
          </div>
          <div class="muted">v{{ runtime.health?.version || '-' }} · {{ runtime.health?.platform }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="hs-card stat">
          <div class="muted">模型</div>
          <div class="stat-num">{{ models.length }}</div>
          <div class="muted">/v1/models</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="hs-card stat">
          <div class="muted">会话（近 200）</div>
          <div class="stat-num">{{ sessions.length }}</div>
          <div class="muted">/api/sessions</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="hs-card stat">
          <div class="muted">工具集 / 技能</div>
          <div class="stat-num">{{ (toolsets?.data || toolsets?.toolsets || []).length || '-' }} / {{ (skills?.data || skills?.skills || []).length || '-' }}</div>
          <div class="muted">/v1/toolsets · /v1/skills</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <div class="hs-card">
          <div class="hs-card-title"><el-icon><Medal /></el-icon>API 能力面（/v1/capabilities）</div>
          <div class="feat-grid">
            <el-tooltip v-for="f in features" :key="f.key" :content="f.key" placement="top">
              <div class="feat" :class="{ off: !f.on }">
                <el-icon><component :is="f.on ? 'CircleCheckFilled' : 'CircleCloseFilled'" /></el-icon>
                <span>{{ f.name }}</span>
              </div>
            </el-tooltip>
          </div>
        </div>
        <div class="hs-card">
          <div class="hs-card-title"><el-icon><Clock /></el-icon>最近会话</div>
          <el-table :data="sessions.slice(0, 6)" size="small" @row-click="(r: any) => router.push('/sessions')">
            <el-table-column prop="id" label="会话 ID" min-width="210" show-overflow-tooltip>
              <template #default="{ row }"><span class="mono">{{ row.id }}</span></template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.title || row.preview || '-' }}</template>
            </el-table-column>
            <el-table-column prop="message_count" label="消息" width="70" />
            <el-table-column label="最近活跃" width="160">
              <template #default="{ row }">{{ fmtTime(row.last_active) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="hs-card">
          <div class="hs-card-title"><el-icon><Monitor /></el-icon>网关详情（/health/detailed）</div>
          <pre class="mono detail-pre">{{ JSON.stringify(detailed, null, 2) }}</pre>
        </div>
        <div class="hs-card">
          <div class="hs-card-title"><el-icon><Position /></el-icon>快捷入口</div>
          <el-space wrap>
            <el-button type="primary" @click="router.push('/chat')">开始聊天</el-button>
            <el-button @click="router.push('/runs')">新建委托</el-button>
            <el-button @click="router.push('/workflows')">编排工作流</el-button>
            <el-button @click="runtime.refreshCapabilities(); runtime.refreshHealth()">刷新能力面</el-button>
          </el-space>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat { text-align: left; }
.stat-num { font-size: 30px; font-weight: 700; margin: 4px 0; }
.feat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.feat { display: flex; align-items: center; gap: 6px; font-size: 13px; padding: 6px 10px; background: #f6f9ff; border-radius: 6px; color: #409eff; }
.feat.off { color: #909399; background: #f4f4f5; }
.detail-pre { margin: 0; max-height: 260px; overflow: auto; background: #1e1e28; color: #d4d4d8; padding: 12px; border-radius: 6px; font-size: 12px; }
</style>
