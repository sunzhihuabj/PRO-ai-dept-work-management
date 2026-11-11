<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>变更管理（ECN · BR-07 旧版资料回收）</span>
          <el-button type="primary" @click="openCreate">新建变更单</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="变更单" name="ecns">
          <el-form inline>
            <el-form-item label="项目ID">
              <el-input-number v-model="queryProjectId" :min="1" controls-position="right" style="width: 140px" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="queryStatus" clearable placeholder="全部" style="width: 140px">
                <el-option v-for="(label, key) in ECN_STATUS_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadEcns">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="ecns" v-loading="loading" border>
            <el-table-column prop="ecnNo" label="ECN 编号" width="150" />
            <el-table-column prop="title" label="变更标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">{{ ECN_TYPE_LABEL[row.changeType] || row.changeType }}</template>
            </el-table-column>
            <el-table-column label="影响级别" width="90">
              <template #default="{ row }">
                <el-tag :type="row.impactLevel === 'A' ? 'danger' : row.impactLevel === 'B' ? 'warning' : 'info'" size="small">{{ row.impactLevel }} 级</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="ECN_STATUS_TYPE[row.status]" size="small">{{ ECN_STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approver" label="审批人" width="100" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'DRAFT'" size="small" type="primary" @click="submitEcn(row)">提交审批</el-button>
                <el-button v-if="row.status === 'PENDING_APPROVAL'" size="small" type="success" @click="openApprove(row)">审批</el-button>
                <el-button v-if="row.status === 'APPROVED'" size="small" type="warning" @click="openImplement(row)">实施</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total"
                         layout="total, prev, pager, next" style="margin-top: 12px" @current-change="loadEcns" />
        </el-tab-pane>

        <el-tab-pane label="旧版资料回收" name="recalls">
          <el-button type="primary" plain @click="loadRecalls" style="margin-bottom: 12px">刷新</el-button>
          <el-table :data="recalls" v-loading="recallsLoading" border>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="ecnId" label="ECN" width="80" />
            <el-table-column label="资料类型" width="110">
              <template #default="{ row }">{{ ECN_ITEM_TYPE_LABEL[row.itemType] || row.itemType }}</template>
            </el-table-column>
            <el-table-column prop="itemDesc" label="资料描述" min-width="200" show-overflow-tooltip />
            <el-table-column prop="owner" label="责任人" width="110" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'CONFIRMED' ? 'success' : row.status === 'RECALLED' ? 'primary' : 'warning'" size="small">
                  {{ ECN_RECALL_LABEL[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="dueDate" label="回收期限" width="110" />
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'RECALLED'" size="small" type="success" @click="confirmRecall(row)">确认完成</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新建 ECN -->
    <el-dialog v-model="createVisible" title="新建 ECN 变更单" width="560px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="项目ID" required>
          <el-input-number v-model="createForm.projectId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="变更标题" required>
          <el-input v-model="createForm.title" maxlength="200" placeholder="如：塔筒法兰规格变更" />
        </el-form-item>
        <el-form-item label="变更原因">
          <el-input v-model="createForm.reason" type="textarea" :rows="3" maxlength="1000" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.changeType" style="width: 200px">
            <el-option label="设计" value="DESIGN" />
            <el-option label="工艺" value="PROCESS" />
            <el-option label="物料" value="MATERIAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="影响级别">
          <el-radio-group v-model="createForm.impactLevel">
            <el-radio value="A">A 重大</el-radio>
            <el-radio value="B">B 一般</el-radio>
            <el-radio value="C">C 轻微</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- ECN 审批 -->
    <el-dialog v-model="approveVisible" title="ECN 审批" width="480px">
      <el-form label-width="90px">
        <el-form-item label="审批人">
          <el-input v-model="approveForm.approver" placeholder="署名" />
        </el-form-item>
        <el-form-item label="结论">
          <el-radio-group v-model="approveForm.approved">
            <el-radio :value="true">批准</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="approveForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doApprove">提交</el-button>
      </template>
    </el-dialog>

    <!-- ECN 实施 -->
    <el-dialog v-model="implementVisible" title="ECN 实施（BR-07：自动生成旧版回收任务+批量齐套复检）" width="520px">
      <el-alert type="warning" :closable="false" style="margin-bottom: 12px"
                title="实施后将联动：旧方案受控归档 → 旧版资料回收任务 → AGT-13 批量齐套复算 → 缺料自动请购草稿" />
      <el-form label-width="110px">
        <el-form-item label="新方案ID">
          <el-input-number v-model="implementForm.newSchemeId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="回收责任人">
          <el-input v-model="implementForm.recallOwner" placeholder="如：张工" />
        </el-form-item>
        <el-form-item label="回收期限(天)">
          <el-input-number v-model="implementForm.recallDueDays" :min="1" :max="60" controls-position="right" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="implementVisible = false">取消</el-button>
        <el-button type="warning" :loading="saving" @click="doImplement">实施</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ECN_STATUS_LABEL, ECN_STATUS_TYPE, ECN_RECALL_LABEL,
  listEcnsApi, createEcnApi, submitEcnApi, approveEcnApi, implementEcnApi,
  listRecallTasksApi, confirmRecallApi
} from '@/api/phase3'
import type { EcnChange, EcnRecallTask } from '@/api/phase3'

const ECN_TYPE_LABEL: Record<string, string> = { DESIGN: '设计', PROCESS: '工艺', MATERIAL: '物料' }
const ECN_ITEM_TYPE_LABEL: Record<string, string> = { SCHEME: '方案', BOM: 'BOM明细', DRAWING: '图纸', DOC: '文档' }

const activeTab = ref('ecns')
const loading = ref(false)
const recallsLoading = ref(false)
const saving = ref(false)
const ecns = ref<EcnChange[]>([])
const recalls = ref<EcnRecallTask[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const queryProjectId = ref<number | undefined>()
const queryStatus = ref<string>('')

const createVisible = ref(false)
const approveVisible = ref(false)
const implementVisible = ref(false)
let currentEcn: EcnChange | null = null

const createForm = reactive({ projectId: 1, title: '', reason: '', changeType: 'DESIGN', impactLevel: 'B' })
const approveForm = reactive({ approver: '', approved: true, remark: '' })
const implementForm = reactive({ newSchemeId: undefined as number | undefined, recallOwner: '', recallDueDays: 7 })

async function loadEcns() {
  loading.value = true
  try {
    const res = await listEcnsApi(pageNum.value, pageSize.value, queryProjectId.value, queryStatus.value || undefined)
    ecns.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadRecalls() {
  recallsLoading.value = true
  try {
    const res = await listRecallTasksApi()
    recalls.value = res.data
  } finally {
    recallsLoading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, { projectId: 1, title: '', reason: '', changeType: 'DESIGN', impactLevel: 'B' })
  createVisible.value = true
}

async function doCreate() {
  if (!createForm.title) {
    ElMessage.warning('请填写变更标题')
    return
  }
  saving.value = true
  try {
    await createEcnApi({ ...createForm })
    ElMessage.success('ECN 已创建')
    createVisible.value = false
    await loadEcns()
  } finally {
    saving.value = false
  }
}

async function submitEcn(row: any) {
  await submitEcnApi(row.id)
  ElMessage.success('已提交审批')
  await loadEcns()
}

function openApprove(row: any) {
  currentEcn = row
  Object.assign(approveForm, { approver: '', approved: true, remark: '' })
  approveVisible.value = true
}

async function doApprove() {
  if (!currentEcn) return
  saving.value = true
  try {
    await approveEcnApi(currentEcn.id, approveForm.approved, approveForm.approver, approveForm.remark)
    ElMessage.success('审批完成')
    approveVisible.value = false
    await loadEcns()
  } finally {
    saving.value = false
  }
}

function openImplement(row: any) {
  currentEcn = row
  Object.assign(implementForm, { newSchemeId: undefined, recallOwner: '', recallDueDays: 7 })
  implementVisible.value = true
}

async function doImplement() {
  if (!currentEcn) return
  saving.value = true
  try {
    const res = await implementEcnApi(currentEcn.id, implementForm)
    const data = res.data || {}
    ElMessage.success(`实施完成：回收任务 ${data.recallTaskCount ?? '-'} 项，齐套 ${data.kittingResult ?? '-'}，缺料草稿 ${data.shortageDraft ?? '-'}`)
    implementVisible.value = false
    await loadEcns()
    await loadRecalls()
  } finally {
    saving.value = false
  }
}

async function confirmRecall(row: any) {
  await confirmRecallApi(row.id)
  ElMessage.success('回收已确认')
  await loadRecalls()
}

onMounted(() => {
  loadEcns()
  loadRecalls()
})
</script>

<style scoped>
.page-container { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
