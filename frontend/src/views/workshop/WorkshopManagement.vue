<template>
  <div class="app-container">
    <el-tabs v-model="activeTab">
      <!-- ============ Tab 1: 齐套看板 ============ -->
      <el-tab-pane label="齐套看板" name="kitting">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input-number v-model="kitProjectId" placeholder="项目ID" :controls="false" style="width: 140px" />
            <el-button type="primary" :disabled="!kitProjectId" @click="loadKitting">查询齐套历史</el-button>
            <el-button type="success" :loading="kittingRunning" :disabled="!kitProjectId" @click="onRunKitting('AGT-13')">
              AGT-13 齐套检查
            </el-button>
            <el-button type="warning" :loading="draftRunning" :disabled="!kitProjectId" @click="onRunKitting('AGT-14')">
              AGT-14 缺料请购草稿
            </el-button>
          </div>
          <el-alert v-if="kitResult" :type="kitResult.kitRate >= 95 ? 'success' : 'error'" :closable="false" show-icon
                    :title="`最新齐套率 ${kitResult.kitRate}%（阈值 95%，BR-08 达标才允许工单开工），物料行 ${kitResult.totalItems} 行，缺料 ${kitResult.shortages.length} 行`"
                    style="margin-bottom: 12px" />
          <el-table v-if="kitResult && kitResult.shortages.length" :data="kitResult.shortages" border size="small">
            <el-table-column prop="materialCode" label="物料编码" width="150" />
            <el-table-column prop="materialName" label="物料名称" min-width="160" />
            <el-table-column prop="required" label="需求数量" width="110" align="right" />
            <el-table-column prop="arrived" label="已到数量" width="110" align="right" />
            <el-table-column prop="shortage" label="缺口" width="110" align="right">
              <template #default="{ row }"><span class="danger-text">{{ row.shortage }}</span></template>
            </el-table-column>
          </el-table>
          <el-divider content-position="left">审查历史</el-divider>
          <el-table v-loading="kitLoading" :data="kittingRecords" border size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="齐套率" width="100" align="right">
              <template #default="{ row }">{{ row.kitRate }}%</template>
            </el-table-column>
            <el-table-column label="结论" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.result === 'PASS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="checker" label="审查方" width="110" />
            <el-table-column prop="createTime" label="审查时间" width="170" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 2: PO 管理 ============ -->
      <el-tab-pane label="PO 管理" name="po">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input-number v-model="poQueryProjectId" placeholder="项目ID" :controls="false" style="width: 140px" />
            <el-button type="primary" @click="loadPos">查询</el-button>
            <el-button type="success" @click="poDialogVisible = true">新建 PO</el-button>
            <el-button type="warning" :loading="overdueRunning" :disabled="!poQueryProjectId" @click="onOverdueScan">
              AGT-15 交期盘点
            </el-button>
          </div>
          <el-alert v-if="overdueList" :type="overdueList.overdueCount > 0 ? 'warning' : 'success'" :closable="false" show-icon
                    :title="`逾期未完结 PO：${overdueList.overdueCount} 张`" style="margin-bottom: 12px" />
          <el-table v-loading="poLoading" :data="pos" border stripe>
            <el-table-column prop="poNo" label="PO 编号" width="160" />
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column prop="materialCode" label="物料编码" width="130" />
            <el-table-column prop="materialName" label="物料名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="qty" label="采购量" width="90" align="right" />
            <el-table-column prop="arrivedQty" label="已到量" width="90" align="right" />
            <el-table-column prop="supplier" label="供应商" width="120" show-overflow-tooltip />
            <el-table-column prop="expectedDate" label="期望到货" width="110" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="PO_STATUS_TYPE[row.status] || 'info'" size="small">{{ PO_STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'CLOSED'" link type="primary" size="small" @click="openArrival(row)">到货登记</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="poPageNum" v-model:page-size="poPageSize" :total="poTotal"
                         layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end"
                         @current-change="loadPos" />
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 3: 工单管理 ============ -->
      <el-tab-pane label="工单管理" name="wo">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input-number v-model="woQueryProjectId" placeholder="项目ID" :controls="false" style="width: 140px" />
            <el-select v-model="woQueryStatus" placeholder="状态" clearable style="width: 130px">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已开工" value="STARTED" />
              <el-option label="已完工" value="CLOSED" />
            </el-select>
            <el-button type="primary" @click="loadWorkOrders">查询</el-button>
            <el-button type="success" @click="woDialogVisible = true">新建工单</el-button>
          </div>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
                    title="硬卡点：BR-01 技改项目样机验收未通过禁建批量工单；BR-08 齐套率<95% 禁开工；BR-09 交底未签字禁开工" />
          <el-table v-loading="woLoading" :data="workOrders" border stripe>
            <el-table-column prop="woNo" label="工单编号" width="160" />
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">{{ row.orderType === 'BATCH' ? '批量' : '样机' }}</template>
            </el-table-column>
            <el-table-column prop="qty" label="台数" width="80" align="right" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="WO_STATUS_TYPE[row.status] || 'info'" size="small">{{ WO_STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startedAt" label="开工时间" width="170" />
            <el-table-column prop="closedAt" label="完工时间" width="170" />
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onStartWo(row)">开工</el-button>
                <el-button v-if="row.status === 'STARTED'" link type="warning" size="small" @click="onCloseWo(row)">完工</el-button>
                <el-button v-if="row.status === 'STARTED'" link type="primary" size="small" @click="openAcceptance(row)">样机验收</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="woPageNum" v-model:page-size="woPageSize" :total="woTotal"
                         layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end"
                         @current-change="loadWorkOrders" />
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 4: 扫码过账 ============ -->
      <el-tab-pane label="扫码过账" name="scan">
        <el-card shadow="never" style="max-width: 680px">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
                    title="扫码 → 出入库记录 → T+ 推送队列 → 对账（AGT-16）。入库必须关联 PO 且不超收；出库必须关联工单" />
          <el-form label-width="100px">
            <el-form-item label="过账方向" required>
              <el-radio-group v-model="scanForm.direction">
                <el-radio-button value="IN">采购入库</el-radio-button>
                <el-radio-button value="OUT">生产出库</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item v-if="scanForm.direction === 'IN'" label="PO ID" required>
              <el-input-number v-model="scanForm.poId" :min="1" :controls="false" style="width: 160px" />
            </el-form-item>
            <el-form-item v-else label="工单 ID" required>
              <el-input-number v-model="scanForm.workorderId" :min="1" :controls="false" style="width: 160px" />
            </el-form-item>
            <el-form-item label="物料编码" required>
              <el-input v-model="scanForm.materialCode" placeholder="扫码枪输入或手工填写" style="width: 240px" @keyup.enter="onScan" />
            </el-form-item>
            <el-form-item label="数量" required>
              <el-input-number v-model="scanForm.qty" :min="0.01" :controls="false" style="width: 160px" />
            </el-form-item>
            <el-form-item label="条码内容">
              <el-input v-model="scanForm.barcode" placeholder="选填" style="width: 240px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="scanPosting" @click="onScan">过账</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 5: 出入库记录 ============ -->
      <el-tab-pane label="出入库记录" name="records">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input-number v-model="invQueryProjectId" placeholder="项目ID" :controls="false" style="width: 140px" />
            <el-select v-model="invQueryDirection" placeholder="方向" clearable style="width: 120px">
              <el-option label="入库" value="IN" />
              <el-option label="出库" value="OUT" />
            </el-select>
            <el-select v-model="invQueryStatus" placeholder="T+状态" clearable style="width: 130px">
              <el-option label="待推送" value="PENDING" />
              <el-option label="已推送" value="PUSHED" />
              <el-option label="失败" value="FAILED" />
            </el-select>
            <el-button type="primary" @click="loadInventory">查询</el-button>
            <el-button type="success" :loading="queueProcessing" @click="onProcessQueue">处理 T+ 推送队列</el-button>
          </div>
          <el-table v-loading="invLoading" :data="inventoryRecords" border stripe size="small">
            <el-table-column prop="recordNo" label="单号" width="150" />
            <el-table-column label="方向" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'" size="small">{{ row.direction === 'IN' ? '入库' : '出库' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column prop="materialCode" label="物料编码" width="130" />
            <el-table-column prop="qty" label="数量" width="90" align="right" />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="scannedAt" label="扫码时间" width="170" />
            <el-table-column label="T+ 状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="TPLUS_STATUS_TYPE[row.tplusStatus] || 'info'" size="small">{{ TPLUS_STATUS_LABEL[row.tplusStatus] || row.tplusStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="tplusVoucherNo" label="T+ 凭证号" width="150" />
          </el-table>
          <el-pagination v-model:current-page="invPageNum" v-model:page-size="invPageSize" :total="invTotal"
                         layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end"
                         @current-change="loadInventory" />
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 6: T+ 对账 ============ -->
      <el-tab-pane label="T+ 对账" name="recon">
        <el-card shadow="never">
          <div class="toolbar">
            <el-button type="primary" :loading="reconRunning" @click="onReconcile(false)">立即对账</el-button>
            <el-button type="success" :loading="autoReconRunning" @click="onReconcile(true)">AGT-16 自动对账</el-button>
          </div>
          <el-alert v-if="reconResult" :type="reconResult.diffCount > 0 ? 'warning' : 'success'" :closable="false" show-icon
                    :title="`批次 ${reconResult.batchNo}：本地 ${reconResult.localCount} 单 / T+ ${reconResult.tplusCount} 单 / 一致 ${reconResult.matchedCount} / 差异 ${reconResult.diffCount}${autoRate ? ' / T+ 成功率 ' + autoRate : ''}`"
                    style="margin: 12px 0" />
          <el-table v-if="reconDiffs.length" :data="reconDiffs" border size="small" style="margin-bottom: 12px">
            <el-table-column prop="type" label="差异类型" width="200" />
            <el-table-column prop="recordNo" label="单号" width="150" />
            <el-table-column prop="materialCode" label="物料编码" width="140" />
            <el-table-column prop="localQty" label="本地数量" width="110" align="right" />
            <el-table-column prop="tplusQty" label="T+ 数量" width="110" align="right" />
          </el-table>
          <el-divider content-position="left">对账批次历史</el-divider>
          <el-table v-loading="reconLoading" :data="reconciliations" border size="small">
            <el-table-column prop="batchNo" label="批次号" width="200" />
            <el-table-column prop="reconcileDate" label="对账日期" width="120" />
            <el-table-column prop="localCount" label="本地单数" width="100" align="right" />
            <el-table-column prop="tplusCount" label="T+ 单数" width="100" align="right" />
            <el-table-column prop="matchedCount" label="一致" width="90" align="right" />
            <el-table-column label="差异" width="90" align="right">
              <template #default="{ row }">
                <span :class="{ 'danger-text': row.diffCount > 0 }">{{ row.diffCount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="对账时间" min-width="170" />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建 PO 对话框 -->
    <el-dialog v-model="poDialogVisible" title="新建采购订单" width="520px">
      <el-form label-width="100px">
        <el-form-item label="项目 ID" required>
          <el-input-number v-model="poForm.projectId" :min="1" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item label="物料编码" required>
          <el-input v-model="poForm.materialCode" style="width: 200px" />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input v-model="poForm.materialName" style="width: 200px" />
        </el-form-item>
        <el-form-item label="采购数量" required>
          <el-input-number v-model="poForm.qty" :min="0.01" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="poForm.supplier" style="width: 200px" />
        </el-form-item>
        <el-form-item label="期望到货">
          <el-date-picker v-model="poForm.expectedDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="poDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="poSubmitting" @click="onCreatePo">创建</el-button>
      </template>
    </el-dialog>

    <!-- 新建工单对话框 -->
    <el-dialog v-model="woDialogVisible" title="新建生产工单" width="480px">
      <el-form label-width="100px">
        <el-form-item label="项目 ID" required>
          <el-input-number v-model="woForm.projectId" :min="1" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item label="工单类型" required>
          <el-radio-group v-model="woForm.orderType">
            <el-radio value="PROTOTYPE">样机</el-radio>
            <el-radio value="BATCH">批量（BR-01）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="台数">
          <el-input-number v-model="woForm.qty" :min="1" :controls="false" style="width: 160px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="woDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="woSubmitting" @click="onCreateWo">创建</el-button>
      </template>
    </el-dialog>

    <!-- 到货登记对话框 -->
    <el-dialog v-model="arrivalDialogVisible" title="到货登记" width="420px">
      <el-form label-width="100px">
        <el-form-item label="PO 编号">
          <span>{{ arrivalPo?.poNo }}</span>
        </el-form-item>
        <el-form-item label="物料">
          <span>{{ arrivalPo?.materialCode }} {{ arrivalPo?.materialName }}</span>
        </el-form-item>
        <el-form-item label="本次到货量" required>
          <el-input-number v-model="arrivalQty" :min="0.01" :controls="false" style="width: 160px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="arrivalDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="arrivalSubmitting" @click="onArrival">登记</el-button>
      </template>
    </el-dialog>

    <!-- 样机验收对话框 -->
    <el-dialog v-model="acceptDialogVisible" title="样机验收登记" width="460px">
      <el-form label-width="100px">
        <el-form-item label="工单编号">
          <span>{{ acceptWo?.woNo }}</span>
        </el-form-item>
        <el-form-item label="验收结论" required>
          <el-radio-group v-model="acceptForm.result">
            <el-radio value="PASS">通过</el-radio>
            <el-radio value="FAIL">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="验收人">
          <el-input v-model="acceptForm.acceptor" style="width: 180px" />
        </el-form-item>
        <el-form-item label="验收意见">
          <el-input v-model="acceptForm.comment" type="textarea" :rows="2" style="width: 260px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="acceptDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="acceptSubmitting" @click="onAccept">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  closeWorkOrderApi, completeAcceptanceApi, createPoApi, createWorkOrderApi, listInventoryRecordsApi,
  listKittingApi, listPosApi, listReconciliationsApi, listWorkOrdersApi, processQueueApi,
  reconcileApi, registerArrivalApi, scanPostingApi, startWorkOrderApi,
  type InventoryRecord, type KittingCheck, type PurchaseOrder, type TplusReconciliation, type WorkOrder
} from '@/api/workshop'
import { getTaskDetailApi, submitInstructionApi } from '@/api/agent'

const PO_STATUS_LABEL: Record<string, string> = { DRAFT: '草稿', SUBMITTED: '已下达', CLOSED: '已完结' }
const PO_STATUS_TYPE: Record<string, any> = { DRAFT: 'info', SUBMITTED: 'primary', CLOSED: 'success' }
const WO_STATUS_LABEL: Record<string, string> = { DRAFT: '草稿', STARTED: '已开工', CLOSED: '已完工' }
const WO_STATUS_TYPE: Record<string, any> = { DRAFT: 'info', STARTED: 'primary', CLOSED: 'success' }
const TPLUS_STATUS_LABEL: Record<string, string> = { PENDING: '待推送', PUSHED: '已推送', FAILED: '失败' }
const TPLUS_STATUS_TYPE: Record<string, any> = { PENDING: 'warning', PUSHED: 'success', FAILED: 'danger' }

const activeTab = ref('kitting')

/** AGT 任务提交并等待终态（齐套/请购/对账均为确定性能力，秒级返回） */
async function runAgentTask(instruction: string, code: string) {
  const t = await submitInstructionApi(instruction, code)
  const deadline = Date.now() + 60000
  while (Date.now() < deadline) {
    const res = await getTaskDetailApi(t.data.id)
    const task = res.data?.task
    if (task && ['done', 'failed', 'escalated'].includes(task.status)) return task
    await new Promise((r) => setTimeout(r, 1500))
  }
  throw new Error('任务执行超时')
}

// ===== 齐套看板 =====
const kitProjectId = ref<number>()
const kitLoading = ref(false)
const kittingRecords = ref<KittingCheck[]>([])
const kitResult = ref<{ kitRate: number; totalItems: number; shortages: any[] } | null>(null)
const kittingRunning = ref(false)
const draftRunning = ref(false)

async function loadKitting() {
  if (!kitProjectId.value) return
  kitLoading.value = true
  try {
    const res = await listKittingApi(kitProjectId.value)
    kittingRecords.value = res.data
  } finally {
    kitLoading.value = false
  }
}

async function onRunKitting(code: 'AGT-13' | 'AGT-14') {
  const running = code === 'AGT-13' ? kittingRunning : draftRunning
  running.value = true
  try {
    const task = await runAgentTask(`projectId=${kitProjectId.value} 齐套检查`, code)
    if (task.status !== 'done') throw new Error(task.errorMsg || task.status)
    const result = JSON.parse(task.resultText!)
    if (code === 'AGT-13') {
      kitResult.value = result
      ElMessage.success(`齐套率 ${result.kitRate}%，缺料 ${result.shortages.length} 行`)
      loadKitting()
    } else {
      ElMessage.success(`已生成 ${result.draftCount ?? result.shortageList?.length ?? 0} 张请购草稿 PO（待人工下达）`)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '执行失败')
  } finally {
    running.value = false
  }
}

// ===== PO 管理 =====
const pos = ref<PurchaseOrder[]>([])
const poLoading = ref(false)
const poPageNum = ref(1)
const poPageSize = ref(10)
const poTotal = ref(0)
const poQueryProjectId = ref<number>()
const poDialogVisible = ref(false)
const poSubmitting = ref(false)
const poForm = ref({ projectId: undefined as number | undefined, materialCode: '', materialName: '', qty: 1, supplier: '', expectedDate: '' })
const overdueRunning = ref(false)
const overdueList = ref<{ overdueCount: number; overdueList: any[] } | null>(null)

async function loadPos() {
  poLoading.value = true
  try {
    const res = await listPosApi(poPageNum.value, poPageSize.value, poQueryProjectId.value)
    pos.value = res.data.records
    poTotal.value = res.data.total
  } finally {
    poLoading.value = false
  }
}

async function onCreatePo() {
  if (!poForm.value.projectId || !poForm.value.materialCode) {
    ElMessage.warning('请填写项目 ID 与物料编码')
    return
  }
  poSubmitting.value = true
  try {
    await createPoApi({ ...poForm.value, projectId: poForm.value.projectId! })
    ElMessage.success('PO 已创建')
    poDialogVisible.value = false
    loadPos()
  } finally {
    poSubmitting.value = false
  }
}

async function onOverdueScan() {
  overdueRunning.value = true
  try {
    const task = await runAgentTask(`projectId=${poQueryProjectId.value} 盘点到货交期`, 'AGT-15')
    if (task.status !== 'done') throw new Error(task.errorMsg || task.status)
    const result = JSON.parse(task.resultText!)
    overdueList.value = result
    ElMessage.success(`盘点完成：逾期 ${result.overdueCount} 张`)
  } catch (e: any) {
    ElMessage.error(e?.message || '盘点失败')
  } finally {
    overdueRunning.value = false
  }
}

// ===== 到货登记 =====
const arrivalDialogVisible = ref(false)
const arrivalSubmitting = ref(false)
const arrivalPo = ref<PurchaseOrder | null>(null)
const arrivalQty = ref(1)

function openArrival(row: any) {
  arrivalPo.value = row
  arrivalQty.value = 1
  arrivalDialogVisible.value = true
}

async function onArrival() {
  if (!arrivalPo.value) return
  arrivalSubmitting.value = true
  try {
    await registerArrivalApi(arrivalPo.value.id, arrivalQty.value)
    ElMessage.success('到货已登记（material.arrived 事件已发出）')
    arrivalDialogVisible.value = false
    loadPos()
  } finally {
    arrivalSubmitting.value = false
  }
}

// ===== 工单管理 =====
const workOrders = ref<WorkOrder[]>([])
const woLoading = ref(false)
const woPageNum = ref(1)
const woPageSize = ref(10)
const woTotal = ref(0)
const woQueryProjectId = ref<number>()
const woQueryStatus = ref('')
const woDialogVisible = ref(false)
const woSubmitting = ref(false)
const woForm = ref({ projectId: undefined as number | undefined, orderType: 'PROTOTYPE', qty: 1 })

async function loadWorkOrders() {
  woLoading.value = true
  try {
    const res = await listWorkOrdersApi(woPageNum.value, woPageSize.value, woQueryProjectId.value, woQueryStatus.value || undefined)
    workOrders.value = res.data.records
    woTotal.value = res.data.total
  } finally {
    woLoading.value = false
  }
}

async function onCreateWo() {
  if (!woForm.value.projectId) {
    ElMessage.warning('请填写项目 ID')
    return
  }
  woSubmitting.value = true
  try {
    await createWorkOrderApi({ projectId: woForm.value.projectId!, orderType: woForm.value.orderType, qty: woForm.value.qty })
    ElMessage.success('工单已创建')
    woDialogVisible.value = false
    loadWorkOrders()
  } finally {
    woSubmitting.value = false
  }
}

async function onStartWo(row: any) {
  await startWorkOrderApi(row.id)
  ElMessage.success('工单已开工（BR-08/09 校验通过）')
  loadWorkOrders()
}

async function onCloseWo(row: any) {
  await closeWorkOrderApi(row.id)
  ElMessage.success('工单已完工')
  loadWorkOrders()
}

// ===== 样机验收 =====
const acceptDialogVisible = ref(false)
const acceptSubmitting = ref(false)
const acceptWo = ref<WorkOrder | null>(null)
const acceptForm = ref({ result: 'PASS', acceptor: '', comment: '' })

function openAcceptance(row: any) {
  acceptWo.value = row
  acceptForm.value = { result: 'PASS', acceptor: '', comment: '' }
  acceptDialogVisible.value = true
}

async function onAccept() {
  if (!acceptWo.value) return
  acceptSubmitting.value = true
  try {
    await completeAcceptanceApi({ workorderId: acceptWo.value.id, ...acceptForm.value })
    ElMessage.success('验收已登记')
    acceptDialogVisible.value = false
    loadWorkOrders()
  } finally {
    acceptSubmitting.value = false
  }
}

// ===== 扫码过账 =====
const scanForm = ref({
  direction: 'IN' as 'IN' | 'OUT',
  poId: undefined as number | undefined,
  workorderId: undefined as number | undefined,
  materialCode: '',
  qty: 1,
  barcode: ''
})
const scanPosting = ref(false)

async function onScan() {
  if (!scanForm.value.materialCode) {
    ElMessage.warning('请输入物料编码')
    return
  }
  scanPosting.value = true
  try {
    const res = await scanPostingApi({
      poId: scanForm.value.direction === 'IN' ? scanForm.value.poId : undefined,
      workorderId: scanForm.value.direction === 'OUT' ? scanForm.value.workorderId : undefined,
      materialCode: scanForm.value.materialCode,
      direction: scanForm.value.direction,
      qty: scanForm.value.qty,
      barcode: scanForm.value.barcode || undefined
    })
    ElMessage.success(`过账成功：${res.msg}，已入 T+ 推送队列`)
    scanForm.value.materialCode = ''
    scanForm.value.barcode = ''
    loadInventory()
  } finally {
    scanPosting.value = false
  }
}

// ===== 出入库记录 =====
const inventoryRecords = ref<InventoryRecord[]>([])
const invLoading = ref(false)
const invPageNum = ref(1)
const invPageSize = ref(10)
const invTotal = ref(0)
const invQueryProjectId = ref<number>()
const invQueryDirection = ref('')
const invQueryStatus = ref('')
const queueProcessing = ref(false)

async function loadInventory() {
  invLoading.value = true
  try {
    const res = await listInventoryRecordsApi(invPageNum.value, invPageSize.value,
      invQueryProjectId.value, invQueryDirection.value || undefined, invQueryStatus.value || undefined)
    inventoryRecords.value = res.data.records
    invTotal.value = res.data.total
  } finally {
    invLoading.value = false
  }
}

async function onProcessQueue() {
  queueProcessing.value = true
  try {
    const res = await processQueueApi()
    ElMessage.success(`队列处理完成：推送 ${res.data.processed} 单，T+ 成功率 ${res.data.successRate}%`)
    loadInventory()
  } finally {
    queueProcessing.value = false
  }
}

// ===== T+ 对账 =====
const reconciliations = ref<TplusReconciliation[]>([])
const reconLoading = ref(false)
const reconRunning = ref(false)
const autoReconRunning = ref(false)
const reconResult = ref<TplusReconciliation | null>(null)
const reconDiffs = ref<any[]>([])
const autoRate = ref('')

async function loadReconciliations() {
  reconLoading.value = true
  try {
    const res = await listReconciliationsApi()
    reconciliations.value = res.data
  } finally {
    reconLoading.value = false
  }
}

function applyRecon(batch: TplusReconciliation) {
  reconResult.value = batch
  try {
    reconDiffs.value = batch.diffJson && batch.diffJson !== '[]' ? JSON.parse(batch.diffJson) : []
  } catch {
    reconDiffs.value = []
  }
}

async function onReconcile(auto: boolean) {
  const running = auto ? autoReconRunning : reconRunning
  running.value = true
  try {
    if (auto) {
      const task = await runAgentTask('扫码过账对账', 'AGT-16')
      if (task.status !== 'done') throw new Error(task.errorMsg || task.status)
      const result = JSON.parse(task.resultText!)
      applyRecon(result.reconcile)
      autoRate.value = result.tplusSuccessRate
      ElMessage.success(`AGT-16 完成：推送 ${result.pushedCount} 单，差异 ${result.reconcile.diffCount} 单`)
    } else {
      const res = await reconcileApi()
      applyRecon(res.data)
      autoRate.value = ''
      ElMessage.success(`对账完成：差异 ${res.data.diffCount} 单`)
    }
    loadReconciliations()
  } catch (e: any) {
    ElMessage.error(e?.message || '对账失败')
  } finally {
    running.value = false
  }
}

onMounted(() => {
  loadPos()
  loadWorkOrders()
  loadInventory()
  loadReconciliations()
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
