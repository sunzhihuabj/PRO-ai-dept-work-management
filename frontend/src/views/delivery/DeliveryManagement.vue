<template>
  <div class="app-container">
    <el-tabs v-model="activeTab">
      <!-- ============ Tab 1: 发货与物流 ============ -->
      <el-tab-pane label="发货与物流" name="shipment">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input-number v-model="spQueryProjectId" placeholder="项目ID" :controls="false" style="width: 140px" />
            <el-select v-model="spQueryStatus" placeholder="状态" clearable style="width: 130px">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已发运" value="SUBMITTED" />
            </el-select>
            <el-button type="primary" @click="loadShipments">查询</el-button>
            <el-button type="success" @click="spDialogVisible = true">新建发货单</el-button>
          </div>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
                    title="硬卡点 BR-02：整机厂项目首次发货必须持有 PASS 的 FAI 首件检验报告，否则发货单禁止提交" />
          <el-table v-loading="spLoading" :data="shipments" border stripe>
            <el-table-column prop="shipmentNo" label="发货单号" width="160" />
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column prop="batchNo" label="批次" width="90" />
            <el-table-column prop="qty" label="台数" width="80" align="right" />
            <el-table-column prop="carrier" label="承运商" width="130" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUBMITTED' ? 'success' : 'info'" size="small">
                  {{ row.status === 'SUBMITTED' ? '已发运' : '草稿' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="shippedAt" label="发运时间" width="170" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onSubmitShipment(row)">提交发运</el-button>
                <el-button link type="primary" size="small" @click="openLogistics(row)">物流</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="spPageNum" v-model:page-size="spPageSize" :total="spTotal"
                         layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end"
                         @current-change="loadShipments" />
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 2: 施工日志 ============ -->
      <el-tab-pane label="施工日志" name="clog">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>
            <span>录入施工日志（当前累计工程量：<b class="danger-text">{{ currentProgress }}%</b>，BR-03 30% / BR-04 85% 自动触发验收）</span>
          </template>
          <el-form label-width="110px" style="max-width: 760px">
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="项目 ID" required>
                  <el-input-number v-model="clogForm.projectId" :min="1" :controls="false" style="width: 100%"
                                   @change="loadProgress" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="日志日期">
                  <el-date-picker v-model="clogForm.logDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="累计工程量%" required>
                  <el-input-number v-model="clogForm.progressPct" :min="0" :max="100" :controls="false" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="天气">
                  <el-input v-model="clogForm.weather" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="在场人数">
                  <el-input-number v-model="clogForm.personnelCount" :min="0" :controls="false" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="记录人">
                  <el-input v-model="clogForm.recorder" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="施工内容">
              <el-input v-model="clogForm.workContent" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="clogSubmitting" :disabled="!clogForm.projectId" @click="onAddClog">保存日志</el-button>
              <el-button type="warning" :loading="checkRunning" :disabled="!clogForm.projectId" @click="onCheckAcceptance">
                AGT-19 验收触发检查
              </el-button>
            </el-form-item>
          </el-form>
          <el-alert v-if="checkResult" :type="(checkResult.triggered || []).length > 0 ? 'success' : 'info'" :closable="false" show-icon
                    :title="`当前工程量 ${checkResult.progress}%，本次触发 ${(checkResult.triggered || []).length} 个验收节点`"
                    style="margin-bottom: 8px" />
          <el-table v-if="checkResult && (checkResult.triggered || []).length" :data="checkResult.triggered" border size="small">
            <el-table-column prop="stage" label="阶段" width="100" />
            <el-table-column prop="rule" label="触发规则" />
            <el-table-column prop="acceptanceId" label="验收单ID" width="110" />
          </el-table>
        </el-card>
        <el-card shadow="never">
          <template #header><span>日志历史</span></template>
          <el-table v-loading="clogLoading" :data="clogs" border size="small">
            <el-table-column prop="logDate" label="日期" width="120" />
            <el-table-column prop="weather" label="天气" width="90" />
            <el-table-column prop="personnelCount" label="人数" width="80" align="right" />
            <el-table-column prop="workContent" label="施工内容" min-width="200" show-overflow-tooltip />
            <el-table-column label="累计工程量" width="110" align="right">
              <template #default="{ row }">{{ row.progressPct }}%</template>
            </el-table-column>
            <el-table-column prop="issues" label="当日问题" min-width="140" show-overflow-tooltip />
            <el-table-column prop="recorder" label="记录人" width="100" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 3: 分级验收 ============ -->
      <el-tab-pane label="分级验收" name="accept">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input-number v-model="accQueryProjectId" placeholder="项目ID" :controls="false" style="width: 140px" />
            <el-select v-model="accQueryStage" placeholder="阶段" clearable style="width: 130px">
              <el-option label="首台验收" value="FIRST" />
              <el-option label="中期验收" value="MID" />
              <el-option label="后期验收" value="LATE" />
              <el-option label="终验" value="FINAL" />
            </el-select>
            <el-button type="primary" @click="loadAcceptances">查询</el-button>
            <el-button type="success" @click="accDialogVisible = true">手动创建验收单</el-button>
          </div>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
                    title="硬卡点 BR-05：验收单必须三方签字（业主+监理+我方）齐备才生效；终验生效自动触发验收款收款提醒（AGT-20）" />
          <el-table v-loading="accLoading" :data="acceptances" border stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column label="阶段" width="110" align="center">
              <template #default="{ row }">{{ STAGE_LABEL[row.stage] || row.stage }}</template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="ACC_STATUS_TYPE[row.status] || 'info'" size="small">{{ ACC_STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="触发工程量" width="100" align="right">
              <template #default="{ row }">{{ row.progressPct != null ? row.progressPct + '%' : '-' }}</template>
            </el-table-column>
            <el-table-column label="签字进度（我方/监理/业主）" min-width="180">
              <template #default="{ row }">
                <el-tag :type="row.signedOwner ? 'success' : 'info'" size="small">我方 {{ row.signedOwner ? '✓' : '○' }}</el-tag>
                <el-tag :type="row.signedSupervisor ? 'success' : 'info'" size="small" style="margin-left: 4px">监理 {{ row.signedSupervisor ? '✓' : '○' }}</el-tag>
                <el-tag :type="row.signedClient ? 'success' : 'info'" size="small" style="margin-left: 4px">业主 {{ row.signedClient ? '✓' : '○' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="triggeredBy" label="触发方式" width="90" align="center" />
            <el-table-column prop="acceptedAt" label="生效时间" width="170" />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'PASS'" link type="primary" size="small" @click="openSign(row)">签字</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建发货单对话框 -->
    <el-dialog v-model="spDialogVisible" title="新建发货单" width="520px">
      <el-form label-width="100px">
        <el-form-item label="项目 ID" required>
          <el-input-number v-model="spForm.projectId" :min="1" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item label="工单 ID">
          <el-input-number v-model="spForm.workorderId" :min="1" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="spForm.batchNo" style="width: 160px" />
        </el-form-item>
        <el-form-item label="发货台数" required>
          <el-input-number v-model="spForm.qty" :min="1" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item label="承运商">
          <el-input v-model="spForm.carrier" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="spDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="spSubmitting" @click="onCreateShipment">创建</el-button>
      </template>
    </el-dialog>

    <!-- 物流对话框 -->
    <el-dialog v-model="logDialogVisible" title="物流跟踪" width="640px">
      <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
        <el-descriptions-item label="发货单号">{{ logisticsShipment?.shipmentNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ logisticsShipment?.status === 'SUBMITTED' ? '已发运' : '草稿' }}</el-descriptions-item>
      </el-descriptions>
      <el-form inline>
        <el-select v-model="logForm.eventType" style="width: 130px">
          <el-option label="发运" value="DISPATCH" />
          <el-option label="在途" value="TRANSIT" />
          <el-option label="到货" value="ARRIVED" />
          <el-option label="进场安装" value="INSTALL" />
        </el-select>
        <el-input v-model="logForm.location" placeholder="位置" style="width: 160px" />
        <el-input v-model="logForm.description" placeholder="说明" style="width: 180px" />
        <el-button type="primary" :loading="logSubmitting" @click="onAddLogistics">登记</el-button>
      </el-form>
      <el-table :data="logisticsRecords" border size="small" style="margin-top: 12px" max-height="300">
        <el-table-column prop="eventTime" label="时间" width="170" />
        <el-table-column label="事件" width="100">
          <template #default="{ row }">{{ LOG_EVENT_LABEL[row.eventType] || row.eventType }}</template>
        </el-table-column>
        <el-table-column prop="location" label="位置" width="140" />
        <el-table-column prop="description" label="说明" />
        <el-table-column prop="operator" label="记录人" width="90" />
      </el-table>
    </el-dialog>

    <!-- 新建验收单对话框 -->
    <el-dialog v-model="accDialogVisible" title="手动创建验收单" width="480px">
      <el-form label-width="100px">
        <el-form-item label="项目 ID" required>
          <el-input-number v-model="accForm.projectId" :min="1" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item label="验收阶段" required>
          <el-select v-model="accForm.stage" style="width: 180px">
            <el-option label="首台验收" value="FIRST" />
            <el-option label="中期验收" value="MID" />
            <el-option label="后期验收" value="LATE" />
            <el-option label="终验" value="FINAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="工程量%">
          <el-input-number v-model="accForm.progressPct" :min="0" :max="100" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="accForm.comment" type="textarea" :rows="2" style="width: 260px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="accSubmitting" @click="onCreateAcceptance">创建</el-button>
      </template>
    </el-dialog>

    <!-- 签字对话框 -->
    <el-dialog v-model="signDialogVisible" title="验收单签字（BR-05 三方齐备生效）" width="460px">
      <el-form label-width="100px">
        <el-form-item label="验收单">
          <span>#{{ signRow?.id }}（{{ STAGE_LABEL[signRow?.stage || ''] }} / 项目 {{ signRow?.projectId }}）</span>
        </el-form-item>
        <el-form-item label="签字方" required>
          <el-radio-group v-model="signForm.party">
            <el-radio value="OWNER">我方</el-radio>
            <el-radio value="SUPERVISOR">监理</el-radio>
            <el-radio value="CLIENT">业主</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="验收人">
          <el-input v-model="signForm.acceptor" style="width: 200px" />
        </el-form-item>
        <el-form-item label="验收意见">
          <el-input v-model="signForm.comment" type="textarea" :rows="2" style="width: 260px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="signDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="signSubmitting" @click="onSign">签字确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  addConstructionLogApi, addLogisticsApi, checkAcceptanceApi, createAcceptanceApi, createShipmentApi,
  getProgressApi, listAcceptancesApi, listConstructionLogsApi, listLogisticsApi, listShipmentsApi,
  signAcceptanceApi, submitShipmentApi,
  type Acceptance, type ConstructionLog, type LogisticsRecord, type Shipment
} from '@/api/delivery'

const STAGE_LABEL: Record<string, string> = { FIRST: '首台验收', MID: '中期验收', LATE: '后期验收', FINAL: '终验' }
const ACC_STATUS_LABEL: Record<string, string> = { TRIGGERED: '已触发', PENDING_SIGN: '待签字', PASS: '已生效', FAIL: '不通过' }
const ACC_STATUS_TYPE: Record<string, any> = { TRIGGERED: 'warning', PENDING_SIGN: 'primary', PASS: 'success', FAIL: 'danger' }
const LOG_EVENT_LABEL: Record<string, string> = { DISPATCH: '发运', TRANSIT: '在途', ARRIVED: '到货', INSTALL: '进场安装' }

const activeTab = ref('shipment')

// ===== 发货与物流 =====
const shipments = ref<Shipment[]>([])
const spLoading = ref(false)
const spPageNum = ref(1)
const spPageSize = ref(10)
const spTotal = ref(0)
const spQueryProjectId = ref<number>()
const spQueryStatus = ref('')
const spDialogVisible = ref(false)
const spSubmitting = ref(false)
const spForm = ref({ projectId: undefined as number | undefined, workorderId: undefined as number | undefined, batchNo: '', qty: 1, carrier: '' })

async function loadShipments() {
  spLoading.value = true
  try {
    const res = await listShipmentsApi(spPageNum.value, spPageSize.value, spQueryProjectId.value, spQueryStatus.value || undefined)
    shipments.value = res.data.records
    spTotal.value = res.data.total
  } finally {
    spLoading.value = false
  }
}

async function onCreateShipment() {
  if (!spForm.value.projectId) {
    ElMessage.warning('请填写项目 ID')
    return
  }
  spSubmitting.value = true
  try {
    await createShipmentApi({ ...spForm.value, projectId: spForm.value.projectId! })
    ElMessage.success('发货单已创建（草稿）')
    spDialogVisible.value = false
    loadShipments()
  } finally {
    spSubmitting.value = false
  }
}

async function onSubmitShipment(row: any) {
  await submitShipmentApi(row.id)
  ElMessage.success('已提交发运（BR-02 校验通过）')
  loadShipments()
}

// ===== 物流 =====
const logDialogVisible = ref(false)
const logSubmitting = ref(false)
const logisticsShipment = ref<Shipment | null>(null)
const logisticsRecords = ref<LogisticsRecord[]>([])
const logForm = ref({ eventType: 'TRANSIT', location: '', description: '' })

async function openLogistics(row: any) {
  logisticsShipment.value = row
  logForm.value = { eventType: 'TRANSIT', location: '', description: '' }
  logDialogVisible.value = true
  const res = await listLogisticsApi(row.id)
  logisticsRecords.value = res.data
}

async function onAddLogistics() {
  if (!logisticsShipment.value) return
  logSubmitting.value = true
  try {
    await addLogisticsApi(logisticsShipment.value.id, logForm.value)
    ElMessage.success('物流事件已登记')
    const res = await listLogisticsApi(logisticsShipment.value.id)
    logisticsRecords.value = res.data
  } finally {
    logSubmitting.value = false
  }
}

// ===== 施工日志 =====
const clogForm = ref({
  projectId: undefined as number | undefined,
  logDate: '',
  weather: '',
  personnelCount: undefined as number | undefined,
  workContent: '',
  progressPct: 0,
  issues: '',
  recorder: ''
})
const clogSubmitting = ref(false)
const currentProgress = ref(0)
const clogs = ref<ConstructionLog[]>([])
const clogLoading = ref(false)
const checkRunning = ref(false)
const checkResult = ref<{ progress: number; triggered: any[] } | null>(null)

async function loadProgress() {
  if (!clogForm.value.projectId) return
  const res = await getProgressApi(clogForm.value.projectId)
  currentProgress.value = Number(res.data.progress)
  const logs = await listConstructionLogsApi(clogForm.value.projectId)
  clogs.value = logs.data
  clogLoading.value = false
}

async function onAddClog() {
  clogSubmitting.value = true
  try {
    const res = await addConstructionLogApi({
      projectId: clogForm.value.projectId!,
      logDate: clogForm.value.logDate || undefined,
      weather: clogForm.value.weather || undefined,
      personnelCount: clogForm.value.personnelCount,
      workContent: clogForm.value.workContent || undefined,
      progressPct: clogForm.value.progressPct,
      issues: clogForm.value.issues || undefined,
      recorder: clogForm.value.recorder || undefined
    })
    currentProgress.value = Number(res.data.currentProgress)
    ElMessage.success(`施工日志已保存，当前累计工程量 ${currentProgress.value}%`)
    loadProgress()
  } finally {
    clogSubmitting.value = false
  }
}

async function onCheckAcceptance() {
  checkRunning.value = true
  try {
    const res = await checkAcceptanceApi(clogForm.value.projectId!)
    checkResult.value = res.data
    const n = (res.data.triggered || []).length
    ElMessage.success(n > 0 ? `已触发 ${n} 个验收节点（BR-03/04）` : '未达到触发阈值或已触发过')
    loadAcceptances()
  } finally {
    checkRunning.value = false
  }
}

// ===== 分级验收 =====
const acceptances = ref<Acceptance[]>([])
const accLoading = ref(false)
const accQueryProjectId = ref<number>()
const accQueryStage = ref('')
const accDialogVisible = ref(false)
const accSubmitting = ref(false)
const accForm = ref({ projectId: undefined as number | undefined, stage: 'MID', progressPct: 0, comment: '' })
const signDialogVisible = ref(false)
const signSubmitting = ref(false)
const signRow = ref<Acceptance | null>(null)
const signForm = ref({ party: 'OWNER', acceptor: '', comment: '' })

async function loadAcceptances() {
  accLoading.value = true
  try {
    const res = await listAcceptancesApi(accQueryProjectId.value, accQueryStage.value || undefined)
    acceptances.value = res.data
  } finally {
    accLoading.value = false
  }
}

async function onCreateAcceptance() {
  if (!accForm.value.projectId) {
    ElMessage.warning('请填写项目 ID')
    return
  }
  accSubmitting.value = true
  try {
    await createAcceptanceApi({ ...accForm.value, projectId: accForm.value.projectId! })
    ElMessage.success('验收单已创建')
    accDialogVisible.value = false
    loadAcceptances()
  } finally {
    accSubmitting.value = false
  }
}

function openSign(row: any) {
  signRow.value = row
  signForm.value = { party: 'OWNER', acceptor: '', comment: '' }
  signDialogVisible.value = true
}

async function onSign() {
  if (!signRow.value) return
  signSubmitting.value = true
  try {
    await signAcceptanceApi(signRow.value.id, signForm.value)
    ElMessage.success('已签字（BR-05 三方齐备后生效）')
    signDialogVisible.value = false
    loadAcceptances()
  } finally {
    signSubmitting.value = false
  }
}

onMounted(() => {
  loadShipments()
  loadAcceptances()
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
</style>
