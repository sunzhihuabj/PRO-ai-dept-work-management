<template>
  <div class="app-container">
    <el-tabs v-model="activeTab">
      <!-- ============ Tab 1: 方案版本 ============ -->
      <el-tab-pane label="方案版本" name="schemes">
        <el-card shadow="never">
          <div class="toolbar">
            <el-input-number v-model="queryProjectId" placeholder="项目ID" :controls="false" style="width: 140px" />
            <el-select v-model="queryStatus" placeholder="状态" clearable style="width: 130px">
              <el-option label="草稿" value="DRAFT" />
              <el-option label="已受控发布" value="RELEASED" />
              <el-option label="已归档" value="ARCHIVED" />
            </el-select>
            <el-button type="primary" @click="loadSchemes">查询</el-button>
            <el-button type="success" @click="activeTab = 'draft'">AI 起草方案</el-button>
          </div>
          <el-table v-loading="loading" :data="schemes" border stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column label="版本" width="70" align="center">
              <template #default="{ row }">v{{ row.versionNo }}</template>
            </el-table-column>
            <el-table-column prop="title" label="方案标题" min-width="200" show-overflow-tooltip />
            <el-table-column label="来源" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.genType === 'AI' ? 'warning' : 'info'" size="small">{{ row.genType === 'AI' ? 'AI 起草' : '人工' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="SCHEME_STATUS_TYPE[row.status || ''] || 'info'" size="small">{{ SCHEME_STATUS_LABEL[row.status || ''] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openDetail(row.id)">详情</el-button>
                <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="onPublish(row)">受控发布</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
                         layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end"
                         @current-change="loadSchemes" />
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 2: AI 起草 ============ -->
      <el-tab-pane label="AI 方案起草" name="draft">
        <el-card shadow="never" style="max-width: 720px">
          <el-alert type="info" :closable="false" show-icon title="AGT-09 依据模板库 + 项目信息 + 勘测记录起草方案草稿；受控发布须人工执行"
                    style="margin-bottom: 16px" />
          <el-form label-width="100px">
            <el-form-item label="项目 ID" required>
              <el-input-number v-model="draftProjectId" :min="1" :controls="false" style="width: 160px" />
            </el-form-item>
            <el-form-item label="场景">
              <el-select v-model="draftScene" style="width: 200px" allow-create filterable>
                <el-option v-for="t in templates" :key="t.id" :label="t.scene" :value="t.scene" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="drafting" :disabled="!draftProjectId" @click="onDraft">开始起草</el-button>
            </el-form-item>
          </el-form>
          <el-descriptions v-if="draftResult" :column="1" border title="起草结果">
            <el-descriptions-item label="方案 ID">{{ draftResult.schemeId }}</el-descriptions-item>
            <el-descriptions-item label="匹配模板">{{ draftResult.template }}</el-descriptions-item>
            <el-descriptions-item label="标题">{{ draftResult.title }}</el-descriptions-item>
            <el-descriptions-item label="正文预览"><pre class="preview">{{ draftResult.contentPreview }}</pre></el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 3: BOM 校验 ============ -->
      <el-tab-pane label="BOM 校验" name="bom">
        <el-card shadow="never">
          <div class="toolbar">
            <span>方案 ID：</span>
            <el-input-number v-model="bomSchemeId" :min="1" :controls="false" style="width: 120px" />
            <el-button type="primary" :loading="bomLoading" :disabled="!bomSchemeId" @click="loadBom">加载 BOM</el-button>
            <el-button type="success" :disabled="!bomSchemeId || bomLocked" @click="onSaveBom">保存 BOM</el-button>
            <el-button type="warning" :disabled="!bomSchemeId" @click="onCheckBom">执行 13 条规则校验</el-button>
            <el-tag v-if="bomLocked" type="danger">方案已发布，BOM 锁定</el-tag>
          </div>
          <el-table :data="bomItems" border size="small">
            <el-table-column type="index" width="50" />
            <el-table-column label="物料编码" width="150">
              <template #default="{ row }"><el-input v-model="row.materialCode" :disabled="bomLocked" size="small" /></template>
            </el-table-column>
            <el-table-column label="物料名称" width="170">
              <template #default="{ row }"><el-input v-model="row.materialName" :disabled="bomLocked" size="small" /></template>
            </el-table-column>
            <el-table-column label="规格型号" width="170">
              <template #default="{ row }"><el-input v-model="row.specModel" :disabled="bomLocked" size="small" /></template>
            </el-table-column>
            <el-table-column label="数量" width="110">
              <template #default="{ row }"><el-input-number v-model="row.qty" :controls="false" :disabled="bomLocked" size="small" style="width: 90px" /></template>
            </el-table-column>
            <el-table-column label="单位" width="90">
              <template #default="{ row }"><el-input v-model="row.unit" :disabled="bomLocked" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ $index }">
                <el-button link type="danger" size="small" :disabled="bomLocked" @click="bomItems.splice($index, 1)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" :disabled="bomLocked" style="margin-top: 8px" @click="bomItems.push(emptyBomRow())">+ 增行</el-button>

          <template v-if="checkResult">
            <el-divider />
            <el-alert :type="checkResult.passed ? 'success' : 'error'" :closable="false" show-icon
                      :title="checkResult.passed ? '校验通过：13 条规则全部满足' : `校验未通过：${checkResult.violations.length} 条违规`" />
            <el-table v-if="!checkResult.passed" :data="checkResult.violations" border size="small" style="margin-top: 8px">
              <el-table-column prop="rule" label="规则" width="80" />
              <el-table-column prop="materialCode" label="物料编码" width="140" />
              <el-table-column prop="message" label="违规说明" />
            </el-table>
          </template>
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 4: 勘测记录 ============ -->
      <el-tab-pane label="勘测记录" name="survey">
        <el-card shadow="never" style="max-width: 720px">
          <div class="toolbar">
            <span>项目 ID：</span>
            <el-input-number v-model="surveyProjectId" :min="1" :controls="false" style="width: 120px" />
            <el-button type="primary" @click="loadSurveys">查询</el-button>
          </div>
          <el-table :data="surveys" border size="small">
            <el-table-column prop="siteName" label="点位" width="140" />
            <el-table-column prop="surveyDate" label="日期" width="110" />
            <el-table-column prop="surveyor" label="勘测人" width="90" />
            <el-table-column prop="windClass" label="风区" width="90" />
            <el-table-column prop="content" label="结论" show-overflow-tooltip />
          </el-table>
          <el-divider>新增记录</el-divider>
          <el-form inline>
            <el-form-item label="点位"><el-input v-model="newSurvey.siteName" style="width: 140px" /></el-form-item>
            <el-form-item label="勘测人"><el-input v-model="newSurvey.surveyor" style="width: 100px" /></el-form-item>
            <el-form-item label="风区"><el-input v-model="newSurvey.windClass" style="width: 110px" /></el-form-item>
            <el-form-item label="结论"><el-input v-model="newSurvey.content" style="width: 200px" /></el-form-item>
            <el-form-item><el-button type="primary" @click="onCreateSurvey">保存</el-button></el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- ============ Tab 5: 模板库 ============ -->
      <el-tab-pane label="方案模板库" name="templates">
        <el-card shadow="never">
          <el-table :data="templates" border size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="templateName" label="模板名称" width="180" />
            <el-table-column prop="scene" label="场景" width="100" />
            <el-table-column prop="specRefs" label="引用规范" width="220" />
            <el-table-column prop="templateText" label="模板正文" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 方案详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detail ? `方案详情 v${detail.scheme.versionNo}` : '方案详情'" size="640px">
      <template v-if="detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="标题">{{ detail.scheme.title }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="SCHEME_STATUS_TYPE[detail.scheme.status || '']" size="small">{{ SCHEME_STATUS_LABEL[detail.scheme.status || ''] }}</el-tag>
            <span v-if="detail.scheme.releasedBy"> — {{ detail.scheme.releasedBy }} 于 {{ detail.scheme.releaseTime }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <h4>方案正文</h4>
        <pre class="preview">{{ detail.scheme.content }}</pre>
        <h4>BOM 明细（{{ detail.bom.length }}）</h4>
        <el-table :data="detail.bom" border size="small">
          <el-table-column prop="materialCode" label="编码" width="110" />
          <el-table-column prop="materialName" label="名称" />
          <el-table-column prop="qty" label="数量" width="70" />
          <el-table-column prop="unit" label="单位" width="60" />
        </el-table>
        <h4>校验记录</h4>
        <el-timeline style="padding-left: 4px">
          <el-timeline-item v-for="log in detail.checkLogs" :key="log.id" :timestamp="log.createTime"
                            :type="(log.passed ? 'success' : 'danger') as any">
            {{ log.checker }} — {{ log.passed ? '通过' : '有缺陷' }}（{{ log.totalItems }} 项）
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  checkBomApi, createSurveyApi, getSchemeDetailApi, listSchemesApi, listSurveysApi,
  listTemplatesApi, publishSchemeApi, replaceBomApi,
  type BomItem, type DesignScheme, type DesignSurvey, type DesignTemplate, type BomViolation
} from '@/api/design'
import { submitInstructionApi, getTaskDetailApi } from '@/api/agent'

const SCHEME_STATUS_LABEL: Record<string, string> = { DRAFT: '草稿', RELEASED: '已受控发布', ARCHIVED: '已归档' }
const SCHEME_STATUS_TYPE: Record<string, any> = { DRAFT: 'info', RELEASED: 'success', ARCHIVED: 'info' }

// ===== 方案列表 =====
const activeTab = ref('schemes')
const schemes = ref<DesignScheme[]>([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const queryProjectId = ref<number | undefined>()
const queryStatus = ref('')

async function loadSchemes() {
  loading.value = true
  try {
    const res = await listSchemesApi(pageNum.value, pageSize.value, queryProjectId.value, queryStatus.value || undefined)
    schemes.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function onPublish(row: any) {
  const { value } = await ElMessageBox.prompt('受控发布后方案不可修改，是否继续？请输入发布说明', '受控发布', {
    inputPlaceholder: '发布说明',
    inputValidator: (v: string) => !!v || '请输入发布说明'
  })
  await publishSchemeApi(row.id!, value)
  ElMessage.success('已受控发布（design.released 事件已发出）')
  loadSchemes()
}

// ===== 详情 =====
const detailVisible = ref(false)
const detail = ref<{ scheme: DesignScheme; bom: BomItem[]; checkLogs: any[] } | null>(null)
async function openDetail(id: number) {
  const res = await getSchemeDetailApi(id)
  detail.value = res.data
  detailVisible.value = true
}

// ===== AI 起草 =====
const templates = ref<DesignTemplate[]>([])
const draftProjectId = ref<number>()
const draftScene = ref('塔筒')
const drafting = ref(false)
const draftResult = ref<any>(null)

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

async function onDraft() {
  drafting.value = true
  draftResult.value = null
  try {
    const t = await submitInstructionApi(`projectId=${draftProjectId.value} scene=${draftScene.value} 请起草制造方案`, 'AGT-09')
    const done = await waitTask(t.data.id)
    if (done.status !== 'done') throw new Error('起草失败：' + (done.errorMsg || done.status))
    draftResult.value = JSON.parse(done.resultText)
    ElMessage.success('AI 起草完成，草稿已入方案版本库')
    loadSchemes()
  } catch (e: any) {
    ElMessage.error(e?.message || '起草失败，请重试或转人工')
  } finally {
    drafting.value = false
  }
}

// ===== BOM 校验 =====
const bomSchemeId = ref<number>()
const bomItems = ref<BomItem[]>([])
const bomLoading = ref(false)
const bomLocked = ref(false)
const checkResult = ref<{ passed: boolean; violations: BomViolation[] } | null>(null)

function emptyBomRow(): BomItem {
  return { materialCode: '', materialName: '', specModel: '', qty: 1, unit: '件', remark: '' }
}

async function loadBom() {
  bomLoading.value = true
  checkResult.value = null
  try {
    const res = await getSchemeDetailApi(bomSchemeId.value!)
    bomItems.value = res.data.bom.length ? res.data.bom : [emptyBomRow()]
    bomLocked.value = res.data.scheme.status !== 'DRAFT'
  } finally {
    bomLoading.value = false
  }
}

async function onSaveBom() {
  await replaceBomApi(bomSchemeId.value!, bomItems.value)
  ElMessage.success('BOM 已保存')
}

async function onCheckBom() {
  const res = await checkBomApi(bomSchemeId.value!)
  checkResult.value = res.data
  ElMessage.success(res.data.passed ? '校验通过' : `检出 ${res.data.violations.length} 条违规`)
}

// ===== 勘测记录 =====
const surveys = ref<DesignSurvey[]>([])
const surveyProjectId = ref<number>()
const newSurvey = ref<DesignSurvey>({ projectId: 0, siteName: '', surveyor: '', windClass: '', content: '' })

async function loadSurveys() {
  if (!surveyProjectId.value) return
  const res = await listSurveysApi(surveyProjectId.value)
  surveys.value = res.data
}

async function onCreateSurvey() {
  await createSurveyApi({ ...newSurvey.value, projectId: surveyProjectId.value! })
  ElMessage.success('勘测记录已保存')
  newSurvey.value = { projectId: 0, siteName: '', surveyor: '', windClass: '', content: '' }
  loadSurveys()
}

onMounted(() => {
  loadSchemes()
  listTemplatesApi().then((res) => (templates.value = res.data))
})
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }
.preview { white-space: pre-wrap; background: #f5f7fa; padding: 10px; border-radius: 4px; max-height: 320px; overflow: auto; }
</style>
