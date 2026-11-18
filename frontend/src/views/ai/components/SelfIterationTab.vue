<template>
  <div class="self-iteration">
    <!-- 反馈统计 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-num">{{ stats.total }}</div>
          <div class="stat-label">累计反馈</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card good">
          <div class="stat-num">{{ stats.positive }}</div>
          <div class="stat-label">好评</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card bad">
          <div class="stat-num">{{ stats.negative }}</div>
          <div class="stat-label">差评</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-num">{{ Math.round((stats.approvalRate || 0) * 100) }}%</div>
          <div class="stat-label">好评率</div>
        </div>
      </el-col>
    </el-row>

    <div class="row-actions">
      <el-button size="small" :loading="iterating" @click="iterateNow">立即归因（补跑周 Job）</el-button>
      <el-button size="small" type="primary" :loading="regressing" @click="runRegression">执行黄金指令回归</el-button>
      <el-button size="small" @click="loadSoulVersions">SOUL 版本链</el-button>
    </div>

    <!-- 回归报告 -->
    <el-card v-if="regression" shadow="never" class="sec-card">
      <template #header>
        <div class="sec-head">
          <span>回归报告（{{ regression.runAt || '' }}）</span>
          <el-tag :type="regression.passRate >= 0.9 ? 'success' : 'danger'" size="small" effect="light">
            通过率 {{ Math.round(regression.passRate * 100) }}%（门槛 90%）
          </el-tag>
        </div>
      </template>
      <div class="reg-summary">共 {{ regression.total }} 条：通过 {{ regression.pass }} / 失败 {{ regression.fail }} / 跳过 {{ regression.skip }} · 耗时 {{ regression.costMs }}ms</div>
      <el-table :data="regression.cases" size="small" max-height="260">
        <el-table-column prop="caseNo" label="用例" width="70" />
        <el-table-column prop="scene" label="场景" width="110" />
        <el-table-column prop="instruction" label="指令" min-width="220" show-overflow-tooltip />
        <el-table-column label="期望/实际" width="180">
          <template #default="{ row }">
            <span v-if="row.caseKind === 'ROUTE'">{{ row.expectedRoute }} → {{ row.actualRoute }}</span>
            <span v-else>{{ row.permEndpoint }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.result === 'PASS' ? 'success' : row.result === 'FAIL' ? 'danger' : 'info'" effect="plain">
              {{ row.result }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 补丁建议 -->
    <el-card shadow="never" class="sec-card">
      <template #header>
        <div class="sec-head">
          <span>补丁建议（差评归因 → 人工采纳）</span>
          <el-radio-group v-model="proposalFilter" size="small" @change="loadProposals">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="PROPOSED">待审</el-radio-button>
            <el-radio-button value="ADOPTED">已采纳</el-radio-button>
            <el-radio-button value="REJECTED">已驳回</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table :data="proposals" size="small" v-loading="loadingProposals" empty-text="暂无建议——差评/失败任务归因后自动生成">
        <el-table-column prop="period" label="周期" width="90" />
        <el-table-column label="类别" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ categoryName(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="问题摘要" min-width="180" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="改进建议" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)" effect="light">{{ statusName(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PROPOSED'">
              <el-button size="small" text type="primary" @click="onAdopt(row as any)">采纳</el-button>
              <el-button size="small" text type="danger" @click="onReject(row as any)">驳回</el-button>
            </template>
            <span v-else-if="row.soulVersion" class="dim">SOUL {{ row.soulVersion }}</span>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- SOUL 版本链抽屉 -->
    <el-drawer v-model="soulVisible" title="SOUL.md 版本链（补丁留痕 · 可回滚）" size="520px">
      <div v-if="!soulVersions.length" class="dim">暂无版本记录——首次采纳补丁后自动建链</div>
      <el-timeline v-else>
        <el-timeline-item v-for="v in soulVersions" :key="v.id" :timestamp="`${v.version} · ${fmtTime(v.createTime)}`"
          :type="v.changeType === 'PATCH' ? 'primary' : v.changeType === 'ROLLBACK' ? 'warning' : 'success'">
          <div class="soul-head">
            <el-tag size="small" effect="plain">{{ v.changeType }}</el-tag>
            <span class="dim">{{ v.operator }}</span>
            <el-button v-if="v.changeType !== 'ROLLBACK'" size="small" text type="warning" @click="onRollback(v.version)">回滚到此版本</el-button>
          </div>
          <div class="soul-diff">{{ v.diffSummary }}</div>
          <el-collapse>
            <el-collapse-item title="内容快照">
              <pre class="soul-snapshot">{{ v.contentSnapshot }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  feedbackStatsApi, listProposalsApi, adoptProposalApi, rejectProposalApi, iterateNowApi,
  soulVersionsApi, soulRollbackApi, regressionRunApi,
  type SoulPatchProposal, type SoulChangeLog, type RegressionReport
} from '@/api/aiws'

const stats = ref<{ total: number; positive: number; negative: number; approvalRate: number }>({
  total: 0, positive: 0, negative: 0, approvalRate: 1
})
const proposals = ref<SoulPatchProposal[]>([])
const proposalFilter = ref('')
const loadingProposals = ref(false)
const iterating = ref(false)
const regressing = ref(false)
const regression = ref<RegressionReport | null>(null)
const soulVisible = ref(false)
const soulVersions = ref<SoulChangeLog[]>([])

async function loadStats() {
  const res = await feedbackStatsApi()
  if (res.data) stats.value = res.data
}

async function loadProposals() {
  loadingProposals.value = true
  try {
    const res = await listProposalsApi(proposalFilter.value || undefined)
    proposals.value = res.data || []
  } finally {
    loadingProposals.value = false
  }
}

async function iterateNow() {
  iterating.value = true
  try {
    const res = await iterateNowApi()
    ElMessage.success(`归因完成：${res.data?.proposalsCreated ?? 0} 条建议生成`)
    loadProposals()
  } finally {
    iterating.value = false
  }
}

async function runRegression() {
  regressing.value = true
  try {
    const res = await regressionRunApi()
    regression.value = res.data || null
    ElMessage.success(`回归完成：通过率 ${Math.round((res.data?.passRate ?? 0) * 100)}%`)
  } finally {
    regressing.value = false
  }
}

async function loadSoulVersions() {
  const res = await soulVersionsApi()
  soulVersions.value = res.data || []
  soulVisible.value = true
}

async function onAdopt(row: SoulPatchProposal) {
  const { value } = await ElMessageBox.prompt('确认采纳该建议？将追加写入 SOUL.md（回归通过率 <90% 时会拒绝写入）', '采纳补丁建议', {
    inputPlaceholder: '采纳备注（可空）', inputValue: ''
  })
  const res = await adoptProposalApi(row.id, value || undefined)
  ElMessage.success(`已采纳并写入 SOUL.md（${res.data?.soulVersion}）`)
  loadProposals()
}

async function onReject(row: SoulPatchProposal) {
  const { value } = await ElMessageBox.prompt('确认驳回该建议？', '驳回补丁建议', {
    inputPlaceholder: '驳回理由（可空）', inputValue: ''
  })
  await rejectProposalApi(row.id, value || undefined)
  ElMessage.success('已驳回')
  loadProposals()
}

async function onRollback(version: string) {
  await ElMessageBox.confirm(`SOUL.md 将回滚到 ${version} 快照，当前内容将被覆盖。继续？`, '回滚确认', { type: 'warning' })
  await soulRollbackApi(version)
  ElMessage.success(`已回滚到 ${version}`)
  loadSoulVersions()
}

function categoryName(c: string) {
  return ({ SOUL_PATCH: '口径补丁', TOOL_REQUEST: '工具需求', SKILL_TEMPLATE: '模板沉淀' } as Record<string, string>)[c] || c
}
function statusName(s: string) {
  return ({ PROPOSED: '待审', ADOPTED: '已采纳', REJECTED: '已驳回' } as Record<string, string>)[s] || s
}
function statusTag(s: string) {
  return ({ PROPOSED: 'warning', ADOPTED: 'success', REJECTED: 'danger' } as Record<string, any>)[s] || 'info'
}
function fmtTime(t?: string) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '—'
}

onMounted(() => { loadStats(); loadProposals() })
</script>

<style scoped>
.self-iteration { display: flex; flex-direction: column; gap: 12px; }
.stat-row { margin-bottom: 2px; }
.stat-card { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 10px 14px; text-align: center; }
.stat-card.good .stat-num { color: var(--el-color-success); }
.stat-card.bad .stat-num { color: var(--el-color-danger); }
.stat-num { font-size: 22px; font-weight: 600; }
.stat-label { font-size: 12px; color: var(--el-text-color-secondary); }
.row-actions { display: flex; gap: 8px; }
.sec-card { border-radius: 10px; }
.sec-head { display: flex; align-items: center; gap: 10px; }
.reg-summary { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 8px; }
.dim { color: var(--el-text-color-placeholder); font-size: 12px; }
.soul-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.soul-diff { font-size: 12px; color: var(--el-text-color-regular); margin-bottom: 6px; }
.soul-snapshot { font-size: 11px; white-space: pre-wrap; word-break: break-word; background: var(--el-fill-color-light); padding: 8px; border-radius: 6px; max-height: 200px; overflow: auto; }
</style>
