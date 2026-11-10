<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">项目详情</span></template>
    </el-page-header>

    <el-card shadow="hover" v-loading="loading" style="margin-bottom: 16px;">
      <el-descriptions :column="3" border :title="project?.projectName">
        <el-descriptions-item label="项目编号">{{ project?.projectCode }}</el-descriptions-item>
        <el-descriptions-item label="项目类型">{{ typeText(project?.projectType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTag(project?.status)" size="small">{{ statusText(project?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ project?.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ project?.projectLeader || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同金额">{{ project?.contractAmount ? '¥' + project.contractAmount : '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划开始">{{ project?.planStartDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划结束">{{ project?.planEndDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ project?.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目描述" :span="3">{{ project?.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="hover" style="margin-bottom: 16px;">
      <template #header><span>项目阶段</span></template>
      <el-steps :active="currentPhaseIndex" finish-status="success" align-center>
        <el-step
          v-for="phase in phases"
          :key="phase.id"
          :title="phase.phaseName"
          :description="phase.planStartDate + ' ~ ' + phase.planEndDate"
          :status="phaseStepStatus(phase.status)"
        />
      </el-steps>

      <el-table :data="phases" style="margin-top: 16px;">
        <el-table-column prop="phaseOrder" label="序号" width="70" />
        <el-table-column prop="phaseName" label="阶段名称" />
        <el-table-column prop="planStartDate" label="计划开始" width="120" />
        <el-table-column prop="planEndDate" label="计划结束" width="120" />
        <el-table-column prop="actualStartDate" label="实际开始" width="120" />
        <el-table-column prop="actualEndDate" label="实际结束" width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }: any">
            <el-tag :type="phaseTag(row.status)" size="small">{{ phaseStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }: any">
            <el-button v-if="row.status === 0" link type="primary" @click="updatePhase(row.id, 1)">开始</el-button>
            <el-button v-if="row.status === 1" link type="success" @click="updatePhase(row.id, 2)">完成</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="hover" style="margin-bottom: 16px;">
      <template #header>
        <div class="card-header">
          <span>项目资料明细（{{ projectFiles.length }}）</span>
          <el-button type="primary" size="small" @click="openMaterialDialog">上传资料</el-button>
        </div>
      </template>
      <el-table :data="projectFiles" size="small">
        <el-table-column label="文件名" min-width="180" show-overflow-tooltip>
          <template #default="{ row }: any">{{ row.fileName }}</template>
        </el-table-column>
        <el-table-column label="类别" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.phaseId ? 'primary' : 'warning'" size="small">
              {{ row.phaseId ? '阶段文件' : '项目资料' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="所属阶段" width="120">
          <template #default="{ row }: any">{{ row.phaseId ? phaseNameOf(row.phaseId) : '—' }}</template>
        </el-table-column>
        <el-table-column prop="fileType" label="格式" width="70" align="center" />
        <el-table-column label="大小" width="90" align="right">
          <template #default="{ row }: any">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="70" align="center" />
        <el-table-column prop="createBy" label="上传人" width="90" />
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" size="small" @click="downloadProjFile(row)">下载</el-button>
            <el-button link type="primary" size="small" @click="$router.push(`/file/detail/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!projectFiles.length" description="暂无项目资料，点击「上传资料」添加" :image-size="60" />
    </el-card>

    <el-row :gutter="16">
      <!-- 项目成员 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>项目成员（{{ members.length }}）</span>
              <el-button type="primary" size="small" @click="memberDialog = true">添加成员</el-button>
            </div>
          </template>
          <el-table :data="members" size="small">
            <el-table-column label="成员" width="110">
              <template #default="{ row }: any">{{ userNameOf(row.userId) }}</template>
            </el-table-column>
            <el-table-column prop="roleType" label="项目角色" />
            <el-table-column prop="joinTime" label="加入时间" width="170" />
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }: any">
                <el-button link type="danger" size="small" @click="removeMember(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!members.length" description="暂无成员，点击添加" :image-size="60" />
        </el-card>
      </el-col>

      <!-- 里程碑 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>项目里程碑</span>
              <el-button type="primary" size="small" @click="openMsDialog()">新建里程碑</el-button>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="m in milestones"
              :key="m.id"
              :timestamp="m.planDate || ''"
              :type="msType(m.status)"
              placement="top"
            >
              <div class="ms-item">
                <span>{{ m.milestoneName }}</span>
                <el-tag :type="msTag(m.status)" size="small">{{ msText(m.status) }}</el-tag>
              </div>
              <div class="ms-actions">
                <el-button v-if="m.status === 0" link type="success" size="small" @click="mark(m, 1)">标记完成</el-button>
                <el-button v-if="m.status === 0" link type="warning" size="small" @click="mark(m, 2)">标记延期</el-button>
                <el-button link type="danger" size="small" @click="removeMs(m)">删除</el-button>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!milestones.length" description="暂无里程碑" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 交付物清单 -->
    <el-card shadow="hover" style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span>
            交付物清单
            <el-tag v-if="stats" size="small" style="margin-left: 8px;" :type="stats.requiredMissing === 0 ? 'success' : 'warning'">
              必需缺 {{ stats.requiredMissing }}
            </el-tag>
            <el-tag v-if="stats" size="small" type="info" style="margin-left: 4px;">
              已传 {{ stats.uploaded }}/{{ stats.total }} · 已检 {{ stats.checked }}
            </el-tag>
          </span>
          <el-button type="primary" size="small" :loading="generating" @click="generate">从模板生成</el-button>
        </div>
      </template>
      <el-table :data="deliverables" size="small">
        <el-table-column label="阶段" width="140">
          <template #default="{ row }: any">{{ phaseNameOf(row.phaseId) }}</template>
        </el-table-column>
        <el-table-column prop="deliverableName" label="交付物" min-width="160" />
        <el-table-column label="必需" width="70" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.required === 1 ? 'danger' : 'info'" size="small">{{ row.required === 1 ? '必需' : '可选' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传" width="80" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.uploaded === 1 ? 'success' : 'info'" size="small">{{ row.uploaded === 1 ? '已上传' : '未上传' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="关联文件" width="110">
          <template #default="{ row }: any">
            <el-link v-if="row.fileId" type="primary" @click="$router.push(`/file/detail/${row.fileId}`)">文件#{{ row.fileId }}</el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="检查" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag v-if="row.checked === 1" type="success" size="small">已检</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button v-if="row.uploaded !== 1" link type="primary" size="small" @click="openBindDialog(row)">绑定文件</el-button>
            <el-button v-if="row.uploaded === 1 && row.checked !== 1" link type="success" size="small" @click="checkOne(row)">检查</el-button>
            <el-button link type="danger" size="small" @click="removeDeliverable(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!deliverables.length" description="暂无清单，点击「从模板生成」按项目类型自动生成" :image-size="60" />
    </el-card>

    <!-- 阶段门禁 -->
    <el-card shadow="hover" style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span>阶段门禁</span>
          <el-button type="primary" size="small" @click="gateDialog = true">新建门禁</el-button>
        </div>
      </template>
      <el-table :data="gates" size="small">
        <el-table-column prop="gateName" label="门禁" min-width="140" />
        <el-table-column label="阶段" width="130">
          <template #default="{ row }: any">{{ phaseNameOf(row.phaseId) }}</template>
        </el-table-column>
        <el-table-column prop="passCondition" label="通过条件" min-width="140" show-overflow-tooltip />
        <el-table-column label="审核结果" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.auditResult === 1 ? 'success' : row.auditResult === 2 ? 'danger' : 'info'" size="small">
              {{ ['待审核', '通过', '驳回'][row.auditResult ?? 0] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="auditBy" label="审核人" width="90" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }: any">
            <template v-if="row.auditResult === 0">
              <el-button link type="success" size="small" @click="auditGate(row, true)">通过</el-button>
              <el-button link type="danger" size="small" @click="auditGate(row, false)">驳回</el-button>
            </template>
            <el-button link type="danger" size="small" @click="removeGate(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!gates.length" description="暂无门禁" :image-size="60" />
    </el-card>

    <!-- 上传项目资料 -->
    <el-dialog v-model="materialDialog" title="上传项目资料" width="520px">
      <el-form label-width="90px">
        <el-form-item label="资料类型">
          <el-input v-model="materialForm.fileType" maxlength="50" placeholder="如：立项资料 / 合同 / 技术协议" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="materialForm.description" maxlength="200" placeholder="资料说明（可选）" />
        </el-form-item>
        <el-form-item label="资料文件" required>
          <el-upload
            v-model:file-list="materialUploadFiles"
            :auto-upload="false"
            multiple
            drag
            style="width: 100%"
          >
            <el-button type="primary" plain>选择文件（可多选）</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="materialDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="uploadMaterials">上传</el-button>
      </template>
    </el-dialog>

    <!-- 新建门禁 -->
    <el-dialog v-model="gateDialog" title="新建阶段门禁" width="480px">
      <el-form ref="gateFormRef" :model="gateForm" :rules="gateRules" label-width="90px">
        <el-form-item label="门禁名称" prop="gateName">
          <el-input v-model="gateForm.gateName" maxlength="200" placeholder="如：设计评审门禁" />
        </el-form-item>
        <el-form-item label="关联阶段">
          <el-select v-model="gateForm.phaseId" clearable style="width: 100%">
            <el-option v-for="p in phases" :key="p.id" :label="p.phaseName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="通过条件">
          <el-input v-model="gateForm.passCondition" type="textarea" :rows="3" placeholder="如：交付物全部检查通过" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gateDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createGate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 绑定文件 -->
    <el-dialog v-model="bindDialog" title="绑定已上传文件" width="480px">
      <el-form label-width="90px">
        <el-form-item label="交付物">
          <span>{{ bindTarget?.deliverableName }}</span>
        </el-form-item>
        <el-form-item label="选择文件" required>
          <el-select v-model="bindFileId" filterable style="width: 100%" placeholder="从档案归档中选择已上传文件">
            <el-option v-for="f in myFiles" :key="f.id" :label="`${f.fileName}（${f.version}）`" :value="f.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bindDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="bindFile">绑定</el-button>
      </template>
    </el-dialog>

    <!-- 添加成员 -->
    <el-dialog v-model="memberDialog" title="添加项目成员" width="420px">
      <el-form label-width="80px">
        <el-form-item label="用户" required>
          <el-select v-model="memberForm.userId" filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目角色">
          <el-select v-model="memberForm.roleType" allow-create filterable style="width: 100%">
            <el-option label="项目经理" value="项目经理" />
            <el-option label="技术负责人" value="技术负责人" />
            <el-option label="成员" value="成员" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="memberDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="addMember">添加</el-button>
      </template>
    </el-dialog>

    <!-- 新建里程碑 -->
    <el-dialog v-model="msDialog" title="新建里程碑" width="480px">
      <el-form ref="msFormRef" :model="msForm" :rules="msRules" label-width="90px">
        <el-form-item label="名称" prop="milestoneName">
          <el-input v-model="msForm.milestoneName" maxlength="200" />
        </el-form-item>
        <el-form-item label="关联阶段">
          <el-select v-model="msForm.phaseId" clearable style="width: 100%">
            <el-option v-for="p in phases" :key="p.id" :label="p.phaseName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="msForm.planDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="msForm.description" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="msDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createMs">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getProjectDetailApi, getProjectPhasesApi, updatePhaseStatusApi,
  listMembersApi, addMemberApi, removeMemberApi,
  listMilestonesApi, createMilestoneApi, markMilestoneApi, deleteMilestoneApi,
  generateDeliverablesApi, listDeliverablesApi, deliverableStatsApi,
  bindDeliverableFileApi, checkDeliverableApi, deleteDeliverableApi,
  listGatesApi, createGateApi, auditGateApi, deleteGateApi,
  type ProjectItem, type ProjectPhase, type ProjectMember, type Milestone,
  type DeliverableChecklist
} from '@/api/project'
import { listFilesApi, uploadFileApi, downloadFileApi } from '@/api/file'
import { getUserOptionsApi, type SysUser } from '@/api/user'

const route = useRoute()
const projectId = Number(route.params.id)
const loading = ref(false)
const saving = ref(false)
const project = ref<ProjectItem | null>(null)
const phases = ref<ProjectPhase[]>([])
const members = ref<ProjectMember[]>([])
const milestones = ref<Milestone[]>([])
const userOptions = ref<SysUser[]>([])

const memberDialog = ref(false)
const memberForm = reactive({ userId: undefined as number | undefined, roleType: '成员' })
const msDialog = ref(false)
const msFormRef = ref<FormInstance>()
const msForm = reactive<Partial<Milestone>>({ milestoneName: '', phaseId: undefined, planDate: '', description: '' })
const msRules: FormRules = { milestoneName: [{ required: true, message: '请输入里程碑名称', trigger: 'blur' }] }

// ---------- 交付物 ----------
const deliverables = ref<DeliverableChecklist[]>([])
const stats = ref<{ total: number; uploaded: number; checked: number; requiredMissing: number } | null>(null)
const generating = ref(false)
const bindDialog = ref(false)
const bindTarget = ref<DeliverableChecklist | null>(null)
const bindFileId = ref<number>()
const myFiles = ref<any[]>([])

const phaseNameOf = (phaseId?: number) => phases.value.find(p => p.id === phaseId)?.phaseName || '-'

async function loadDeliverables() {
  try {
    const [listRes, statRes] = await Promise.all([
      listDeliverablesApi(projectId), deliverableStatsApi(projectId)
    ])
    deliverables.value = listRes.data || []
    stats.value = statRes.data
  } catch (e) {}
}

async function generate() {
  generating.value = true
  try {
    const res = await generateDeliverablesApi(projectId)
    ElMessage.success(`已生成 ${res.data.created} 项交付物`)
    loadDeliverables()
  } catch (e) {
  } finally {
    generating.value = false
  }
}

async function openBindDialog(row: DeliverableChecklist) {
  bindTarget.value = row
  bindFileId.value = undefined
  if (!myFiles.value.length) {
    try {
      const res = await listFilesApi({ pageNum: 1, pageSize: 200 })
      myFiles.value = res?.data?.records ?? []
    } catch (e) {}
  }
  bindDialog.value = true
}

async function bindFile() {
  if (!bindTarget.value || !bindFileId.value) {
    ElMessage.warning('请选择文件')
    return
  }
  saving.value = true
  try {
    await bindDeliverableFileApi(bindTarget.value.id, bindFileId.value)
    ElMessage.success('绑定成功')
    bindDialog.value = false
    loadDeliverables()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

async function checkOne(row: DeliverableChecklist) {
  try {
    await checkDeliverableApi(row.id)
    ElMessage.success('检查通过')
    loadDeliverables()
  } catch (e) {}
}

async function removeDeliverable(row: DeliverableChecklist) {
  try {
    await ElMessageBox.confirm(`确认删除交付物【${row.deliverableName}】？`, '提示', { type: 'warning' })
    await deleteDeliverableApi(row.id)
    ElMessage.success('删除成功')
    loadDeliverables()
  } catch (e) {}
}

// ---------- 项目资料明细 ----------
const projectFiles = ref<any[]>([])
const materialDialog = ref(false)
const materialUploadFiles = ref<any[]>([])
const materialForm = reactive({ fileType: '项目资料', description: '项目资料' })
const uploading = ref(false)

async function loadProjectFiles() {
  try {
    const res = await listFilesApi({ projectId })
    projectFiles.value = res?.data?.records ?? []
  } catch (e) {}
}

function openMaterialDialog() {
  materialUploadFiles.value = []
  materialForm.fileType = '项目资料'
  materialForm.description = '项目资料'
  materialDialog.value = true
}

async function uploadMaterials() {
  if (!materialUploadFiles.value.length) {
    ElMessage.warning('请选择资料文件')
    return
  }
  uploading.value = true
  let ok = 0
  let fail = 0
  try {
    for (const f of materialUploadFiles.value) {
      try {
        const fd = new FormData()
        fd.append('projectId', String(projectId))
        fd.append('fileType', materialForm.fileType || '项目资料')
        fd.append('description', materialForm.description || '')
        fd.append('file', f.raw)
        await uploadFileApi(fd)
        ok++
      } catch (e) {
        fail++
      }
    }
    if (fail === 0) {
      ElMessage.success(`${ok} 份资料已上传并保存到项目服务器`)
    } else {
      ElMessage.warning(`${ok} 份成功、${fail} 份失败`)
    }
    materialDialog.value = false
    loadProjectFiles()
  } finally {
    uploading.value = false
  }
}

async function downloadProjFile(row: any) {
  try {
    const res: any = await downloadFileApi(row.id)
    const blob = res instanceof Blob ? res : new Blob([res?.data ?? res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

// ---------- 阶段门禁 ----------
const gates = ref<any[]>([])
const gateDialog = ref(false)
const gateFormRef = ref<FormInstance>()
const gateForm = reactive({ gateName: '', phaseId: undefined as number | undefined, passCondition: '' })
const gateRules: FormRules = { gateName: [{ required: true, message: '请输入门禁名称', trigger: 'blur' }] }

async function loadGates() {
  try {
    const res = await listGatesApi(projectId)
    gates.value = res.data || []
  } catch (e) {}
}

async function createGate() {
  if (!gateFormRef.value) return
  await gateFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await createGateApi(projectId, { ...gateForm })
      ElMessage.success('门禁创建成功')
      gateDialog.value = false
      Object.assign(gateForm, { gateName: '', phaseId: undefined, passCondition: '' })
      loadGates()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function auditGate(row: any, pass: boolean) {
  try {
    const { value } = await ElMessageBox.prompt(
      pass ? '审核意见（可选）' : '驳回原因（建议填写）',
      pass ? '门禁通过' : '门禁驳回',
      { inputType: 'textarea', inputValue: '' }
    )
    await auditGateApi(row.id, pass, value || '')
    ElMessage.success(pass ? '门禁已通过' : '已驳回')
    loadGates()
  } catch (e) {}
}

async function removeGate(row: any) {
  try {
    await ElMessageBox.confirm(`确认删除门禁【${row.gateName}】？`, '提示', { type: 'warning' })
    await deleteGateApi(row.id)
    ElMessage.success('删除成功')
    loadGates()
  } catch (e) {}
}

const userNameOf = (id?: number) => userOptions.value.find(u => u.id === id)?.realName || `#${id}`
const msText = (s?: number) => ['未开始', '已完成', '延期'][s ?? 0] || '未知'
const msTag = (s?: number) => (['info', 'success', 'warning'] as const)[s ?? 0] || 'info'
const msType = (s?: number): 'success' | 'warning' | 'info' => (s === 1 ? 'success' : s === 2 ? 'warning' : 'info')

const currentPhaseIndex = computed(() => {
  const idx = phases.value.findIndex(p => p.status === 1)
  if (idx >= 0) return idx
  return phases.value.filter(p => p.status === 2).length
})

const typeText = (t?: number) => ['整机厂', '技改', '研发'][((t ?? 1) - 1)] || '未知'
const statusText = (s?: number) => ['待启动', '进行中', '已完成', '已关闭', '暂停'][s ?? 0] || '未知'
const statusTag = (s?: number) => (['info', 'primary', 'success', 'danger', 'warning'] as const)[s ?? 0] || 'info'
const phaseStatusText = (s?: number) => ['未开始', '进行中', '已完成'][s ?? 0] || '未知'
const phaseTag = (s?: number) => (['info', 'primary', 'success'] as const)[s ?? 0] || 'info'
const phaseStepStatus = (s?: number) => s === 2 ? 'success' as const : s === 1 ? 'process' as const : 'wait' as const

async function loadData() {
  loading.value = true
  try {
    const [detailRes, phaseRes, memberRes, msRes] = await Promise.all([
      getProjectDetailApi(projectId), getProjectPhasesApi(projectId),
      listMembersApi(projectId), listMilestonesApi(projectId)
    ])
    project.value = detailRes.data
    phases.value = phaseRes.data || []
    members.value = memberRes.data || []
    milestones.value = msRes.data || []
  } finally {
    loading.value = false
  }
}

async function updatePhase(phaseId: number, status: number) {
  try {
    await updatePhaseStatusApi(phaseId, status)
    ElMessage.success('阶段状态更新成功')
    loadData()
  } catch (e) {}
}

async function addMember() {
  if (!memberForm.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  saving.value = true
  try {
    await addMemberApi(projectId, memberForm.userId, memberForm.roleType)
    ElMessage.success('添加成功')
    memberDialog.value = false
    memberForm.userId = undefined
    loadData()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

async function removeMember(row: ProjectMember) {
  try {
    await ElMessageBox.confirm(`确认移除成员【${userNameOf(row.userId)}】？`, '提示', { type: 'warning' })
    await removeMemberApi(row.id)
    ElMessage.success('已移除')
    loadData()
  } catch (e) {}
}

function openMsDialog() {
  Object.assign(msForm, { milestoneName: '', phaseId: undefined, planDate: '', description: '' })
  msDialog.value = true
}

async function createMs() {
  if (!msFormRef.value) return
  await msFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await createMilestoneApi(projectId, { ...msForm })
      ElMessage.success('里程碑创建成功')
      msDialog.value = false
      loadData()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function mark(m: Milestone, status: number) {
  try {
    await markMilestoneApi(m.id, status)
    ElMessage.success(status === 1 ? '已标记完成' : '已标记延期')
    loadData()
  } catch (e) {}
}

async function removeMs(m: Milestone) {
  try {
    await ElMessageBox.confirm(`确认删除里程碑【${m.milestoneName}】？`, '提示', { type: 'warning' })
    await deleteMilestoneApi(m.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

onMounted(async () => {
  await loadData()
  await loadDeliverables()
  await loadGates()
  await loadProjectFiles()
  try {
    const res = await getUserOptionsApi()
    userOptions.value = res.data
  } catch (e) {}
})
</script>

<style lang="scss" scoped>
.page-header-title { font-size: 16px; font-weight: bold; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ms-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ms-actions {
  margin-top: 4px;
}
</style>
