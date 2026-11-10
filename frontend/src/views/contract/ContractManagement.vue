<template>
  <div class="app-container">
    <el-tabs v-model="activeTab">
      <!-- ============ Tab 1: 合同台账 ============ -->
      <el-tab-pane label="合同台账" name="list">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="queryName" placeholder="合同名称" clearable style="width: 200px" @keyup.enter="loadList" />
            <el-select v-model="queryStatus" placeholder="状态" clearable style="width: 140px">
              <el-option v-for="(label, key) in STATUS_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
            <el-button type="primary" @click="loadList">查询</el-button>
            <el-button type="success" @click="activeTab = 'create'">AI 建档</el-button>
          </div>

          <el-table v-loading="loading" :data="records" border stripe>
            <el-table-column prop="contractNo" label="合同编号" width="150" />
            <el-table-column prop="name" label="合同名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
            <el-table-column label="金额（元）" width="130" align="right">
              <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="风险" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.riskLevel" :type="RISK_TYPE[row.riskLevel] || 'info'" size="small">{{ row.riskLevel }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="STATUS_TYPE[row.status] || 'info'" size="small">{{ STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
                <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="onSubmitCountersign(row)">提交会签</el-button>
                <el-button v-if="row.status === 'COUNTERSIGN'" link type="success" size="small" @click="onApproveNode(row, 'countersign')">会签</el-button>
                <el-button v-if="row.status === 'APPROVING'" link type="success" size="small" @click="onApproveNode(row, 'approve')">审批</el-button>
                <el-button v-if="row.status === 'SEALING'" link type="warning" size="small" @click="onSeal(row)">用印登记</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            style="margin-top: 12px; justify-content: flex-end"
            @current-change="loadList"
          />
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 2: AI 建档（上传→提取→确认） ============ -->
      <el-tab-pane label="AI 智能建档" name="create">
        <el-card shadow="never">
          <el-steps :active="step" align-center finish-status="success" style="margin-bottom: 20px">
            <el-step title="上传合同文档" description="PDF/Word/文本" />
            <el-step title="AI 要素提取" description="AGT-06 自动提取" />
            <el-step title="确认建档" description="人工核对后入库" />
          </el-steps>

          <!-- 步骤1：上传 -->
          <div v-if="step === 0" class="upload-area">
            <el-upload drag :auto-upload="false" :limit="1" accept=".txt,.pdf,.docx" :on-change="onFileChange" :file-list="fileList">
              <el-icon size="40"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽合同文件到此处，或 <em>点击选择</em></div>
              <template #tip>
                <div class="el-upload__tip">扫描件 PDF 暂不支持（OCR 后续版本接入）</div>
              </template>
            </el-upload>
            <el-button type="primary" :disabled="!file" :loading="extracting" style="margin-top: 16px" @click="startExtract">
              上传并开始 AI 提取
            </el-button>
          </div>

          <!-- 步骤2/3：提取结果确认 -->
          <div v-else>
            <el-alert v-if="riskInfo.summary" :type="RISK_TYPE[riskInfo.level] === 'danger' ? 'error' : RISK_TYPE[riskInfo.level] === 'warning' ? 'warning' : 'success'"
                      :title="`AI 风险评估：${riskInfo.level}风险 — ${riskInfo.summary}`" show-icon :closable="false" style="margin-bottom: 16px" />
            <el-form :model="form" label-width="120px" style="max-width: 640px">
              <el-form-item label="合同名称" required><el-input v-model="form.name" /></el-form-item>
              <el-form-item label="客户名称" required><el-input v-model="form.customerName" /></el-form-item>
              <el-form-item label="合同金额" required><el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 200px" /></el-form-item>
              <el-form-item label="签订日期"><el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
              <el-form-item label="质保期（月）"><el-input-number v-model="form.warrantyMonths" :min="1" /></el-form-item>
              <el-form-item label="预付款比例%"><el-input-number v-model="form.payRatioPrepay" :min="0" :max="100" /></el-form-item>
              <el-form-item label="到货款比例%"><el-input-number v-model="form.payRatioDelivery" :min="0" :max="100" /></el-form-item>
              <el-form-item label="验收款比例%"><el-input-number v-model="form.payRatioAccept" :min="0" :max="100" /></el-form-item>
              <el-form-item label="质保金比例%">
                <el-input-number v-model="form.payRatioWarranty" :min="0" :max="100" />
                <el-tag :type="ratioSum === 100 ? 'success' : 'danger'" style="margin-left: 12px">合计 {{ ratioSum }}%</el-tag>
              </el-form-item>
              <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
            </el-form>
            <div style="margin-top: 12px">
              <el-button @click="resetCreate">重新上传</el-button>
              <el-button type="primary" :loading="creating" :disabled="ratioSum !== 100 || !form.name || !form.customerName || !form.amount" @click="onCreate">
                确认建档（G1 门校验）
              </el-button>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="合同详情" size="560px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="合同编号">{{ detail.contract.contractNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="STATUS_TYPE[detail.contract.status || '']" size="small">{{ STATUS_LABEL[detail.contract.status || ''] || detail.contract.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.contract.customerName }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ formatAmount(detail.contract.amount) }} 元</el-descriptions-item>
          <el-descriptions-item label="收款比例">
            预付 {{ detail.contract.payRatioPrepay }}% / 到货 {{ detail.contract.payRatioDelivery }}% /
            验收 {{ detail.contract.payRatioAccept }}% / 质保金 {{ detail.contract.payRatioWarranty }}%
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.contract.riskLevel" label="AI 风险">
            <el-tag :type="RISK_TYPE[detail.contract.riskLevel]" size="small">{{ detail.contract.riskLevel }}</el-tag>
            {{ detail.contract.riskSummary }}
          </el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.contract.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4>审批记录</h4>
        <el-timeline style="padding-left: 4px">
          <el-timeline-item v-for="a in detail.approvals" :key="a.id" :timestamp="a.createTime"
                            :type="(a.result === 'APPROVED' ? 'success' : 'danger') as any">
            {{ NODE_LABEL[a.node] || a.node }} — {{ a.approverName }}（{{ a.result === 'APPROVED' ? '通过' : '驳回' }}）
            <div v-if="a.comment" class="comment">{{ a.comment }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  approveContractApi, closeContractApi, countersignApi, createContractApi, getContractDetailApi,
  listContractsApi, sealContractApi, submitCountersignApi, uploadContractDocApi, type Contract
} from '@/api/contract'
import { submitInstructionApi, getTaskDetailApi } from '@/api/agent'

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿', COUNTERSIGN: '会签中', APPROVING: '审批中', SEALING: '用印中',
  EFFECTIVE: '已生效', CLOSED: '已关闭', TERMINATED: '已终止'
}
const STATUS_TYPE: Record<string, any> = {
  DRAFT: 'info', COUNTERSIGN: 'warning', APPROVING: 'warning',
  SEALING: 'warning', EFFECTIVE: 'success', CLOSED: 'info', TERMINATED: 'danger'
}
const RISK_TYPE: Record<string, any> = { 高: 'danger', 中: 'warning', 低: 'success' }
const NODE_LABEL: Record<string, string> = { COUNTERSIGN: '会签', APPROVE: '审批', SEAL: '用印' }

// ===== 台账 =====
const activeTab = ref('list')
const records = ref<Contract[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const queryName = ref('')
const queryStatus = ref('')

async function loadList() {
  loading.value = true
  try {
    const res = await listContractsApi(pageNum.value, pageSize.value, queryName.value || undefined, queryStatus.value || undefined)
    records.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function formatAmount(v?: number) {
  return v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

// ===== 审批流转 =====
async function onSubmitCountersign(row: any) {
  await submitCountersignApi(row.id!)
  ElMessage.success('已提交会签（G1 门校验通过）')
  loadList()
}

async function onApproveNode(row: any, node: 'countersign' | 'approve') {
  const { value } = await ElMessageBox.prompt(`请输入${node === 'countersign' ? '会签' : '审批'}意见`, node === 'countersign' ? '会签' : '审批', {
    inputPlaceholder: '同意/驳回及意见',
    inputValidator: (v: string) => !!v || '请输入意见'
  })
  const approved = await ElMessageBox.confirm('是否通过？', '确认', { cancelButtonText: '驳回' })
    .then(() => true)
    .catch(() => false)
  await (node === 'countersign' ? countersignApi : approveContractApi)(row.id!, {
    result: approved ? 'APPROVED' : 'REJECTED', comment: value
  })
  ElMessage.success(approved ? '已通过' : '已驳回')
  loadList()
}

async function onSeal(row: any) {
  const { value } = await ElMessageBox.prompt('用印登记（G2 门：登记完成合同即生效）', '用印登记', {
    inputPlaceholder: '用印说明',
    inputValidator: (v: string) => !!v || '请输入用印说明'
  })
  await sealContractApi(row.id!, value)
  ElMessage.success('合同已生效，预付款提醒已自动发送')
  loadList()
}

// ===== 详情 =====
const detailVisible = ref(false)
const detail = ref<{ contract: Contract; approvals: any[] } | null>(null)
async function openDetail(id: number) {
  const res = await getContractDetailApi(id)
  detail.value = res.data
  detailVisible.value = true
}

// ===== AI 建档 =====
const step = ref(0)
const file = ref<File | null>(null)
const fileList = ref<any[]>([])
const extracting = ref(false)
const creating = ref(false)
const riskInfo = ref<{ level: string; summary: string }>({ level: '', summary: '' })

const form = ref<Contract>({
  name: '', customerName: '', amount: 0, warrantyMonths: 24,
  payRatioPrepay: 30, payRatioDelivery: 30, payRatioAccept: 30, payRatioWarranty: 10
})
const ratioSum = computed(() =>
  (form.value.payRatioPrepay || 0) + (form.value.payRatioDelivery || 0)
  + (form.value.payRatioAccept || 0) + (form.value.payRatioWarranty || 0))

function onFileChange(f: any) {
  file.value = f.raw
}

/** 轮询 Agent 任务至终态 */
async function waitTask(id: number, timeoutMs = 120000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const res = await getTaskDetailApi(id)
    const task = res.data?.task
    if (task && ['done', 'failed', 'escalated'].includes(task.status)) return task
    await new Promise((r) => setTimeout(r, 2000))
  }
  throw new Error('任务执行超时')
}

async function startExtract() {
  if (!file.value) return
  extracting.value = true
  try {
    // 1. 上传到知识库
    const up = await uploadContractDocApi(file.value)
    const documentId = up.data?.id
    if (!documentId) throw new Error('文档上传失败')
    // 2. AGT-06 要素提取
    const t1 = await submitInstructionApi(`documentId=${documentId} 提取合同要素`, 'AGT-06')
    const done1 = await waitTask(t1.data.id)
    if (done1.status !== 'done') throw new Error('要素提取失败：' + (done1.errorMsg || done1.status))
    const extracted = JSON.parse(done1.resultText).extracted || {}
    // 3. AGT-07 风险分析（失败不阻塞建档，风险信息置空）
    try {
      const t2 = await submitInstructionApi(`documentId=${documentId} 分析合同风险`, 'AGT-07')
      const done2 = await waitTask(t2.data.id)
      if (done2.status === 'done') {
        const analysis = JSON.parse(done2.resultText).analysis || {}
        riskInfo.value = { level: analysis.risk_level || '', summary: analysis.risk_summary || '' }
      }
    } catch { /* 风险分析可选 */ }
    // 4. 预填表单（AI 建议，人工可改）
    form.value = {
      name: extracted.contract_name || '',
      customerName: extracted.customer_name || '',
      amount: extracted.amount || 0,
      signDate: extracted.sign_date || undefined,
      warrantyMonths: extracted.warranty_months || 24,
      payRatioPrepay: extracted.pay_ratios?.prepay ?? 30,
      payRatioDelivery: extracted.pay_ratios?.delivery ?? 30,
      payRatioAccept: extracted.pay_ratios?.accept ?? 30,
      payRatioWarranty: extracted.pay_ratios?.warranty ?? 10
    }
    step.value = 2
    ElMessage.success('AI 提取完成，请人工核对后建档')
  } catch (e: any) {
    ElMessage.error(e?.message || '提取失败，请重试或转人工录入')
  } finally {
    extracting.value = false
  }
}

async function onCreate() {
  creating.value = true
  try {
    await createContractApi(form.value)
    ElMessage.success('建档成功（草稿）')
    resetCreate()
    activeTab.value = 'list'
    loadList()
  } finally {
    creating.value = false
  }
}

function resetCreate() {
  step.value = 0
  file.value = null
  fileList.value = []
  riskInfo.value = { level: '', summary: '' }
  form.value = {
    name: '', customerName: '', amount: 0, warrantyMonths: 24,
    payRatioPrepay: 30, payRatioDelivery: 30, payRatioAccept: 30, payRatioWarranty: 10
  }
}

onMounted(loadList)
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.upload-area { display: flex; flex-direction: column; align-items: center; padding: 32px 0; }
.comment { color: #909399; font-size: 12px; margin-top: 2px; }
</style>
