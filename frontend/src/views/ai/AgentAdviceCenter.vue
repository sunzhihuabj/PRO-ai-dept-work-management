<template>
  <div class="page-container">
    <el-page-header style="margin-bottom: 16px;" @back="$router.back()">
      <template #content>
        <span class="page-header-title">Agent 决策建议</span>
        <span class="page-header-sub">阶段一多 Agent 协同：业务事件驱动建议生成 + 人工审批（HITL）</span>
      </template>
    </el-page-header>

    <el-card shadow="hover">
      <div class="toolbar">
        <el-select v-model="queryStatus" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已采纳" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-select v-model="queryScene" placeholder="全部场景" clearable style="width: 200px" @change="load">
          <el-option label="项目立项预审" value="PROJECT_CREATED" />
          <el-option label="任务创建/指派" value="TASK_CREATED" />
          <el-option label="任务状态流转" value="TASK_STATUS_CHANGED" />
          <el-option label="阶段完成后续" value="PROJECT_PROGRESS" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="load">查询</el-button>
        <div class="spacer" />
        <span class="stat-hint">置信度 &lt;{{ threshold }} 的建议将强制人工审批；含审批动作的建议参数已 HMAC 锁定</span>
      </div>

      <el-table :data="rows" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column label="场景" width="140">
          <template #default="{ row }">
            <el-tag size="small" :type="sceneTag(row.scene)">{{ sceneName(row.scene) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceBizNo" label="来源" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.sourceType }} #{{ row.sourceId }} {{ row.sourceBizNo || '' }}</template>
        </el-table-column>
        <el-table-column prop="title" label="建议标题" min-width="240" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="contentTag(row)">{{ parse(row).type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="86" align="center">
          <template #default="{ row }">
            <span :class="lowConf(row) ? 'conf-low' : ''">{{ fmtConf(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="风险" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="riskTag(row.riskLevel)">{{ row.riskLevel || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批" width="96" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.requiresApproval === 1" type="warning" size="small">需审批</el-tag>
            <el-tag v-else type="info" size="small">纯建议</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="92" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="showDetail(row)">详情</el-button>
            <template v-if="row.status === 'PENDING' && userStore.isAdmin">
              <el-popconfirm title="采纳该建议？含动作的建议将通过 HMAC 校验后执行" @confirm="approve(row)">
                <template #reference>
                  <el-button size="small" type="success">采纳</el-button>
                </template>
              </el-popconfirm>
              <el-popconfirm title="确认驳回该建议？" @confirm="reject(row)">
                <template #reference>
                  <el-button size="small" type="danger">驳回</el-button>
                </template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, total"
        class="mt-8"
        @current-change="load"
      />
    </el-card>

    <!-- 建议详情抽屉 -->
    <el-drawer v-model="detailVisible" title="决策建议详情" size="40%">
      <template v-if="current">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="ID">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="场景">{{ sceneName(current.scene) }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ current.sourceType }} #{{ current.sourceId }} {{ current.sourceBizNo || '' }}</el-descriptions-item>
          <el-descriptions-item label="建议 Agent">{{ current.agentId }}（{{ current.agentRole }}）</el-descriptions-item>
          <el-descriptions-item label="置信度">{{ fmtConf(current) }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">{{ current.riskLevel }}</el-descriptions-item>
          <el-descriptions-item label="审批状态">{{ statusName(current.status) }}</el-descriptions-item>
          <el-descriptions-item label="响应 SLA">{{ current.responseDeadline || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="current.approverComment" label="审批备注">{{ current.approverComment }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="mt-8">建议内容</h4>
        <el-alert v-if="parse(current).type === 'WARNING'" type="warning" :closable="false">
          {{ parse(current).body }}
        </el-alert>
        <el-alert v-else type="info" :closable="false">{{ parse(current).body }}</el-alert>
        <template v-if="Object.keys(parse(current).structured || {}).length">
          <h4 class="mt-8">结构化字段</h4>
          <pre class="json-pre">{{ JSON.stringify(parse(current).structured, null, 2) }}</pre>
        </template>
        <template v-if="evidence(current).length">
          <h4 class="mt-8">依据引用</h4>
          <el-tag v-for="(e, i) in evidence(current)" :key="i" size="small" class="mr-8">
            {{ e.source }}: {{ e.ref }}
          </el-tag>
        </template>
        <template v-if="current.approvalAction">
          <h4 class="mt-8">审批动作（HMAC 锁定）</h4>
          <pre class="json-pre">{{ current.approvalAction }}
{{ current.approvalParamsJson }}</pre>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  pageAdvicesApi, approveAdviceApi, rejectAdviceApi, parseAdviceContent,
  type AgentDecisionAdvice
} from '@/api/agentAdvice'

const userStore = useUserStore()
const threshold = 0.85

const rows = ref<AgentDecisionAdvice[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const queryStatus = ref('')
const queryScene = ref('')
const detailVisible = ref(false)
const current = ref<AgentDecisionAdvice | null>(null)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res: any = await pageAdvicesApi({
      pageNum: pageNum.value, pageSize: pageSize.value,
      status: queryStatus.value || undefined, scene: queryScene.value || undefined
    })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

/** 表格行类型（element-plus slot 默认 DefaultRow，交叉索引签名兼容） */
type Row = AgentDecisionAdvice & Record<string, any>

function showDetail(row: any) {
  current.value = row
  detailVisible.value = true
}

async function approve(row: any) {
  try {
    await approveAdviceApi(row.id!)
    ElMessage.success('已采纳' + (row.approvalAction ? '，动作已执行' : ''))
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '采纳失败')
  }
}

async function reject(row: any) {
  try {
    await rejectAdviceApi(row.id!)
    ElMessage.success('已驳回')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '驳回失败')
  }
}

const parse = (row: any) => parseAdviceContent(row)
const lowConf = (row: any) => (row.confidence ?? 0) < threshold
const fmtConf = (row: any) =>
  row.confidence == null ? '-' : row.confidence.toFixed(2)
const evidence = (row: any): Array<{ source: string; ref: string }> => {
  try { return JSON.parse(row.evidenceJson || '[]') } catch { return [] }
}

function sceneName(s: string) {
  return ({
    PROJECT_CREATED: '立项预审', TASK_CREATED: '任务创建',
    TASK_STATUS_CHANGED: '状态流转', PROJECT_PROGRESS: '阶段完成',
    TOOL_APPROVAL: '写操作确认', HERMES_APPROVAL: 'Hermes 执行授权',
    TASK_OVERDUE_REMIND: '超期催办', PROCESS_SLA_WARNING: '流程健康度',
    ADVISOR: '专业意见'
  } as Record<string, string>)[s] || s
}
function sceneTag(s: string) {
  return ({ PROJECT_CREATED: 'primary', TASK_CREATED: 'success',
    TASK_STATUS_CHANGED: 'warning', PROJECT_PROGRESS: 'primary',
    TOOL_APPROVAL: 'danger', HERMES_APPROVAL: 'danger',
    TASK_OVERDUE_REMIND: 'warning', PROCESS_SLA_WARNING: 'danger',
    ADVISOR: 'primary' } as Record<string, any>)[s] || 'info'
}
function contentTag(row: any) {
  const t = parse(row).type
  return ({ INFO: 'info', WARNING: 'warning', REQUEST: 'warning' } as Record<string, any>)[t] || 'info'
}
function riskTag(r?: string) {
  return ({ LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' } as Record<string, any>)[r || ''] || 'info'
}
function statusTag(s: string) {
  return ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'info' } as Record<string, any>)[s] || 'info'
}
function statusName(s: string) {
  return ({ PENDING: '待处理', APPROVED: '已采纳', REJECTED: '已驳回', EXPIRED: '已过期' } as Record<string, string>)[s] || s
}
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 8px; }
.spacer { flex: 1; }
.stat-hint { color: var(--el-text-color-secondary); font-size: 12px; }
.conf-low { color: var(--el-color-danger); font-weight: 600; }
.json-pre {
  background: var(--el-fill-color-light); padding: 8px; border-radius: 4px;
  font-size: 12px; white-space: pre-wrap; word-break: break-all;
}
.mr-8 { margin-right: 8px; }
</style>
