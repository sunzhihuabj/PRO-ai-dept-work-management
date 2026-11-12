<template>
  <div class="app-container">
    <!-- 项目详情跳转来的上下文提示条（T-406 跨模块操作不断层） -->
    <el-alert
      v-if="fromProjectId"
      type="info"
      :closable="true"
      style="margin-bottom: 12px"
      @close="fromProjectId = null"
    >
      <template #title>
        来自项目「{{ fromProjectName || fromProjectId }}」的关联合同视图
        <el-button link type="primary" size="small" style="margin-left: 8px" @click="goBackToProject">
          ← 返回项目详情
        </el-button>
      </template>
    </el-alert>

    <el-tabs v-model="activeTab">
      <!-- ============ Tab: 合同台账 ============ -->
      <el-tab-pane label="合同台账" name="list">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input v-model="queryName" placeholder="合同名称" clearable style="width: 200px" @keyup.enter="loadList" />
            <el-select v-model="queryStatus" placeholder="状态" clearable style="width: 140px">
              <el-option v-for="(label, key) in STATUS_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
            <el-button type="primary" @click="loadList">查询</el-button>
            <el-button type="success" @click="router.push('/contract/filing')">建档</el-button>
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
            <el-table-column label="关联项目" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.projectCount" size="small" effect="plain" type="primary" style="cursor: pointer" @click="openProjects(row)">
                  {{ row.projectCount }} 个
                </el-tag>
                <span v-else style="color: #c0c4cc">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
                <el-button link type="primary" size="small" @click="openProjects(row)">关联项目</el-button>
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
    <!-- 关联项目抽屉（T-406） -->
    <el-drawer v-model="projDrawerVisible" :title="`关联项目 — ${projDrawerContract?.contractNo || ''} ${projDrawerContract?.name || ''}`" size="640px">
      <el-alert
        v-if="projDrawerContract && projDrawerContract.status !== 'EFFECTIVE'"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        title="仅已生效合同可建立项目关联；当前合同未生效，可先查看已有关联"
      />
      <div class="bind-form">
        <div class="bind-form-label">绑定新项目：</div>
        <div class="bind-form-row">
          <project-select v-model="bindProjectName" style="flex: 1" @change="onBindProjectChange" />
          <el-button type="primary" :disabled="!bindProjectId || projDrawerContract?.status !== 'EFFECTIVE'" @click="onBind">
            绑定
          </el-button>
        </div>
        <div class="bind-form-tip">输入项目名称模糊搜索；仅已生效合同允许绑定，重复绑定会被拒绝</div>
      </div>

      <el-table v-loading="projLoading" :data="projList" border stripe>
        <el-table-column prop="projectName" label="项目名称" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.projectStatus == null" style="color: #c0c4cc">{{ row.projectName }}</span>
            <el-link v-else type="primary" :underline="false" @click="goProject(row.projectId)">{{ row.projectName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">{{ PROJECT_TYPE_LABEL[row.projectType] || '-' }}</template>
        </el-table-column>
        <el-table-column label="项目状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.projectStatus != null" :type="PROJECT_STATUS_TYPE[row.projectStatus] || 'info'" size="small">
              {{ PROJECT_STATUS_LABEL[row.projectStatus] ?? row.projectStatus }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectLeader" label="负责人" width="90" show-overflow-tooltip />
        <el-table-column prop="bindTime" label="绑定时间" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.projectStatus != null" link type="primary" size="small" @click="goProject(row.projectId)">打开项目</el-button>
            <el-button link type="danger" size="small" @click="onUnbind(row as any)">解除绑定</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!projLoading && projList.length === 0" description="暂无关联项目" :image-size="72" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveContractApi, bindContractProjectApi, closeContractApi, countersignApi,
  getContractDetailApi, listContractProjectsApi, listContractsApi, sealContractApi,
  submitCountersignApi, unbindContractProjectApi,
  type Contract, type ContractProjectItem
} from '@/api/contract'
import { getProjectDetailApi } from '@/api/project'

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
const PROJECT_TYPE_LABEL: Record<number, string> = { 1: '整机厂', 2: '技改', 3: '研发' }
const PROJECT_STATUS_LABEL: Record<number, string> = { 0: '待启动', 1: '进行中', 2: '已完成', 3: '已关闭', 4: '暂停' }
const PROJECT_STATUS_TYPE: Record<number, any> = { 0: 'info', 1: 'primary', 2: 'success', 3: 'info', 4: 'warning' }

const route = useRoute()
const router = useRouter()

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

// ===== 合同-项目关联（T-406）=====
const projDrawerVisible = ref(false)
const projDrawerContract = ref<Contract | null>(null)
const projList = ref<ContractProjectItem[]>([])
const projLoading = ref(false)
const bindProjectName = ref('')
const bindProjectId = ref<number | null>(null)

/** 项目详情跳转过来的上下文（?fromProject=&contractId=） */
const fromProjectId = ref<number | null>(null)
const fromProjectName = ref('')

function openProjects(row: any) {
  projDrawerContract.value = row
  bindProjectName.value = ''
  bindProjectId.value = null
  projDrawerVisible.value = true
  loadProjList()
}

async function loadProjList() {
  if (!projDrawerContract.value?.id) return
  projLoading.value = true
  try {
    const res = await listContractProjectsApi(projDrawerContract.value.id)
    projList.value = res.data || []
  } finally {
    projLoading.value = false
  }
}

function onBindProjectChange(project: any) {
  bindProjectId.value = project?.id ?? null
}

async function onBind() {
  if (!projDrawerContract.value?.id || !bindProjectId.value) return
  await bindContractProjectApi(projDrawerContract.value.id, bindProjectId.value)
  ElMessage.success('项目已关联至本合同')
  bindProjectName.value = ''
  bindProjectId.value = null
  loadProjList()
  loadList()
}

async function onUnbind(row: ContractProjectItem) {
  const effective = projDrawerContract.value?.status === 'EFFECTIVE'
  const tip = effective
    ? `确认解除合同「${projDrawerContract.value?.contractNo}」与项目「${row.projectName}」的关联？\n该合同履行中，解除后项目侧将不再展示本合同，请确认无结算/对账依赖。`
    : `确认解除合同「${projDrawerContract.value?.contractNo}」与项目「${row.projectName}」的关联？`
  await ElMessageBox.confirm(tip, '解除关联确认', {
    confirmButtonText: '确认解除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await unbindContractProjectApi(projDrawerContract.value!.id!, row.projectId)
  ElMessage.success('已解除关联')
  loadProjList()
  loadList()
}

/** 合同 → 项目：携带上下文跳转项目详情 */
function goProject(projectId: number) {
  projDrawerVisible.value = false
  detailVisible.value = false
  router.push({
    path: `/project/detail/${projectId}`,
    query: { from: 'contract', contractId: String(projDrawerContract.value?.id || '') }
  })
}

/** 项目详情 → 合同 的返回路径 */
function goBackToProject() {
  router.push(`/project/detail/${fromProjectId.value}`)
}

onMounted(async () => {
  loadList()
  // 跨模块上下文还原：项目详情「查看合同」跳转 ?contractId=xx&fromProject=yy
  const qContractId = Number(route.query.contractId)
  if (qContractId > 0) {
    try {
      const d = await getContractDetailApi(qContractId)
      const row = d.data?.contract
      if (row) {
        openProjects(row)
      }
    } catch {
      ElMessage.warning('指定的合同不存在或已被删除')
    }
    if (route.query.fromProject) {
      fromProjectId.value = Number(route.query.fromProject)
      try {
        const pres = await getProjectDetailApi(fromProjectId.value)
        fromProjectName.value = pres.data?.projectName || ''
      } catch {
        fromProjectName.value = ''
      }
    }
  }
})
</script>

<style scoped>
.comment { color: #909399; font-size: 12px; margin-top: 2px; }
.bind-form { margin-bottom: 14px; padding: 12px; background: #f5f7fa; border-radius: 6px; }
.bind-form-label { font-size: 13px; color: #606266; margin-bottom: 8px; }
.bind-form-row { display: flex; gap: 8px; align-items: center; }
.bind-form-tip { font-size: 12px; color: #909399; margin-top: 6px; }
</style>
