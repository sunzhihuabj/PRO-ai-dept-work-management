<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">精益改进</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <el-tabs v-model="activeTab">
        <!-- ============ 改进提案 ============ -->
        <el-tab-pane label="改进提案" name="proposal">
          <div class="search-bar">
            <el-input v-model="pQuery.keyword" placeholder="标题/内容" clearable style="width: 180px" @keyup.enter="loadProposals" />
            <el-select v-model="pQuery.status" placeholder="状态" clearable style="width: 120px">
              <el-option v-for="(label, i) in PROPOSAL_STATUS" :key="i" :label="label" :value="i" />
            </el-select>
            <el-button type="primary" @click="loadProposals">查询</el-button>
            <el-button type="success" @click="openProposalDialog()">新建提案</el-button>
          </div>

          <el-table v-loading="pLoading" :data="proposals" border stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="proposalType" label="类型" width="100" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }: any">
                <el-tag :type="pStatusTag(row.status)" size="small">{{ PROPOSAL_STATUS[row.status] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reviewComment" label="审核意见" min-width="120" show-overflow-tooltip />
            <el-table-column prop="createTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="240" align="center" fixed="right">
              <template #default="{ row }: any">
                <el-button v-if="row.status === 0" link type="success" size="small" @click="openReviewDialog(row, true)">通过</el-button>
                <el-button v-if="row.status === 0" link type="danger" size="small" @click="openReviewDialog(row, false)">驳回</el-button>
                <el-button v-if="row.status === 1 || row.status === 2" link type="primary" size="small" @click="advance(row)">
                  {{ row.status === 1 ? '开始实施' : '标记完成' }}
                </el-button>
                <el-button v-if="row.status === 0 || row.status === 4" link type="primary" size="small" @click="openProposalDialog(row)">编辑</el-button>
                <el-button v-if="row.status === 0 || row.status === 4" link type="danger" size="small" @click="removeProposal(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="pQuery.pageNum" v-model:page-size="pQuery.pageSize"
            :total="pTotal" :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadProposals" @current-change="loadProposals"
            style="margin-top: 16px; justify-content: flex-end; display: flex;"
          />
        </el-tab-pane>

        <!-- ============ 根因分析 ============ -->
        <el-tab-pane label="根因分析" name="rca">
          <div class="search-bar">
            <el-select v-model="rQuery.analysisMethod" placeholder="分析方法" clearable style="width: 130px">
              <el-option v-for="m in RCA_METHODS" :key="m" :label="m" :value="m" />
            </el-select>
            <el-select v-model="rQuery.status" placeholder="状态" clearable style="width: 110px">
              <el-option v-for="(label, i) in RCA_STATUS" :key="i" :label="label" :value="i" />
            </el-select>
            <el-button type="primary" @click="loadRcas">查询</el-button>
            <el-button type="success" @click="openRcaDialog()">新建分析</el-button>
          </div>

          <el-table v-loading="rLoading" :data="rcas" border stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="problemDesc" label="问题描述" min-width="180" show-overflow-tooltip />
            <el-table-column prop="analysisMethod" label="方法" width="90" align="center" />
            <el-table-column prop="rootCause" label="根本原因" min-width="160" show-overflow-tooltip />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }: any">
                <el-tag :type="rStatusTag(row.status)" size="small">{{ RCA_STATUS[row.status] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createBy" label="创建人" width="100" />
            <el-table-column label="操作" width="220" align="center" fixed="right">
              <template #default="{ row }: any">
                <el-button link type="primary" size="small" @click="openRcaDialog(row)">编辑</el-button>
                <el-button v-if="row.status === 0" link type="success" size="small" @click="advanceRca(row, 1)">完成</el-button>
                <el-button v-if="row.status === 1" link type="warning" size="small" @click="advanceRca(row, 2)">关闭</el-button>
                <el-button link type="danger" size="small" @click="removeRca(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="rQuery.pageNum" v-model:page-size="rQuery.pageSize"
            :total="rTotal" :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadRcas" @current-change="loadRcas"
            style="margin-top: 16px; justify-content: flex-end; display: flex;"
          />
        </el-tab-pane>

        <!-- ============ 经验教训 ============ -->
        <el-tab-pane label="经验库" name="lesson">
          <div class="search-bar">
            <el-input v-model="lQuery.keyword" placeholder="标题/内容/关键词" clearable style="width: 200px" @keyup.enter="loadLessons" />
            <el-select v-model="lQuery.category" placeholder="类别" clearable style="width: 120px">
              <el-option v-for="c in LESSON_CATEGORIES" :key="c" :label="c" :value="c" />
            </el-select>
            <el-button type="primary" @click="loadLessons">查询</el-button>
            <el-button type="success" @click="openLessonDialog()">沉淀经验</el-button>
          </div>

          <el-table v-loading="lLoading" :data="lessons" border stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="category" label="类别" width="100" align="center" />
            <el-table-column label="影响" width="80" align="center">
              <template #default="{ row }: any">
                <el-tag :type="row.impactLevel === '高' ? 'danger' : row.impactLevel === '中' ? 'warning' : 'info'" size="small">
                  {{ row.impactLevel || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="keywords" label="关键词" min-width="120" show-overflow-tooltip />
            <el-table-column prop="createBy" label="创建人" width="100" />
            <el-table-column label="操作" width="140" align="center" fixed="right">
              <template #default="{ row }: any">
                <el-button link type="primary" size="small" @click="openLessonDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeLesson(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="lQuery.pageNum" v-model:page-size="lQuery.pageSize"
            :total="lTotal" :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadLessons" @current-change="loadLessons"
            style="margin-top: 16px; justify-content: flex-end; display: flex;"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 提案编辑 -->
    <el-dialog v-model="pDialog" :title="pEditing ? '编辑提案' : '新建提案'" width="640px">
      <el-form ref="pFormRef" :model="pForm" :rules="pRules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="pForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="所属项目">
          <el-select v-model="pForm.projectId" clearable filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="提案类型">
          <el-select v-model="pForm.proposalType" allow-create filterable clearable style="width: 100%">
            <el-option label="流程改进" value="流程改进" />
            <el-option label="质量提升" value="质量提升" />
            <el-option label="成本节约" value="成本节约" />
            <el-option label="安全改善" value="安全改善" />
          </el-select>
        </el-form-item>
        <el-form-item label="现状描述">
          <el-input v-model="pForm.currentState" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="目标状态">
          <el-input v-model="pForm.targetState" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="改进方案">
          <el-input v-model="pForm.proposalContent" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProposal">保存</el-button>
      </template>
    </el-dialog>

    <!-- 评审 -->
    <el-dialog v-model="reviewDialog" :title="reviewApproved ? '评审通过' : '驳回提案'" width="420px">
      <el-input v-model="reviewComment" type="textarea" :rows="3" maxlength="500" show-word-limit
                :placeholder="reviewApproved ? '审核意见（可选）' : '驳回原因（建议填写）'" />
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReview">确定</el-button>
      </template>
    </el-dialog>

    <!-- 根因分析编辑 -->
    <el-dialog v-model="rDialog" :title="rEditing ? '编辑分析' : '新建分析'" width="680px">
      <el-form ref="rFormRef" :model="rForm" :rules="rRules" label-width="90px">
        <el-form-item label="问题描述" prop="problemDesc">
          <el-input v-model="rForm.problemDesc" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="所属项目">
          <el-select v-model="rForm.projectId" clearable filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分析方法">
          <el-select v-model="rForm.analysisMethod" style="width: 100%">
            <el-option v-for="m in RCA_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="分析发现">
          <el-input v-model="rForm.findings" type="textarea" :rows="3" placeholder="5Why 逐层追问 / 鱼骨图要因等" />
        </el-form-item>
        <el-form-item label="根本原因">
          <el-input v-model="rForm.rootCause" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="纠正措施">
          <el-input v-model="rForm.correctiveAction" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="预防措施">
          <el-input v-model="rForm.preventiveAction" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRca">保存</el-button>
      </template>
    </el-dialog>

    <!-- 经验编辑 -->
    <el-dialog v-model="lDialog" :title="lEditing ? '编辑经验' : '沉淀经验'" width="560px">
      <el-form ref="lFormRef" :model="lForm" :rules="lRules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="lForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="所属项目" prop="projectId">
          <el-select v-model="lForm.projectId" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="lForm.category" style="width: 100%">
            <el-option v-for="c in LESSON_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="影响程度">
          <el-radio-group v-model="lForm.impactLevel">
            <el-radio value="高">高</el-radio>
            <el-radio value="中">中</el-radio>
            <el-radio value="低">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="lForm.content" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="lForm.keywords" maxlength="200" placeholder="多个用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveLesson">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listProposalsApi, createProposalApi, updateProposalApi, deleteProposalApi,
  reviewProposalApi, advanceProposalApi,
  listRcasApi, createRcaApi, updateRcaApi, advanceRcaApi, deleteRcaApi,
  listLessonsApi, createLessonApi, updateLessonApi, deleteLessonApi,
  PROPOSAL_STATUS, RCA_STATUS, RCA_METHODS, LESSON_CATEGORIES,
  type ImprovementProposal, type RootCauseAnalysis, type LessonsLearned
} from '@/api/lean'
import { getProjectListApi, type ProjectItem } from '@/api/project'

const activeTab = ref('proposal')
const saving = ref(false)
const projects = ref<ProjectItem[]>([])

const pStatusTag = (s?: number) => (['warning', 'primary', 'warning', 'success', 'danger'] as const)[s ?? 0] || 'info'
const rStatusTag = (s?: number) => (['warning', 'success', 'info'] as const)[s ?? 0] || 'info'

// ---------- 提案 ----------
const pLoading = ref(false)
const proposals = ref<ImprovementProposal[]>([])
const pTotal = ref(0)
const pQuery = reactive({ pageNum: 1, pageSize: 10, status: undefined as number | undefined, keyword: '' })
const pDialog = ref(false)
const pEditing = ref<ImprovementProposal | null>(null)
const pFormRef = ref<FormInstance>()
const pForm = reactive<Partial<ImprovementProposal>>({
  title: '', projectId: undefined, proposalType: '', currentState: '', targetState: '', proposalContent: ''
})
const pRules: FormRules = { title: [{ required: true, message: '请输入标题', trigger: 'blur' }] }
const reviewDialog = ref(false)
const reviewApproved = ref(true)
const reviewComment = ref('')
const reviewTarget = ref<ImprovementProposal | null>(null)

async function loadProposals() {
  pLoading.value = true
  try {
    const res = await listProposalsApi(pQuery)
    proposals.value = res.data.records
    pTotal.value = res.data.total
  } finally {
    pLoading.value = false
  }
}

function openProposalDialog(row?: ImprovementProposal) {
  pEditing.value = row ?? null
  if (row) {
    Object.assign(pForm, {
      title: row.title, projectId: row.projectId, proposalType: row.proposalType,
      currentState: row.currentState, targetState: row.targetState, proposalContent: row.proposalContent
    })
  } else {
    Object.assign(pForm, { title: '', projectId: undefined, proposalType: '', currentState: '', targetState: '', proposalContent: '' })
  }
  pDialog.value = true
}

async function saveProposal() {
  if (!pFormRef.value) return
  await pFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (pEditing.value) {
        await updateProposalApi(pEditing.value.id, { ...pForm })
        ElMessage.success('提案更新成功')
      } else {
        await createProposalApi({ ...pForm })
        ElMessage.success('提案创建成功')
      }
      pDialog.value = false
      loadProposals()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

function openReviewDialog(row: ImprovementProposal, approved: boolean) {
  reviewTarget.value = row
  reviewApproved.value = approved
  reviewComment.value = ''
  reviewDialog.value = true
}

async function submitReview() {
  if (!reviewTarget.value) return
  saving.value = true
  try {
    await reviewProposalApi(reviewTarget.value.id, reviewApproved.value, reviewComment.value)
    ElMessage.success(reviewApproved.value ? '已通过' : '已驳回')
    reviewDialog.value = false
    loadProposals()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

async function advance(row: ImprovementProposal) {
  await advanceProposalApi(row.id)
  ElMessage.success('推进成功')
  loadProposals()
}

async function removeProposal(row: ImprovementProposal) {
  try {
    await ElMessageBox.confirm(`确认删除提案【${row.title}】？`, '提示', { type: 'warning' })
    await deleteProposalApi(row.id)
    ElMessage.success('删除成功')
    loadProposals()
  } catch (e) {}
}

// ---------- 根因分析 ----------
const rLoading = ref(false)
const rcas = ref<RootCauseAnalysis[]>([])
const rTotal = ref(0)
const rQuery = reactive({ pageNum: 1, pageSize: 10, analysisMethod: undefined as string | undefined, status: undefined as number | undefined })
const rDialog = ref(false)
const rEditing = ref<RootCauseAnalysis | null>(null)
const rFormRef = ref<FormInstance>()
const rForm = reactive<Partial<RootCauseAnalysis>>({
  problemDesc: '', projectId: undefined, analysisMethod: '5Why',
  findings: '', rootCause: '', correctiveAction: '', preventiveAction: ''
})
const rRules: FormRules = { problemDesc: [{ required: true, message: '请输入问题描述', trigger: 'blur' }] }

async function loadRcas() {
  rLoading.value = true
  try {
    const res = await listRcasApi(rQuery)
    rcas.value = res.data.records
    rTotal.value = res.data.total
  } finally {
    rLoading.value = false
  }
}

function openRcaDialog(row?: RootCauseAnalysis) {
  rEditing.value = row ?? null
  if (row) {
    Object.assign(rForm, {
      problemDesc: row.problemDesc, projectId: row.projectId, analysisMethod: row.analysisMethod || '5Why',
      findings: row.findings, rootCause: row.rootCause, correctiveAction: row.correctiveAction, preventiveAction: row.preventiveAction
    })
  } else {
    Object.assign(rForm, {
      problemDesc: '', projectId: undefined, analysisMethod: '5Why',
      findings: '', rootCause: '', correctiveAction: '', preventiveAction: ''
    })
  }
  rDialog.value = true
}

async function saveRca() {
  if (!rFormRef.value) return
  await rFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (rEditing.value) {
        await updateRcaApi(rEditing.value.id, { ...rForm })
        ElMessage.success('分析更新成功')
      } else {
        await createRcaApi({ ...rForm })
        ElMessage.success('分析创建成功')
      }
      rDialog.value = false
      loadRcas()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function advanceRca(row: RootCauseAnalysis, target: number) {
  await advanceRcaApi(row.id, target)
  ElMessage.success('推进成功')
  loadRcas()
}

async function removeRca(row: RootCauseAnalysis) {
  try {
    await ElMessageBox.confirm('确认删除该根因分析？', '提示', { type: 'warning' })
    await deleteRcaApi(row.id)
    ElMessage.success('删除成功')
    loadRcas()
  } catch (e) {}
}

// ---------- 经验库 ----------
const lLoading = ref(false)
const lessons = ref<LessonsLearned[]>([])
const lTotal = ref(0)
const lQuery = reactive({ pageNum: 1, pageSize: 10, category: undefined as string | undefined, keyword: '' })
const lDialog = ref(false)
const lEditing = ref<LessonsLearned | null>(null)
const lFormRef = ref<FormInstance>()
const lForm = reactive<Partial<LessonsLearned>>({
  title: '', projectId: undefined, category: '成功经验', impactLevel: '中', content: '', keywords: ''
})
const lRules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }]
}

async function loadLessons() {
  lLoading.value = true
  try {
    const res = await listLessonsApi(lQuery)
    lessons.value = res.data.records
    lTotal.value = res.data.total
  } finally {
    lLoading.value = false
  }
}

function openLessonDialog(row?: LessonsLearned) {
  lEditing.value = row ?? null
  if (row) {
    Object.assign(lForm, {
      title: row.title, projectId: row.projectId, category: row.category || '成功经验',
      impactLevel: row.impactLevel || '中', content: row.content, keywords: row.keywords
    })
  } else {
    Object.assign(lForm, { title: '', projectId: undefined, category: '成功经验', impactLevel: '中', content: '', keywords: '' })
  }
  lDialog.value = true
}

async function saveLesson() {
  if (!lFormRef.value) return
  await lFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (lEditing.value) {
        await updateLessonApi(lEditing.value.id, { ...lForm })
        ElMessage.success('经验更新成功')
      } else {
        await createLessonApi({ ...lForm })
        ElMessage.success('经验沉淀成功')
      }
      lDialog.value = false
      loadLessons()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function removeLesson(row: LessonsLearned) {
  try {
    await ElMessageBox.confirm(`确认删除【${row.title}】？`, '提示', { type: 'warning' })
    await deleteLessonApi(row.id)
    ElMessage.success('删除成功')
    loadLessons()
  } catch (e) {}
}

onMounted(async () => {
  await Promise.all([loadProposals(), loadRcas(), loadLessons()])
  try {
    const res = await getProjectListApi({ pageNum: 1, pageSize: 500 })
    projects.value = res.data.records
  } catch (e) {}
})
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
.search-bar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
</style>
