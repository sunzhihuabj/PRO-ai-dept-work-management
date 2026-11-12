<template>
  <div class="app-container">
    <el-page-header @back="$router.push('/contract')" style="margin-bottom: 16px">
      <template #content><span class="page-header-title">合同建档</span></template>
    </el-page-header>

    <el-row :gutter="16">
      <!-- 左列：文件上传 -->
      <el-col :xs="24" :lg="10">
        <!-- 关联项目 -->
        <el-card shadow="hover" style="margin-bottom: 16px">
          <template #header><span>关联项目（归档目标）</span></template>
          <project-select v-model="filingProjectName" @change="onFilingProjectChange" />
          <div class="field-tip">合同主文件与全部附件将自动绑定至该项目并按分类归档（档案管理可查）</div>
        </el-card>

        <!-- 合同主文件上传区 -->
        <el-card shadow="hover" style="margin-bottom: 16px">
          <template #header>
            <div class="card-header"><span>合同主文件上传</span></div>
          </template>
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            accept=".pdf,.doc,.docx,.txt"
            :on-change="onMainFileChange"
            :on-remove="() => (mainFile = null)"
            :file-list="mainFile ? [{ name: mainFile.name }] : []"
          >
            <el-icon size="36"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽合同主文件到此处，或 <em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 PDF / Word（.doc/.docx）/ TXT；扫描件 PDF 暂不支持（OCR 后续版本接入）</div>
            </template>
          </el-upload>
          <div style="margin-top: 12px; display: flex; gap: 8px">
            <el-button type="primary" :disabled="!mainFile || !filingProjectId || uploading" :loading="uploading" @click="uploadMainFile">
              上传主文件并开始 AI 提取
            </el-button>
          </div>
          <el-alert v-if="extractState" :type="extractState.type" :closable="false" show-icon style="margin-top: 12px"
                    :title="extractState.title" :description="extractState.desc" />
        </el-card>

        <!-- 附件上传区 -->
        <el-card shadow="hover">
          <template #header>
            <div class="card-header"><span>附件上传（批量）</span></div>
          </template>
          <div class="attach-category">
            <span class="attach-label">附件分类：</span>
            <el-select v-model="attachCategory" style="width: 180px">
              <el-option v-for="c in ATTACH_CATEGORIES" :key="c" :label="c" :value="c" />
            </el-select>
          </div>
          <el-upload
            multiple
            :auto-upload="false"
            accept=".pdf,.doc,.docx,.txt,.xls,.xlsx,.jpg,.png"
            :on-change="onAttachChange"
            :on-remove="onAttachRemove"
            :file-list="attachList"
          >
            <el-button type="primary" plain>选择附件文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持补充协议、澄清文件、技术协议、技术要求、技术规范等全部附属材料批量上传</div>
            </template>
          </el-upload>
          <el-button style="margin-top: 12px" :disabled="!attachList.length || !filingProjectId || attaching" :loading="attaching" @click="uploadAttaches">
            批量上传并归档（{{ attachList.length }} 个）
          </el-button>
          <el-table v-if="archivedFiles.length" :data="archivedFiles" size="small" style="margin-top: 12px">
            <el-table-column prop="fileName" label="已归档文件" min-width="150" show-overflow-tooltip />
            <el-table-column prop="fileType" label="分类" width="100" />
            <el-table-column prop="createTime" label="归档时间" width="160" />
          </el-table>
        </el-card>
      </el-col>

      <!-- 右列：合同信息表单（AI 预填 + 人工校验编辑） -->
      <el-col :xs="24" :lg="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>合同信息</span>
              <el-tag v-if="aiFilled" type="success" size="small">AI 已预填，请逐项核对修改</el-tag>
            </div>
          </template>
          <el-alert v-if="riskInfo.summary" :type="riskInfo.level === '高' ? 'error' : riskInfo.level === '中' ? 'warning' : 'success'"
                    :closable="false" show-icon style="margin-bottom: 12px"
                    :title="`AI 风险评估：${riskInfo.level || '未知'}风险 — ${riskInfo.summary}`" />
          <el-alert v-if="missingFields.length" type="warning" :closable="false" show-icon style="margin-bottom: 12px"
                    title="部分字段 AI 未能识别或置信度不足，请人工补全后保存"
                    :description="`未识别字段：${missingFields.join('、')}`" />

          <el-form :model="form" label-width="110px">
            <el-divider content-position="left">基础信息</el-divider>
            <el-row :gutter="12">
              <el-col :span="12"><el-form-item label="合同编号"><el-input v-model="form.contractNo" placeholder="留空由系统按 HT 规则生成" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="合同名称" required><el-input v-model="form.name" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="甲方（客户）" required><el-input v-model="form.customerName" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="乙方主体"><el-input v-model="form.partyB" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="合同金额" required>
                <el-input-number v-model="form.amount" :min="0" :controls="false" style="width: 100%" />
              </el-form-item></el-col>
              <el-col :span="12"><el-form-item label="签订日期"><el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="质保期（月）"><el-input-number v-model="form.warrantyMonths" :min="1" style="width: 100%" /></el-form-item></el-col>
            </el-row>

            <el-divider content-position="left">收款比例（合计须为 100）</el-divider>
            <el-row :gutter="12">
              <el-col :span="6"><el-form-item label="预付款%"><el-input-number v-model="form.payRatioPrepay" :min="0" :max="100" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="到货款%"><el-input-number v-model="form.payRatioDelivery" :min="0" :max="100" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="验收款%"><el-input-number v-model="form.payRatioAccept" :min="0" :max="100" style="width: 100%" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="质保金%">
                <el-input-number v-model="form.payRatioWarranty" :min="0" :max="100" style="width: 100%" />
              </el-form-item></el-col>
            </el-row>
            <el-form-item label-width="110px">
              <el-tag :type="ratioSum === 100 ? 'success' : 'danger'">合计 {{ ratioSum }}%（决策门 G1 要求 100）</el-tag>
            </el-form-item>

            <el-divider content-position="left">商务与履约条款</el-divider>
            <el-row :gutter="12">
              <el-col :span="12"><el-form-item label="付款条件"><el-input v-model="form.paymentTerms" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="付款方式"><el-input v-model="form.paymentMethod" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="交货时间"><el-input v-model="form.deliveryTime" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="交货地点"><el-input v-model="form.deliveryPlace" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="联系人信息"><el-input v-model="form.contactInfo" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="验收条件"><el-input v-model="form.acceptanceCondition" type="textarea" :rows="2" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="合同摘要"><el-input v-model="form.summary" type="textarea" :rows="3" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item></el-col>
            </el-row>
          </el-form>

          <div style="margin-top: 8px; display: flex; gap: 8px">
            <el-button @click="resetAll">重置</el-button>
            <el-popconfirm title="确认按当前信息完成建档入库？建档后可在台账中继续发起审批。" width="280" @confirm="onSave">
              <template #reference>
                <el-button type="primary" :loading="saving"
                           :disabled="ratioSum !== 100 || !form.name || !form.customerName || !form.amount || !filingProjectId">
                  保存并完成建档
                </el-button>
              </template>
            </el-popconfirm>
          </div>
          <div class="field-tip" style="margin-top: 6px">
            建档前置条件：已选关联项目（归档目标）、合同名称/甲方/金额已填写、收款比例合计 100；主文件上传后 AI 自动预填，全部字段支持人工修改
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { createContractApi, uploadContractDocApi, type Contract } from '@/api/contract'
import { uploadFileApi } from '@/api/file'
import { submitInstructionApi, getTaskDetailApi, confirmTaskApi } from '@/api/agent'

const router = useRouter()

const ATTACH_CATEGORIES = ['补充协议', '澄清文件', '技术协议', '技术要求', '技术规范', '其他附件']

// ===== 关联项目 =====
const filingProjectName = ref('')
const filingProjectId = ref<number | null>(null)
function onFilingProjectChange(project: any) {
  filingProjectId.value = project?.id ?? null
}

// ===== 主文件上传与 AI 提取 =====
const mainFile = ref<File | null>(null)
const uploading = ref(false)
const aiFilled = ref(false)
const missingFields = ref<string[]>([])
const extractState = ref<{ type: 'success' | 'warning' | 'error' | 'info'; title: string; desc?: string } | null>(null)
const riskInfo = ref<{ level: string; summary: string }>({ level: '', summary: '' })
const mainArchivedId = ref<number | null>(null)
/** 已归档文件（主文件 + 附件） */
const archivedFiles = ref<{ fileName: string; fileType: string; createTime: string }[]>([])

const ALLOWED_MAIN_EXT = ['pdf', 'doc', 'docx', 'txt']

function onMainFileChange(f: any) {
  const ext = (f.name.split('.').pop() || '').toLowerCase()
  if (!ALLOWED_MAIN_EXT.includes(ext)) {
    ElMessage.error(`不支持的文件格式 .${ext}：仅支持 PDF / Word（.doc/.docx）/ TXT，请转换后重新上传`)
    mainFile.value = null
    return
  }
  mainFile.value = f.raw
  extractState.value = null
}

/** 轮询 Agent 任务至终态；建议级能力进入 pending_confirm 时自动确认（用户上传即明确发起提取意图） */
async function waitTask(id: number, timeoutMs = 120000) {
  const deadline = Date.now() + timeoutMs
  let confirmed = false
  while (Date.now() < deadline) {
    const res = await getTaskDetailApi(id)
    const task = res.data?.task
    if (task && ['done', 'failed', 'escalated'].includes(task.status)) return task
    if (task?.status === 'pending_confirm' && !confirmed) {
      confirmed = true
      await confirmTaskApi(id)
    }
    await new Promise((r) => setTimeout(r, 2000))
  }
  throw new Error('AI 提取任务超时（超过 2 分钟），请稍后在 Agent 工作台查看任务结果后手动重试')
}

/** 字段中文标签（缺字段提示用） */
const FIELD_LABEL: Record<string, string> = {
  contract_no: '合同编号', party_b: '乙方主体', payment_terms: '付款条件', payment_method: '付款方式',
  delivery_time: '交货时间', delivery_place: '交货地点', contact_info: '联系人信息',
  acceptance_condition: '验收条件', summary: '合同摘要'
}

async function uploadMainFile() {
  if (!mainFile.value || !filingProjectId.value) return
  uploading.value = true
  extractState.value = { type: 'info', title: '正在上传主文件…' }
  try {
    // 1. 归档到文件域（绑定关联项目，分类=合同主文件）
    const fd = new FormData()
    fd.append('file', mainFile.value)
    fd.append('projectId', String(filingProjectId.value))
    fd.append('fileType', '合同主文件')
    fd.append('description', `合同建档主文件：${mainFile.value.name}`)
    const archived = await uploadFileApi(fd)
    mainArchivedId.value = archived.data?.id ?? null
    archivedFiles.value = [{ fileName: mainFile.value.name, fileType: '合同主文件', createTime: archived.data?.createTime || '' }].concat(archivedFiles.value)
    // 2. 上传知识库（AI 提取源；文本解析失败不阻塞建档）
    let documentId: number | null = null
    try {
      const kb = await uploadContractDocApi(mainFile.value)
      documentId = kb.data?.id ?? null
    } catch {
      extractState.value = { type: 'warning', title: '主文件已归档，但知识库解析失败', desc: 'AI 提取不可用，请直接人工填写下方表单后保存' }
      return
    }
    // 3. AGT-06 要素提取（AI 预填）
    if (documentId) {
      extractState.value = { type: 'info', title: 'AI 正在读取并解析合同内容…' }
      const t = await submitInstructionApi(`documentId=${documentId} 提取合同要素`, 'AGT-06')
      const done = await waitTask(t.data.id)
      if (done.status !== 'done') {
        throw new Error('AI 要素提取失败：' + (done.errorMsg || done.status) + '。请人工填写表单完成建档。')
      }
      const extracted = JSON.parse(done.resultText).extracted || {}
      prefillForm(extracted)
      // 4. AGT-07 风险分析（可选，失败不阻塞）
      try {
        const t2 = await submitInstructionApi(`documentId=${documentId} 分析合同风险`, 'AGT-07')
        const done2 = await waitTask(t2.data.id)
        if (done2.status === 'done') {
          const analysis = JSON.parse(done2.resultText).analysis || {}
          riskInfo.value = { level: analysis.risk_level || '', summary: analysis.risk_summary || '' }
        }
      } catch { /* 风险分析可选 */ }
    }
  } catch (e: any) {
    extractState.value = { type: 'error', title: 'AI 提取未完成，可人工填写建档', desc: e?.message || '请检查文件内容是否清晰可读，或直接人工填写表单' }
  } finally {
    uploading.value = false
  }
}

function prefillForm(extracted: Record<string, any>) {
  form.value = {
    contractNo: extracted.contract_no || '',
    name: extracted.contract_name || '',
    customerName: extracted.customer_name || '',
    partyB: extracted.party_b || '',
    amount: extracted.amount || undefined as any,
    signDate: extracted.sign_date || undefined,
    warrantyMonths: extracted.warranty_months || 24,
    payRatioPrepay: extracted.pay_ratios?.prepay ?? 0,
    payRatioDelivery: extracted.pay_ratios?.delivery ?? 0,
    payRatioAccept: extracted.pay_ratios?.accept ?? 0,
    payRatioWarranty: extracted.pay_ratios?.warranty ?? 0,
    paymentTerms: extracted.payment_terms || '',
    paymentMethod: extracted.payment_method || '',
    deliveryTime: extracted.delivery_time || '',
    deliveryPlace: extracted.delivery_place || '',
    contactInfo: extracted.contact_info || '',
    acceptanceCondition: extracted.acceptance_condition || '',
    summary: extracted.summary || ''
  }
  missingFields.value = Object.keys(FIELD_LABEL).filter((k) => !extracted[k])
  aiFilled.value = true
  if (missingFields.value.length) {
    extractState.value = {
      type: 'warning',
      title: 'AI 提取完成，部分字段未识别',
      desc: `已按识别结果预填表单，请重点核对并补全：${missingFields.value.map((k) => FIELD_LABEL[k]).join('、')}`
    }
  } else {
    extractState.value = { type: 'success', title: 'AI 提取完成，全部字段已预填', desc: '请逐项核对修改后保存建档' }
  }
}

// ===== 附件批量上传归档 =====
const attachCategory = ref('补充协议')
const attachList = ref<any[]>([])
const attaching = ref(false)

function onAttachChange(f: any, fileList: any[]) {
  attachList.value = fileList
}

function onAttachRemove(_f: any, fileList: any[]) {
  attachList.value = fileList
}

async function uploadAttaches() {
  if (!filingProjectId.value || !attachList.value.length) return
  attaching.value = true
  let ok = 0
  const failed: string[] = []
  try {
    for (const item of attachList.value) {
      try {
        const fd = new FormData()
        fd.append('file', item.raw)
        fd.append('projectId', String(filingProjectId.value))
        fd.append('fileType', attachCategory.value)
        fd.append('description', `合同建档附件（${attachCategory.value}）：${item.name}`)
        const res = await uploadFileApi(fd)
        archivedFiles.value = [{ fileName: item.name, fileType: attachCategory.value, createTime: res.data?.createTime || '' }].concat(archivedFiles.value)
        ok++
      } catch {
        failed.push(item.name)
      }
    }
    if (failed.length) {
      ElMessage.warning(`归档完成 ${ok} 个；${failed.length} 个失败：${failed.join('、')}（请检查文件是否被占用后重试）`)
    } else {
      ElMessage.success(`${ok} 个附件已按「${attachCategory.value}」分类归档并绑定项目`)
    }
    attachList.value = []
  } finally {
    attaching.value = false
  }
}

// ===== 表单 =====
const saving = ref(false)
const emptyForm = (): Contract => ({
  contractNo: '', name: '', customerName: '', partyB: '', amount: undefined as any,
  warrantyMonths: 24, payRatioPrepay: 0, payRatioDelivery: 0, payRatioAccept: 0, payRatioWarranty: 0
})
const form = ref<Contract>(emptyForm())

const ratioSum = computed(() =>
  (form.value.payRatioPrepay || 0) + (form.value.payRatioDelivery || 0)
  + (form.value.payRatioAccept || 0) + (form.value.payRatioWarranty || 0))

function resetAll() {
  form.value = emptyForm()
  mainFile.value = null
  attachList.value = []
  archivedFiles.value = []
  mainArchivedId.value = null
  missingFields.value = []
  aiFilled.value = false
  extractState.value = null
  riskInfo.value = { level: '', summary: '' }
  filingProjectName.value = ''
  filingProjectId.value = null
}

async function onSave() {
  saving.value = true
  try {
    // 显式字段构造，避免提交多余 UI 字段
    const payload: Contract = {
      contractNo: form.value.contractNo || undefined,
      name: form.value.name,
      customerName: form.value.customerName,
      partyB: form.value.partyB || undefined,
      amount: form.value.amount,
      signDate: form.value.signDate || undefined,
      warrantyMonths: form.value.warrantyMonths,
      payRatioPrepay: form.value.payRatioPrepay,
      payRatioDelivery: form.value.payRatioDelivery,
      payRatioAccept: form.value.payRatioAccept,
      payRatioWarranty: form.value.payRatioWarranty,
      paymentTerms: form.value.paymentTerms || undefined,
      paymentMethod: form.value.paymentMethod || undefined,
      deliveryTime: form.value.deliveryTime || undefined,
      deliveryPlace: form.value.deliveryPlace || undefined,
      contactInfo: form.value.contactInfo || undefined,
      acceptanceCondition: form.value.acceptanceCondition || undefined,
      summary: form.value.summary || undefined,
      remark: form.value.remark || undefined,
      filingMainFileId: mainArchivedId.value || undefined
    }
    await createContractApi(payload)
    ElMessage.success('建档成功，合同已入台账（草稿），可在台账中发起审批')
    router.push('/contract')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page-header-title { font-size: 16px; font-weight: 600; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.field-tip { font-size: 12px; color: #909399; margin-top: 8px; }
.attach-category { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.attach-label { font-size: 13px; color: #606266; }
</style>
