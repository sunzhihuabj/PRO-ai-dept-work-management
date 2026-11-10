<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">绩效管理</span></template>
    </el-page-header>

    <el-tabs v-model="activeTab">
      <!-- ================= 指标管理 ================= -->
      <el-tab-pane label="指标管理" name="indicator">
        <div class="search-bar">
          <el-input v-model="indicatorQuery.keyword" placeholder="指标名称/编码" clearable style="width: 200px"
                    @keyup.enter="loadIndicators" />
          <el-select v-model="indicatorQuery.projectType" placeholder="适用项目类型" clearable style="width: 150px">
            <el-option label="整机厂" :value="1" />
            <el-option label="技改" :value="2" />
            <el-option label="研发" :value="3" />
          </el-select>
          <el-button type="primary" @click="loadIndicators">查询</el-button>
          <el-button type="success" @click="openIndicatorDialog()">新增指标</el-button>
        </div>

        <el-table :data="indicators" v-loading="loading" border stripe>
          <el-table-column prop="indicatorName" label="指标名称" min-width="160" />
          <el-table-column prop="indicatorCode" label="编码" width="180" />
          <el-table-column label="项目类型" width="110" align="center">
            <template #default="{ row }: any">{{ projectTypeText(row.projectType) }}</template>
          </el-table-column>
          <el-table-column prop="indicatorType" label="类别" width="100" align="center" />
          <el-table-column prop="baseScore" label="基准分" width="90" align="center" />
          <el-table-column prop="unit" label="单位" width="90" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }: any">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center" fixed="right">
            <template #default="{ row }: any">
              <el-button link type="primary" @click="openIndicatorDialog(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDeleteIndicator(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ================= 绩效评分 ================= -->
      <el-tab-pane label="绩效评分" name="score">
        <div class="search-bar">
          <el-input v-model="period" placeholder="期间（如 2026-08）" style="width: 200px" />
          <el-button type="primary" :loading="calculating" @click="calculate">计算本期绩效</el-button>
          <el-button type="success" @click="loadScores">查询评分</el-button>
          <el-button type="warning" :loading="confirming" @click="confirmPeriod">发布本期</el-button>
          <el-button @click="loadReport">汇总报表</el-button>
          <el-button @click="exportReport">导出 CSV</el-button>
        </div>

        <el-table :data="scores" v-loading="scoreLoading" border stripe style="margin-bottom: 16px">
          <el-table-column prop="userId" label="用户ID" width="80" />
          <el-table-column label="用户名" width="120">
            <template #default="{ row }: any">{{ userNameOf(row.userId) }}</template>
          </el-table-column>
          <el-table-column label="指标" min-width="160">
            <template #default="{ row }: any">{{ indicatorNameOf(row.indicatorId) }}</template>
          </el-table-column>
          <el-table-column prop="baseScore" label="基准分" width="80" align="center" />
          <el-table-column prop="adjustScore" label="调整" width="80" align="center" />
          <el-table-column prop="finalScore" label="最终分" width="80" align="center" />
          <el-table-column label="等级" width="70" align="center">
            <template #default="{ row }: any">
              <el-tag :type="levelTag(row.level)" size="small">{{ row.level || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }: any">{{ scoreStatusText(row.status) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="{ row }: any">
              <el-button v-if="row.status === 0" link type="warning" @click="openAdjustDialog(row)">调整</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="report">
          <el-divider content-position="left">汇总报表（{{ report.period }}）</el-divider>
          <el-descriptions :column="3" border style="margin-bottom: 12px">
            <el-descriptions-item label="参与人数">{{ report.userCount }}</el-descriptions-item>
            <el-descriptions-item label="平均分">{{ report.avgScore }}</el-descriptions-item>
            <el-descriptions-item label="等级分布">
              <span v-for="(v, k) in report.levelCount" :key="k" style="margin-right: 8px">
                <el-tag size="small" :type="levelTag(k)">{{ k }}: {{ v }}</el-tag>
              </span>
            </el-descriptions-item>
          </el-descriptions>
          <el-table :data="report.rows" size="small" border>
            <el-table-column type="index" label="排名" width="70" align="center" />
            <el-table-column prop="userName" label="用户" />
            <el-table-column prop="totalScore" label="加权总分" width="120" align="center" />
            <el-table-column label="等级" width="100" align="center">
              <template #default="{ row }: any">
                <el-tag :type="levelTag(row.level)" size="small">{{ row.level }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 指标编辑对话框 -->
    <el-dialog v-model="indicatorDialog" :title="editingIndicator ? '编辑指标' : '新增指标'" width="540px">
      <el-form ref="indicatorFormRef" :model="indicatorForm" :rules="indicatorRules" label-width="90px">
        <el-form-item label="指标名称" prop="indicatorName">
          <el-input v-model="indicatorForm.indicatorName" maxlength="50" />
        </el-form-item>
        <el-form-item label="指标编码" prop="indicatorCode">
          <el-input v-model="indicatorForm.indicatorCode" maxlength="20" placeholder="如 KPI_QUALITY" />
        </el-form-item>
        <el-form-item label="项目类型">
          <el-select v-model="indicatorForm.projectType" placeholder="请选择" style="width: 100%">
            <el-option label="整机厂" :value="1" />
            <el-option label="技改" :value="2" />
            <el-option label="研发" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="indicatorForm.indicatorType" placeholder="请选择" style="width: 100%">
            <el-option label="进度" value="进度" />
            <el-option label="成本" value="成本" />
            <el-option label="质量" value="质量" />
            <el-option label="满意度" value="满意度" />
          </el-select>
        </el-form-item>
        <el-form-item label="基准分">
          <el-input-number v-model="indicatorForm.baseScore" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="indicatorForm.unit" maxlength="20" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="indicatorForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="indicatorForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="indicatorDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveIndicator">保存</el-button>
      </template>
    </el-dialog>

    <!-- 调整评分对话框 -->
    <el-dialog v-model="adjustDialog" title="调整评分（±10 分）" width="420px">
      <el-form label-width="80px">
        <el-form-item label="调整分">
          <el-input-number v-model="adjustForm.adjustScore" :min="-10" :max="10" />
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="adjustForm.adjustReason" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitAdjust">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getKpiIndicatorsApi, createKpiIndicatorApi, updateKpiIndicatorApi, deleteKpiIndicatorApi,
  getKpiScoresApi, calculateKpiApi, adjustKpiScoreApi, confirmKpiPeriodApi, getKpiReportApi,
  exportKpiReportApi,
  type KpiIndicator, type KpiScore, type KpiReport
} from '@/api/kpi'
import { getUserOptionsApi, type SysUser } from '@/api/user'

const activeTab = ref('indicator')
const loading = ref(false)
const saving = ref(false)
const calculating = ref(false)
const confirming = ref(false)
const scoreLoading = ref(false)

/* ---------- 指标管理 ---------- */
const indicators = ref<KpiIndicator[]>([])
const indicatorQuery = reactive({ keyword: '', projectType: undefined as number | undefined })
const indicatorDialog = ref(false)
const editingIndicator = ref<KpiIndicator | null>(null)
const indicatorFormRef = ref<FormInstance>()
const indicatorForm = reactive({
  indicatorName: '', indicatorCode: '', projectType: undefined as number | undefined,
  indicatorType: '', baseScore: 100, unit: '', description: '', status: 1
})
const indicatorRules: FormRules = {
  indicatorName: [{ required: true, message: '请输入指标名称', trigger: 'blur' }],
  indicatorCode: [{ required: true, message: '请输入指标编码', trigger: 'blur' }]
}

function projectTypeText(t?: number) {
  return t === 1 ? '整机厂' : t === 2 ? '技改' : t === 3 ? '研发' : '-'
}
function levelTag(l?: string) {
  return l === 'A' ? 'success' as const : l === 'B' ? 'primary' as const : l === 'C' ? 'warning' as const : 'danger' as const
}
function scoreStatusText(s?: number) {
  return s === 1 ? '已确认' : s === 2 ? '已归档' : '草稿'
}

async function loadIndicators() {
  loading.value = true
  try {
    const res = await getKpiIndicatorsApi(indicatorQuery)
    indicators.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openIndicatorDialog(row?: KpiIndicator) {
  editingIndicator.value = row ?? null
  if (row) {
    Object.assign(indicatorForm, {
      indicatorName: row.indicatorName, indicatorCode: row.indicatorCode, projectType: row.projectType,
      indicatorType: row.indicatorType, baseScore: row.baseScore ?? 100, unit: row.unit || '',
      description: row.description || '', status: row.status ?? 1
    })
  } else {
    Object.assign(indicatorForm, {
      indicatorName: '', indicatorCode: '', projectType: undefined,
      indicatorType: '', baseScore: 100, unit: '', description: '', status: 1
    })
  }
  indicatorDialog.value = true
}

async function saveIndicator() {
  if (!indicatorFormRef.value) return
  await indicatorFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (editingIndicator.value) {
        await updateKpiIndicatorApi(editingIndicator.value.id, { ...indicatorForm })
        ElMessage.success('指标更新成功')
      } else {
        await createKpiIndicatorApi({ ...indicatorForm })
        ElMessage.success('指标创建成功')
      }
      indicatorDialog.value = false
      loadIndicators()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function handleDeleteIndicator(row: KpiIndicator) {
  try {
    await ElMessageBox.confirm(`确认删除指标【${row.indicatorName}】？`, '提示', { type: 'warning' })
    await deleteKpiIndicatorApi(row.id)
    ElMessage.success('删除成功')
    loadIndicators()
  } catch (e) {}
}

/* ---------- 绩效评分 ---------- */
const period = ref('')
const scores = ref<KpiScore[]>([])
const userMap = ref<Record<number, SysUser>>({})
const indicatorMap = ref<Record<number, KpiIndicator>>({})
const adjustDialog = ref(false)
const adjustRow = ref<KpiScore | null>(null)
const adjustForm = reactive({ adjustScore: 0, adjustReason: '' })
const report = ref<KpiReport | null>(null)

const userNameOf = (id?: number) => userMap.value[id ?? 0]?.realName || `#${id}`
const indicatorNameOf = (id?: number) => indicatorMap.value[id ?? 0]?.indicatorName || `#${id}`

async function loadScores() {
  if (!period.value) {
    ElMessage.warning('请先填写期间（YYYY-MM）')
    return
  }
  scoreLoading.value = true
  try {
    const res = await getKpiScoresApi({ period: period.value })
    scores.value = res.data || []
  } finally {
    scoreLoading.value = false
  }
}

async function calculate() {
  if (!period.value) {
    ElMessage.warning('请先填写期间')
    return
  }
  calculating.value = true
  try {
    const res = await calculateKpiApi(period.value)
    ElMessage.success(`已生成 ${res.data.created} 条评分`)
    loadScores()
  } catch (e) {
  } finally {
    calculating.value = false
  }
}

async function confirmPeriod() {
  if (!period.value) {
    ElMessage.warning('请先填写期间')
    return
  }
  confirming.value = true
  try {
    await confirmKpiPeriodApi(period.value)
    ElMessage.success('发布完成')
    loadScores()
  } catch (e) {
  } finally {
    confirming.value = false
  }
}

function openAdjustDialog(row: KpiScore) {
  adjustRow.value = row
  Object.assign(adjustForm, { adjustScore: row.adjustScore || 0, adjustReason: row.adjustReason || '' })
  adjustDialog.value = true
}

async function submitAdjust() {
  if (!adjustRow.value) return
  saving.value = true
  try {
    await adjustKpiScoreApi(adjustRow.value.id, { ...adjustForm })
    ElMessage.success('评分已调整')
    adjustDialog.value = false
    loadScores()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

async function loadReport() {
  if (!period.value) {
    ElMessage.warning('请先填写期间')
    return
  }
  try {
    const res = await getKpiReportApi(period.value)
    report.value = res.data
  } catch (e) {}
}

async function exportReport() {
  if (!period.value) {
    ElMessage.warning('请先填写期间')
    return
  }
  try {
    const res: any = await exportKpiReportApi(period.value)
    const blob = res instanceof Blob ? res : new Blob([res?.data ?? res], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `绩效报表_${period.value}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

onMounted(async () => {
  await loadIndicators()
  try {
    const res = await getUserOptionsApi()
    userMap.value = Object.fromEntries((res.data || []).map(u => [u.id, u]))
  } catch (e) {}
  indicatorMap.value = Object.fromEntries(indicators.value.map(i => [i.id, i]))
})
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.search-bar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.page-header-title { font-size: 16px; font-weight: bold; }
</style>
