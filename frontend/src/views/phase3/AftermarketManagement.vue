<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>售后管理（AGT-23 报修分级 · AGT-24 质保金提醒 · 巡检 · 故障月报）</span>
          <el-button type="primary" @click="repairVisible = true">报修登记（自动分级）</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="报修工单" name="repairs">
          <el-form inline>
            <el-form-item label="分级">
              <el-select v-model="queryLevel" clearable placeholder="全部" style="width: 120px">
                <el-option v-for="(label, key) in REPAIR_LEVEL_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryStatus" clearable placeholder="全部" style="width: 120px">
                <el-option v-for="(label, key) in REPAIR_STATUS_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadRepairs">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="repairs" v-loading="loading" border stripe
                    :empty-text="'暂无报修工单，点击右上角「报修登记」受理（AGT-23 自动分级）'">
            <el-table-column prop="ticketNo" label="工单号" width="150" />
            <el-table-column prop="faultDesc" label="故障描述" min-width="200" show-overflow-tooltip />
            <el-table-column label="分级" width="90">
              <template #default="{ row }">
                <el-tag :type="REPAIR_LEVEL_TYPE[row.level]" size="small">{{ REPAIR_LEVEL_LABEL[row.level] || row.level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="responseSla" label="响应时效" width="110" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'CLOSED' ? 'success' : row.status === 'PROCESSING' ? 'primary' : 'warning'" size="small">
                  {{ REPAIR_STATUS_LABEL[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="handler" label="处理人" width="100" />
            <el-table-column prop="faultTime" label="报修时间" width="160" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="showDiagnosis(row)">诊断建议</el-button>
                <el-button v-if="row.status === 'OPEN'" size="small" type="primary" @click="openStart(row)">受理</el-button>
                <el-button v-if="row.status === 'PROCESSING'" size="small" type="success" @click="openClose(row)">闭环</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total"
                         layout="total, prev, pager, next" style="margin-top: 12px" @current-change="loadRepairs" />
        </el-tab-pane>

        <el-tab-pane label="巡检记录" name="inspections">
          <el-button type="primary" plain @click="inspectionVisible = true" style="margin-bottom: 12px">登记巡检</el-button>
          <el-table :data="inspections" v-loading="inspectionsLoading" border stripe :empty-text="'暂无巡检记录'">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ INSPECTION_TYPE_LABEL[row.inspectionType] || row.inspectionType }}</template>
            </el-table-column>
            <el-table-column prop="inspectionDate" label="巡检日期" width="110" />
            <el-table-column prop="inspector" label="巡检人" width="100" />
            <el-table-column prop="content" label="巡检内容" min-width="180" show-overflow-tooltip />
            <el-table-column prop="issues" label="发现问题" min-width="160" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="质保金（AGT-24）" name="warranties">
          <el-button type="primary" plain @click="warrantyVisible = true" style="margin-bottom: 12px">登记质保</el-button>
          <el-alert type="info" :closable="false" style="margin-bottom: 12px"
                    title="质保期满前 30 天自动提醒（AGT-24，幂等：reminded 标记防重复提醒）" />
          <el-table :data="warranties" v-loading="warrantiesLoading" border stripe :empty-text="'暂无质保登记'">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column prop="warrantyEnd" label="质保期满" width="120" />
            <el-table-column prop="depositAmount" label="质保金（元）" width="130" align="right" />
            <el-table-column label="已提醒" width="90">
              <template #default="{ row }">
                <el-tag :type="row.reminded ? 'success' : 'info'" size="small">{{ row.reminded ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">{{ WARRANTY_STATUS_LABEL[row.status] || row.status }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="故障月报" name="monthly">
          <el-form inline>
            <el-form-item label="月份">
              <el-date-picker v-model="monthValue" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 160px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="reportLoading" @click="loadMonthly">生成月报</el-button>
            </el-form-item>
          </el-form>
          <el-descriptions v-if="monthly" :column="2" border>
            <el-descriptions-item v-for="(v, k) in monthly" :key="k" :label="String(k)">{{ v }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 报修登记 -->
    <el-dialog v-model="repairVisible" title="报修登记（AGT-23 自动分级：紧急/一般/咨询）" width="540px">
      <el-form label-width="90px">
        <el-form-item label="项目ID" required>
          <el-input-number v-model="repairForm.projectId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="故障描述" required>
          <el-input v-model="repairForm.faultDesc" type="textarea" :rows="3"
                    placeholder="描述故障现象（含'安全事故/停机/机组故障'等关键词将判为紧急）" />
        </el-form-item>
        <el-form-item label="报修人">
          <el-input v-model="repairForm.reporter" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="repairVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCreateRepair">登记（自动分级）</el-button>
      </template>
    </el-dialog>

    <!-- 受理 -->
    <el-dialog v-model="startVisible" title="工单受理" width="420px">
      <el-form label-width="90px">
        <el-form-item label="处理人" required>
          <el-input v-model="startForm.handler" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doStart">受理</el-button>
      </template>
    </el-dialog>

    <!-- 闭环 -->
    <el-dialog v-model="closeVisible" title="工单闭环（根因与方案必填）" width="520px">
      <el-form label-width="90px">
        <el-form-item label="根因" required>
          <el-input v-model="closeForm.rootCause" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="解决方案" required>
          <el-input v-model="closeForm.solution" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="success" :loading="saving" @click="doClose">闭环</el-button>
      </template>
    </el-dialog>

    <!-- 诊断建议 -->
    <el-dialog v-model="diagnosisVisible" title="远程诊断建议（AGT-23 确定性引擎）" width="560px">
      <el-descriptions :column="1" border>
        <el-descriptions-item v-for="(v, k) in diagnosis" :key="k" :label="String(k)">{{ v }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 登记巡检 -->
    <el-dialog v-model="inspectionVisible" title="登记巡检" width="540px">
      <el-form label-width="90px">
        <el-form-item label="项目ID" required>
          <el-input-number v-model="inspectionForm.projectId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="inspectionForm.inspectionType">
            <el-radio value="HALF_YEAR">半年检</el-radio>
            <el-radio value="ANNUAL">年度检</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="巡检人" required>
          <el-input v-model="inspectionForm.inspector" style="width: 200px" />
        </el-form-item>
        <el-form-item label="巡检内容">
          <el-input v-model="inspectionForm.content" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="发现问题">
          <el-input v-model="inspectionForm.issues" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inspectionVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doAddInspection">登记</el-button>
      </template>
    </el-dialog>

    <!-- 登记质保 -->
    <el-dialog v-model="warrantyVisible" title="登记质保信息" width="480px">
      <el-form label-width="110px">
        <el-form-item label="项目ID" required>
          <el-input-number v-model="warrantyForm.projectId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="质保期满日" required>
          <el-date-picker v-model="warrantyForm.warrantyEnd" type="date" value-format="YYYY-MM-DD" style="width: 200px" />
        </el-form-item>
        <el-form-item label="质保金金额">
          <el-input-number v-model="warrantyForm.depositAmount" :min="0" :precision="2" controls-position="right" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="warrantyVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doAddWarranty">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  REPAIR_LEVEL_LABEL, REPAIR_LEVEL_TYPE, REPAIR_STATUS_LABEL,
  createRepairApi, listRepairsApi, startRepairApi, closeRepairApi, diagnosisApi,
  addInspectionApi, listInspectionsApi, addWarrantyApi, listWarrantiesApi, monthlyReportApi
} from '@/api/phase3'
import type { RepairTicket, InspectionRecord, WarrantyInfo } from '@/api/phase3'

const INSPECTION_TYPE_LABEL: Record<string, string> = { HALF_YEAR: '半年检', ANNUAL: '年度检' }
const WARRANTY_STATUS_LABEL: Record<string, string> = { ACTIVE: '质保中', CLAIMED: '已申请质保金', EXPIRED: '已过期' }

const activeTab = ref('repairs')
const loading = ref(false)
const inspectionsLoading = ref(false)
const warrantiesLoading = ref(false)
const reportLoading = ref(false)
const saving = ref(false)
const repairs = ref<RepairTicket[]>([])
const inspections = ref<InspectionRecord[]>([])
const warranties = ref<WarrantyInfo[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const queryLevel = ref('')
const queryStatus = ref('')
const monthValue = ref<string>('')
const monthly = ref<Record<string, any> | null>(null)
const diagnosis = ref<Record<string, any> | null>(null)
const diagnosisVisible = ref(false)

const repairVisible = ref(false)
const startVisible = ref(false)
const closeVisible = ref(false)
const inspectionVisible = ref(false)
const warrantyVisible = ref(false)
let current: RepairTicket | null = null

const repairForm = reactive({ projectId: 1, faultDesc: '', reporter: '' })
const startForm = reactive({ handler: '' })
const closeForm = reactive({ rootCause: '', solution: '' })
const inspectionForm = reactive({ projectId: 1, inspectionType: 'HALF_YEAR', inspector: '', content: '', issues: '' })
const warrantyForm = reactive({ projectId: 1, warrantyEnd: '', depositAmount: 0 })

async function loadRepairs() {
  loading.value = true
  try {
    const res = await listRepairsApi(undefined, queryLevel.value || undefined, queryStatus.value || undefined)
    repairs.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadInspections() {
  inspectionsLoading.value = true
  try {
    const res = await listInspectionsApi()
    inspections.value = res.data.records
  } finally {
    inspectionsLoading.value = false
  }
}

async function loadWarranties() {
  warrantiesLoading.value = true
  try {
    const res = await listWarrantiesApi()
    warranties.value = res.data.records
  } finally {
    warrantiesLoading.value = false
  }
}

async function loadMonthly() {
  if (!monthValue.value) {
    ElMessage.warning('请选择月份')
    return
  }
  reportLoading.value = true
  try {
    const res = await monthlyReportApi(monthValue.value)
    monthly.value = res.data
  } finally {
    reportLoading.value = false
  }
}

async function doCreateRepair() {
  if (!repairForm.faultDesc) {
    ElMessage.warning('请填写故障描述')
    return
  }
  saving.value = true
  try {
    const res = await createRepairApi({ ...repairForm })
    const t = res.data
    ElMessage.success(`工单 ${t.ticketNo} 已登记，AGT-23 分级：${REPAIR_LEVEL_LABEL[t.level] || t.level}（SLA ${t.responseSla || '-'}）`)
    repairVisible.value = false
    await loadRepairs()
  } finally {
    saving.value = false
  }
}

function openStart(row: any) {
  current = row
  startForm.handler = ''
  startVisible.value = true
}

async function doStart() {
  if (!current || !startForm.handler) {
    ElMessage.warning('请填写处理人')
    return
  }
  saving.value = true
  try {
    await startRepairApi(current.id, startForm.handler)
    ElMessage.success('已受理')
    startVisible.value = false
    await loadRepairs()
  } finally {
    saving.value = false
  }
}

function openClose(row: any) {
  current = row
  closeForm.rootCause = ''
  closeForm.solution = ''
  closeVisible.value = true
}

async function doClose() {
  if (!current || !closeForm.rootCause || !closeForm.solution) {
    ElMessage.warning('根因与解决方案均为闭环必填')
    return
  }
  saving.value = true
  try {
    await closeRepairApi(current.id, { ...closeForm })
    ElMessage.success('工单已闭环')
    closeVisible.value = false
    await loadRepairs()
  } finally {
    saving.value = false
  }
}

async function showDiagnosis(row: any) {
  const res = await diagnosisApi(row.id)
  diagnosis.value = res.data
  diagnosisVisible.value = true
}

async function doAddInspection() {
  if (!inspectionForm.inspector) {
    ElMessage.warning('请填写巡检人')
    return
  }
  saving.value = true
  try {
    await addInspectionApi({ ...inspectionForm })
    ElMessage.success('巡检已登记')
    inspectionVisible.value = false
    await loadInspections()
  } finally {
    saving.value = false
  }
}

async function doAddWarranty() {
  if (!warrantyForm.warrantyEnd) {
    ElMessage.warning('请选择质保期满日期')
    return
  }
  saving.value = true
  try {
    await addWarrantyApi({ ...warrantyForm })
    ElMessage.success('质保信息已登记')
    warrantyVisible.value = false
    await loadWarranties()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadRepairs()
  loadInspections()
  loadWarranties()
})
</script>

<style scoped>
.page-container { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
