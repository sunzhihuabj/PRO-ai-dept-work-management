<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>成本管理（AGT-21 自动归集 · AGT-22 毛利三口径 · F-08 报价模型）</span>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="成本台账" name="ledger">
          <el-form inline>
            <el-form-item label="项目ID">
              <el-input-number v-model="projectId" :min="1" controls-position="right" style="width: 140px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadLedger">查询台账</el-button>
              <el-button type="success" @click="doCollect">自动归集（AGT-21）</el-button>
              <el-button @click="manualVisible = true">手工录入</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="ledger" v-loading="loading" border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="成本类型" width="100">
              <template #default="{ row }">{{ COST_TYPE_LABEL[row.costType] || row.costType }}</template>
            </el-table-column>
            <el-table-column label="来源" width="130">
              <template #default="{ row }">{{ row.sourceType === 'AGENT' ? 'AGT-21 自动归集' : '手工录入' }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="金额（元）" width="130" align="right" />
            <el-table-column prop="occurredDate" label="发生日期" width="110" />
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="完工核算（毛利三口径）" name="settle">
          <el-alert type="info" :closable="false" style="margin-bottom: 12px"
                    title="三口径披露：完全成本=台账全量；标准成本=Σ入库×标准价；现金支出=已完结 PO 部分入库×标准价" />
          <el-form inline>
            <el-form-item label="项目ID">
              <el-input-number v-model="projectId" :min="1" controls-position="right" style="width: 140px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="settling" @click="doSettle">生成核算单（AGT-22）</el-button>
            </el-form-item>
          </el-form>
          <el-descriptions v-if="settlement" :column="3" border style="margin-top: 12px">
            <el-descriptions-item label="核算单号">{{ settlement.settleNo }}</el-descriptions-item>
            <el-descriptions-item label="合同额">{{ fmt(settlement.contractAmount) }}</el-descriptions-item>
            <el-descriptions-item label="毛利率%">{{ settlement.grossMargin }}</el-descriptions-item>
            <el-descriptions-item label="物料成本">{{ fmt(settlement.materialCost) }}</el-descriptions-item>
            <el-descriptions-item label="人工成本">{{ fmt(settlement.laborCost) }}</el-descriptions-item>
            <el-descriptions-item label="费用成本">{{ fmt(settlement.expenseCost) }}</el-descriptions-item>
            <el-descriptions-item label="完全成本">{{ fmt(settlement.totalCost) }}</el-descriptions-item>
            <el-descriptions-item label="标准成本口径">{{ fmt(settlement.standardCost) }}</el-descriptions-item>
            <el-descriptions-item label="现金支出口径">{{ fmt(settlement.cashCost) }}</el-descriptions-item>
          </el-descriptions>
          <el-button v-if="settlement && settlement.id && settlement.status === 'DRAFT'"
                     type="success" style="margin-top: 12px" :loading="saving" @click="doConfirmSettlement">
            核算单定稿（参与报价模型）
          </el-button>
        </el-tab-pane>

        <el-tab-pane label="报价模型（F-08）" name="priceModel">
          <el-button type="primary" plain :loading="saving" @click="doRefreshModel" style="margin-bottom: 12px">刷新模型（按定稿样本重算，version+1）</el-button>
          <el-table :data="priceModels" border>
            <el-table-column label="项目类型" width="120">
              <template #default="{ row }">{{ PRICE_TYPE_LABEL[row.projectType] || row.projectType }}</template>
            </el-table-column>
            <el-table-column prop="avgMargin" label="平均毛利率%" width="120" align="right" />
            <el-table-column prop="minMargin" label="最低毛利率%" width="120" align="right" />
            <el-table-column prop="sampleCount" label="样本数" width="100" align="right" />
            <el-table-column prop="version" label="模型版本" width="100" align="right" />
            <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 手工录入成本 -->
    <el-dialog v-model="manualVisible" title="手工录入人工/费用成本" width="480px">
      <el-form label-width="90px">
        <el-form-item label="项目ID">
          <el-input-number v-model="manualForm.projectId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="成本类型">
          <el-radio-group v-model="manualForm.costType">
            <el-radio value="LABOR">人工</el-radio>
            <el-radio value="EXPENSE">费用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额" required>
          <el-input-number v-model="manualForm.amount" :min="0.01" :precision="2" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="manualForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doAddCost">录入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  COST_TYPE_LABEL,
  listLedgerApi, collectCostApi, addCostApi, settleApi, confirmSettlementApi,
  refreshPriceModelApi, listPriceModelApi
} from '@/api/phase3'
import type { CostLedger, PriceModel } from '@/api/phase3'

const PRICE_TYPE_LABEL: Record<string, string> = { 1: '整机厂', 2: '技改', 3: '研发' }

const activeTab = ref('ledger')
const loading = ref(false)
const settling = ref(false)
const saving = ref(false)
const projectId = ref(1)
const ledger = ref<CostLedger[]>([])
const priceModels = ref<PriceModel[]>([])
const settlement = ref<Record<string, any> | null>(null)
const manualVisible = ref(false)
const manualForm = reactive({ projectId: 1, costType: 'LABOR', amount: 0, remark: '' })

function fmt(v: any) {
  if (v === null || v === undefined) return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function loadLedger() {
  loading.value = true
  try {
    const res = await listLedgerApi(projectId.value)
    ledger.value = res.data
  } finally {
    loading.value = false
  }
}

async function doCollect() {
  saving.value = true
  try {
    const res = await collectCostApi(projectId.value)
    ElMessage.success(`归集完成：新增 ${res.data?.collected ?? '-'} 笔，金额 ${res.data?.totalAmount ?? '-'}`)
    await loadLedger()
  } finally {
    saving.value = false
  }
}

async function doAddCost() {
  saving.value = true
  try {
    await addCostApi({ ...manualForm })
    ElMessage.success('成本已录入')
    manualVisible.value = false
    projectId.value = manualForm.projectId
    await loadLedger()
  } finally {
    saving.value = false
  }
}

async function doSettle() {
  settling.value = true
  try {
    const res = await settleApi(projectId.value)
    settlement.value = res.data
    ElMessage.success('核算单已生成（三口径就绪）')
  } finally {
    settling.value = false
  }
}

async function doConfirmSettlement() {
  if (!settlement.value?.id) return
  saving.value = true
  try {
    await confirmSettlementApi(settlement.value.id)
    ElMessage.success('核算单已定稿（version 将在模型刷新时 +1）')
  } finally {
    saving.value = false
  }
}

async function doRefreshModel() {
  saving.value = true
  try {
    const res = await refreshPriceModelApi()
    priceModels.value = res.data
    ElMessage.success('报价模型已刷新')
  } finally {
    saving.value = false
  }
}

async function loadPriceModels() {
  const res = await listPriceModelApi()
  priceModels.value = res.data
}

onMounted(() => {
  loadLedger()
  loadPriceModels()
})
</script>

<style scoped>
.page-container { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
