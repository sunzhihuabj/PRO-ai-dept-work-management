<template>
  <div class="app-container">
    <el-tabs v-model="activeTab">
      <!-- ============ Tab 1: 标讯看板 ============ -->
      <el-tab-pane label="标讯看板" name="notices">
        <el-card shadow="never">
          <div class="toolbar">
            <el-select v-model="queryStatus" placeholder="状态" clearable style="width: 130px">
              <el-option v-for="(label, key) in STATUS_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
            <el-input v-model="queryKeyword" placeholder="标讯标题关键词" style="width: 200px" clearable />
            <el-button type="primary" @click="loadTenders">查询</el-button>
            <el-button type="success" @click="importVisible = true">AGT-01 标讯解析导入</el-button>
            <el-button @click="manualVisible = true">手工建档</el-button>
          </div>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
                    title="AGT-01 标讯解析自动推送（Auto 级）；AGT-02/04/05 比对与建议为 Ask 级，此处为同步入口" />
          <el-table v-loading="loading" :data="tenders" border stripe>
            <el-table-column prop="tenderNo" label="标讯编号" width="150" />
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="platform" label="来源平台" width="150" show-overflow-tooltip />
            <el-table-column label="预算" width="110" align="right">
              <template #default="{ row }">{{ formatMoney(row.budget) }}</template>
            </el-table-column>
            <el-table-column prop="deadline" label="截止日期" width="110" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="STATUS_TYPE[row.status] || 'info'" size="small">{{ STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
                         layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end"
                         @current-change="loadTenders" />
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 2: 报价与竞品 ============ -->
      <el-tab-pane label="报价与竞品" name="bid">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header><span>登记投标报价（毛利率自动计算）</span></template>
          <el-form inline>
            <el-input-number v-model="bidForm.tenderId" placeholder="标讯ID" :controls="false" style="width: 120px" />
            <el-input-number v-model="bidForm.price" placeholder="我方报价" :controls="false" style="width: 150px" />
            <el-input-number v-model="bidForm.costEstimate" placeholder="预估成本" :controls="false" style="width: 150px" />
            <el-input v-model="bidForm.strategyText" placeholder="投标策略说明" style="width: 220px" />
            <el-button type="primary" :loading="bidSubmitting" :disabled="!bidForm.tenderId" @click="onSaveBid">登记</el-button>
          </el-form>
        </el-card>
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header><span>报价记录</span></template>
          <el-table v-loading="bidLoading" :data="bids" border size="small">
            <el-table-column prop="tenderId" label="标讯ID" width="90" />
            <el-table-column label="报价" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.price) }}</template>
            </el-table-column>
            <el-table-column label="预估成本" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.costEstimate) }}</template>
            </el-table-column>
            <el-table-column label="毛利率%" width="100" align="right">
              <template #default="{ row }">
                <span :class="{ 'danger-text': row.grossMargin != null && row.grossMargin < 15 }">{{ row.grossMargin ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="strategyText" label="策略" min-width="180" show-overflow-tooltip />
            <el-table-column prop="openResult" label="开标结果" width="160" show-overflow-tooltip />
          </el-table>
        </el-card>
        <el-card shadow="never">
          <template #header><span>竞品开标记录</span></template>
          <el-form inline style="margin-bottom: 8px">
            <el-input-number v-model="compForm.tenderId" placeholder="标讯ID" :controls="false" style="width: 120px" />
            <el-input v-model="compForm.competitorName" placeholder="竞品单位" style="width: 180px" />
            <el-input-number v-model="compForm.price" placeholder="竞品报价" :controls="false" style="width: 150px" />
            <el-button type="primary" :loading="compSubmitting" :disabled="!compForm.tenderId || !compForm.competitorName"
                       @click="onAddCompetitor">录入</el-button>
            <el-button type="warning" :disabled="!compForm.tenderId" @click="onAnalyzeCompetitors">AGT-05 竞品分析</el-button>
          </el-form>
          <el-table v-loading="compLoading" :data="competitors" border size="small">
            <el-table-column prop="tenderId" label="标讯ID" width="90" />
            <el-table-column prop="competitorName" label="竞品单位" min-width="160" />
            <el-table-column label="报价" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.price) }}</template>
            </el-table-column>
            <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- AGT-01 解析导入对话框 -->
    <el-dialog v-model="importVisible" title="AGT-01 标讯解析导入" width="640px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
                title="粘贴标讯公告原文，AGT-01 自动解析项目名称/预算/资质要求/截止时间并建档推送" />
      <el-input v-model="importText" type="textarea" :rows="10" placeholder="粘贴标讯公告原文…" />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importRunning" :disabled="!importText" @click="onImport">解析建档</el-button>
      </template>
    </el-dialog>

    <!-- 手工建档对话框 -->
    <el-dialog v-model="manualVisible" title="手工建档标讯" width="560px">
      <el-form label-width="110px">
        <el-form-item label="标题" required>
          <el-input v-model="manualForm.title" style="width: 320px" />
        </el-form-item>
        <el-form-item label="来源平台">
          <el-input v-model="manualForm.platform" style="width: 240px" />
        </el-form-item>
        <el-form-item label="预算金额">
          <el-input-number v-model="manualForm.budget" :min="0" :controls="false" style="width: 180px" />
        </el-form-item>
        <el-form-item label="资质要求">
          <el-input v-model="manualForm.qualificationReq" type="textarea" :rows="3" style="width: 320px"
                    placeholder="多项用中文分号；分隔" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="manualForm.deadline" type="date" value-format="YYYY-MM-DD" style="width: 180px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSubmitting" :disabled="!manualForm.title" @click="onManualCreate">建档</el-button>
      </template>
    </el-dialog>

    <!-- 标讯详情对话框 -->
    <el-dialog v-model="detailVisible" title="标讯详情与决策辅助" width="820px" top="6vh">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="编号">{{ detail?.tenderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ STATUS_LABEL[detail?.status || ''] || detail?.status }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail?.title }}</el-descriptions-item>
        <el-descriptions-item label="预算">{{ formatMoney(detail?.budget) }}</el-descriptions-item>
        <el-descriptions-item label="截止">{{ detail?.deadline }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">状态推进</el-divider>
      <el-select v-model="newStatus" style="width: 140px" size="small">
        <el-option v-for="(label, key) in STATUS_LABEL" :key="key" :label="label" :value="key" />
      </el-select>
      <el-button type="primary" size="small" style="margin-left: 8px" @click="onUpdateStatus">推进</el-button>
      <el-divider content-position="left">AGT-02 资格比对</el-divider>
      <el-button type="primary" size="small" :loading="qualRunning" @click="onCheckQualification">执行比对</el-button>
      <el-table v-if="qualChecks.length" :data="qualChecks" border size="small" style="margin-top: 8px">
        <el-table-column prop="requirement" label="要求项" min-width="220" show-overflow-tooltip />
        <el-table-column label="结论" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="QUAL_TYPE[row.companyStatus] || 'info'" size="small">{{ QUAL_LABEL[row.companyStatus] || row.companyStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="evidence" label="依据" min-width="220" show-overflow-tooltip />
      </el-table>
      <el-divider content-position="left">AGT-04 报价建议</el-divider>
      <el-button type="warning" size="small" :loading="priceRunning" @click="onSuggestPrice">生成报价区间</el-button>
      <el-descriptions v-if="priceResult" :column="4" border size="small" style="margin-top: 8px">
        <el-descriptions-item label="历史均价">{{ formatMoney(priceResult.historyAvg) }}</el-descriptions-item>
        <el-descriptions-item label="下限">{{ formatMoney(priceResult.range?.low) }}</el-descriptions-item>
        <el-descriptions-item label="建议价">{{ formatMoney(priceResult.range?.suggested) }}</el-descriptions-item>
        <el-descriptions-item label="上限">{{ formatMoney(priceResult.range?.high) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert v-for="(w, i) in priceResult?.warnings || []" :key="i" type="warning" :title="w"
                :closable="false" show-icon style="margin-top: 6px" />
      <el-divider content-position="left">AGT-05 竞品分析</el-divider>
      <el-button type="warning" size="small" :loading="compAnaRunning" @click="onAnalyzeInDetail">生成竞品分析</el-button>
      <el-descriptions v-if="compResult" :column="3" border size="small" style="margin-top: 8px">
        <el-descriptions-item label="竞品数">{{ compResult.competitorCount }}</el-descriptions-item>
        <el-descriptions-item label="均价">{{ formatMoney(compResult.avgPrice) }}</el-descriptions-item>
        <el-descriptions-item label="我方报价">{{ formatMoney(compResult.ourPrice) }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="compResult">
        <div v-for="(s, i) in compResult.strategies" :key="i" class="strategy-item">· {{ s }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  addCompetitorApi, analyzeCompetitorsApi, checkQualificationApi, createTenderApi, getTenderApi,
  importTenderApi, listBidsApi, listCompetitorsApi, listTendersApi, saveBidApi, suggestPriceApi,
  updateTenderStatusApi,
  TENDER_STATUS_LABEL as STATUS_LABEL, TENDER_STATUS_TYPE as STATUS_TYPE,
  type TenderBid, type TenderCompetitor, type TenderNotice
} from '@/api/tender'
import { getTaskDetailApi } from '@/api/agent'

const QUAL_LABEL: Record<string, string> = { SATISFIED: '满足', NOT_SATISFIED: '不满足', UNCERTAIN: '待核实' }
const QUAL_TYPE: Record<string, any> = { SATISFIED: 'success', NOT_SATISFIED: 'danger', UNCERTAIN: 'warning' }

const activeTab = ref('notices')

function formatMoney(v?: number | null): string {
  if (v == null) return '-'
  return Number(v).toLocaleString('zh-CN')
}

// ===== 标讯列表 =====
const tenders = ref<TenderNotice[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const queryStatus = ref('')
const queryKeyword = ref('')

async function loadTenders() {
  loading.value = true
  try {
    const res = await listTendersApi(pageNum.value, pageSize.value, queryStatus.value || undefined, queryKeyword.value || undefined)
    tenders.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

// ===== AGT-01 解析导入 =====
const importVisible = ref(false)
const importText = ref('')
const importRunning = ref(false)

async function onImport() {
  importRunning.value = true
  try {
    const t = await importTenderApi(importText.value)
    const deadline = Date.now() + 60000
    let task: any = null
    while (Date.now() < deadline) {
      const res = await getTaskDetailApi(t.data.id)
      task = res.data?.task
      if (task && ['done', 'failed', 'escalated'].includes(task.status)) break
      await new Promise((r) => setTimeout(r, 1500))
    }
    if (!task || task.status !== 'done') throw new Error(task?.errorMsg || '解析超时')
    ElMessage.success('标讯解析建档完成，已推送商务确认')
    importVisible.value = false
    importText.value = ''
    loadTenders()
  } catch (e: any) {
    ElMessage.error(e?.message || '解析失败')
  } finally {
    importRunning.value = false
  }
}

// ===== 手工建档 =====
const manualVisible = ref(false)
const manualSubmitting = ref(false)
const manualForm = ref({ title: '', platform: '', budget: undefined as number | undefined, qualificationReq: '', deadline: '' })

async function onManualCreate() {
  manualSubmitting.value = true
  try {
    await createTenderApi({
      title: manualForm.value.title,
      platform: manualForm.value.platform || undefined,
      budget: manualForm.value.budget,
      qualificationReq: manualForm.value.qualificationReq || undefined,
      deadline: manualForm.value.deadline || undefined
    })
    ElMessage.success('标讯已建档')
    manualVisible.value = false
    loadTenders()
  } finally {
    manualSubmitting.value = false
  }
}

// ===== 详情与决策辅助 =====
const detailVisible = ref(false)
const detail = ref<TenderNotice | null>(null)
const newStatus = ref('')
const qualChecks = ref<any[]>([])
const qualRunning = ref(false)
const priceRunning = ref(false)
const priceResult = ref<any>(null)
const compAnaRunning = ref(false)
const compResult = ref<any>(null)

async function openDetail(row: any) {
  const res = await getTenderApi(row.id)
  detail.value = res.data
  newStatus.value = row.status
  qualChecks.value = []
  priceResult.value = null
  compResult.value = null
  detailVisible.value = true
}

async function onUpdateStatus() {
  if (!detail.value) return
  await updateTenderStatusApi(detail.value.id, newStatus.value)
  ElMessage.success('状态已推进' + (newStatus.value === 'WON' ? '（tender.won 事件已发出）' : ''))
  detailVisible.value = false
  loadTenders()
}

async function onCheckQualification() {
  if (!detail.value) return
  qualRunning.value = true
  try {
    const res = await checkQualificationApi(detail.value.id)
    qualChecks.value = res.data.checks
    ElMessage.success(`比对完成：满足 ${res.data.satisfied} / 不满足 ${res.data.notSatisfied} / 待核实 ${res.data.uncertain}`)
  } finally {
    qualRunning.value = false
  }
}

async function onSuggestPrice() {
  if (!detail.value) return
  priceRunning.value = true
  try {
    const res = await suggestPriceApi(detail.value.id)
    priceResult.value = res.data
    ElMessage.success('报价区间已生成')
  } finally {
    priceRunning.value = false
  }
}

async function onAnalyzeInDetail() {
  if (!detail.value) return
  compAnaRunning.value = true
  try {
    const res = await analyzeCompetitorsApi(detail.value.id)
    compResult.value = res.data
    ElMessage.success('竞品分析已生成')
  } finally {
    compAnaRunning.value = false
  }
}

// ===== 报价与竞品 =====
const bids = ref<TenderBid[]>([])
const bidLoading = ref(false)
const bidForm = ref({ tenderId: undefined as number | undefined, price: undefined as number | undefined, costEstimate: undefined as number | undefined, strategyText: '' })
const bidSubmitting = ref(false)
const competitors = ref<TenderCompetitor[]>([])
const compLoading = ref(false)
const compForm = ref({ tenderId: undefined as number | undefined, competitorName: '', price: undefined as number | undefined })
const compSubmitting = ref(false)

async function loadBids() {
  bidLoading.value = true
  try {
    const res = await listBidsApi(undefined)
    bids.value = res.data.slice(0, 50)
  } finally {
    bidLoading.value = false
  }
}

async function onSaveBid() {
  bidSubmitting.value = true
  try {
    await saveBidApi({
      tenderId: bidForm.value.tenderId!,
      price: bidForm.value.price,
      costEstimate: bidForm.value.costEstimate,
      strategyText: bidForm.value.strategyText || undefined
    })
    ElMessage.success('报价已登记（毛利率自动计算，<15% 红色预警）')
    bidForm.value = { tenderId: bidForm.value.tenderId, price: undefined, costEstimate: undefined, strategyText: '' }
    loadBids()
  } finally {
    bidSubmitting.value = false
  }
}

async function loadCompetitors() {
  if (!compForm.value.tenderId) return
  compLoading.value = true
  try {
    const res = await listCompetitorsApi(compForm.value.tenderId)
    competitors.value = res.data
  } finally {
    compLoading.value = false
  }
}

async function onAddCompetitor() {
  compSubmitting.value = true
  try {
    await addCompetitorApi({
      tenderId: compForm.value.tenderId!,
      competitorName: compForm.value.competitorName,
      price: compForm.value.price
    })
    ElMessage.success('竞品记录已录入')
    compForm.value.competitorName = ''
    compForm.value.price = undefined
    loadCompetitors()
  } finally {
    compSubmitting.value = false
  }
}

async function onAnalyzeCompetitors() {
  if (!compForm.value.tenderId) return
  const res = await analyzeCompetitorsApi(compForm.value.tenderId)
  compResult.value = res.data
  ElMessage.success('竞品分析已生成，详见控制台返回')
}

onMounted(() => {
  loadTenders()
  loadBids()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.danger-text {
  color: var(--el-color-danger);
  font-weight: 600;
}
.strategy-item {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin-top: 4px;
}
</style>
