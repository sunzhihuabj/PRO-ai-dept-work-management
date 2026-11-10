<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">奖金管理</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <el-tabs v-model="activeTab">
        <!-- ============ 奖金配置 ============ -->
        <el-tab-pane label="奖金配置" name="config">
          <div class="search-bar">
            <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 200px">
              <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
            <el-select v-model="query.userId" placeholder="用户" clearable filterable style="width: 150px">
              <el-option v-for="u in users" :key="u.id" :label="u.realName" :value="u.id" />
            </el-select>
            <el-select v-model="query.bonusType" placeholder="奖金类型" clearable style="width: 120px">
              <el-option label="整机厂" :value="1" />
              <el-option label="技改" :value="2" />
              <el-option label="研发" :value="3" />
            </el-select>
            <el-button type="primary" @click="loadConfig">查询</el-button>
            <el-button type="success" @click="openConfigDialog()">新建配置</el-button>
          </div>

          <el-table v-loading="loading" :data="configs" border stripe>
            <el-table-column label="项目" min-width="160">
              <template #default="{ row }: any">{{ projectNameOf(row.projectId) }}</template>
            </el-table-column>
            <el-table-column label="用户" width="100">
              <template #default="{ row }: any">{{ userNameOf(row.userId) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }: any">{{ bonusTypeText(row.bonusType) }}</template>
            </el-table-column>
            <el-table-column label="阶段" width="80" align="center">
              <template #default="{ row }: any">{{ periodText(row.projectPeriod) }}</template>
            </el-table-column>
            <el-table-column prop="baseAmount" label="基础金额" width="100" align="right" />
            <el-table-column prop="coefficient" label="系数" width="70" align="center" />
            <el-table-column prop="monthlyAmount" label="月度奖金" width="100" align="right" />
            <el-table-column prop="yearlyAmount" label="年度奖金" width="100" align="right" />
            <el-table-column label="已发(月/年)" width="120" align="right">
              <template #default="{ row }: any">{{ row.paidMonthly ?? 0 }} / {{ row.paidYearly ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }: any">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" align="center" fixed="right">
              <template #default="{ row }: any">
                <el-button link type="primary" size="small" @click="openConfigDialog(row)">编辑</el-button>
                <el-button link type="warning" size="small" @click="openKpiDialog(row)">KPI联动</el-button>
                <el-button link type="success" size="small" @click="openPayDialog(row)">发放</el-button>
                <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :total="configTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadConfig"
            @current-change="loadConfig"
            style="margin-top: 16px; justify-content: flex-end; display: flex;"
          />
        </el-tab-pane>

        <!-- ============ 发放记录 ============ -->
        <el-tab-pane label="发放记录" name="records">
          <div class="search-bar">
            <el-input v-model="recordQuery.payPeriod" placeholder="发放周期(YYYY-MM)" clearable style="width: 180px" @keyup.enter="loadRecords" />
            <el-select v-model="recordQuery.payType" placeholder="发放类型" clearable style="width: 120px">
              <el-option label="月度" :value="1" />
              <el-option label="年度" :value="2" />
            </el-select>
            <el-button type="primary" @click="loadRecords">查询</el-button>
          </div>

          <el-table v-loading="recordLoading" :data="records" border stripe>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="项目" min-width="150">
              <template #default="{ row }: any">{{ projectNameOf(row.projectId) }}</template>
            </el-table-column>
            <el-table-column label="用户" width="100">
              <template #default="{ row }: any">{{ userNameOf(row.userId) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="80" align="center">
              <template #default="{ row }: any">{{ row.payType === 1 ? '月度' : '年度' }}</template>
            </el-table-column>
            <el-table-column prop="payAmount" label="金额" width="110" align="right" />
            <el-table-column prop="payPeriod" label="周期" width="100" align="center" />
            <el-table-column prop="payTime" label="发放时间" width="170" />
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          </el-table>

          <el-pagination
            v-model:current-page="recordQuery.pageNum"
            v-model:page-size="recordQuery.pageSize"
            :total="recordTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadRecords"
            @current-change="loadRecords"
            style="margin-top: 16px; justify-content: flex-end; display: flex;"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 配置编辑 -->
    <el-dialog v-model="configDialog" :title="editing ? '编辑配置' : '新建配置'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="项目" prop="projectId">
          <el-select v-model="form.projectId" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户" prop="userId">
          <el-select v-model="form.userId" filterable style="width: 100%">
            <el-option v-for="u in users" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="奖金类型" prop="bonusType">
          <el-select v-model="form.bonusType" style="width: 100%">
            <el-option label="整机厂" :value="1" />
            <el-option label="技改" :value="2" />
            <el-option label="研发" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目阶段">
          <el-select v-model="form.projectPeriod" clearable style="width: 100%">
            <el-option label="前期" :value="1" />
            <el-option label="中期" :value="2" />
            <el-option label="后期" :value="3" />
            <el-option label="大后期" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="基础金额" prop="baseAmount">
          <el-input-number v-model="form.baseAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="系数">
          <el-input-number v-model="form.coefficient" :min="0" :max="10" :precision="2" :step="0.1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- KPI 联动 -->
    <el-dialog v-model="kpiDialog" title="按KPI等级联动调系数" width="380px">
      <el-form label-width="80px">
        <el-form-item label="KPI期间" required>
          <el-input v-model="kpiPeriod" placeholder="如 2026-08" />
        </el-form-item>
      </el-form>
      <p style="color: #909399; font-size: 12px; margin: 0;">
        规则：A=1.2、B=1.0、C=0.8、D=0.6，调整后自动重算月度/年度奖金。
      </p>
      <template #footer>
        <el-button @click="kpiDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="applyKpi">确定</el-button>
      </template>
    </el-dialog>

    <!-- 发放 -->
    <el-dialog v-model="payDialog" title="发放奖金" width="400px">
      <el-form label-width="80px">
        <el-form-item label="发放类型">
          <el-radio-group v-model="payForm.payType">
            <el-radio :value="1">月度（{{ payTarget?.monthlyAmount }}）</el-radio>
            <el-radio :value="2">年度（{{ payTarget?.yearlyAmount }}）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发放周期" required>
          <el-input v-model="payForm.payPeriod" placeholder="YYYY-MM" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="payForm.remark" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="payDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="pay">确认发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listBonusApi, createBonusApi, updateBonusApi, deleteBonusApi,
  applyKpiApi, payBonusApi, listBonusRecordsApi,
  type ProjectBonus, type BonusRecord
} from '@/api/bonus'
import { getProjectListApi, type ProjectItem } from '@/api/project'
import { getUserOptionsApi, type SysUser } from '@/api/user'

const activeTab = ref('config')
const loading = ref(false)
const recordLoading = ref(false)
const saving = ref(false)

const projects = ref<ProjectItem[]>([])
const users = ref<SysUser[]>([])
const projectNameOf = (id?: number) => projects.value.find(p => p.id === id)?.projectName || `#${id}`
const userNameOf = (id?: number) => users.value.find(u => u.id === id)?.realName || `#${id}`
const bonusTypeText = (t?: number) => t === 1 ? '整机厂' : t === 2 ? '技改' : t === 3 ? '研发' : '-'
const periodText = (p?: number) => p === 1 ? '前期' : p === 2 ? '中期' : p === 3 ? '后期' : p === 4 ? '大后期' : '-'

// ---------- 配置 ----------
const configs = ref<ProjectBonus[]>([])
const configTotal = ref(0)
const query = reactive({
  pageNum: 1, pageSize: 10,
  projectId: undefined as number | undefined,
  userId: undefined as number | undefined,
  bonusType: undefined as number | undefined
})
const configDialog = ref(false)
const editing = ref<ProjectBonus | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  projectId: undefined as number | undefined,
  userId: undefined as number | undefined,
  bonusType: undefined as number | undefined,
  projectPeriod: undefined as number | undefined,
  baseAmount: 0,
  coefficient: 1
})
const rules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  userId: [{ required: true, message: '请选择用户', trigger: 'change' }],
  bonusType: [{ required: true, message: '请选择奖金类型', trigger: 'change' }],
  baseAmount: [{ required: true, message: '请输入基础金额', trigger: 'blur' }]
}

async function loadConfig() {
  loading.value = true
  try {
    const res = await listBonusApi(query)
    configs.value = res.data.records
    configTotal.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openConfigDialog(row?: ProjectBonus) {
  editing.value = row ?? null
  if (row) {
    Object.assign(form, {
      projectId: row.projectId, userId: row.userId, bonusType: row.bonusType,
      projectPeriod: row.projectPeriod, baseAmount: row.baseAmount ?? 0, coefficient: row.coefficient ?? 1
    })
  } else {
    Object.assign(form, { projectId: undefined, userId: undefined, bonusType: undefined, projectPeriod: undefined, baseAmount: 0, coefficient: 1 })
  }
  configDialog.value = true
}

async function save() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (editing.value) {
        await updateBonusApi(editing.value.id, { ...form } as any)
        ElMessage.success('配置更新成功')
      } else {
        await createBonusApi({ ...form } as any)
        ElMessage.success('配置创建成功')
      }
      configDialog.value = false
      loadConfig()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row: ProjectBonus) {
  try {
    await ElMessageBox.confirm('确认删除该奖金配置？', '提示', { type: 'warning' })
    await deleteBonusApi(row.id)
    ElMessage.success('删除成功')
    loadConfig()
  } catch (e) {}
}

// ---------- KPI 联动 ----------
const kpiDialog = ref(false)
const kpiPeriod = ref('')
const kpiTarget = ref<ProjectBonus | null>(null)

function openKpiDialog(row: ProjectBonus) {
  kpiTarget.value = row
  kpiPeriod.value = ''
  kpiDialog.value = true
}

async function applyKpi() {
  if (!kpiTarget.value) return
  if (!/^\d{4}-\d{2}$/.test(kpiPeriod.value)) {
    ElMessage.warning('请输入正确的期间格式 YYYY-MM')
    return
  }
  saving.value = true
  try {
    await applyKpiApi(kpiTarget.value.id, kpiPeriod.value)
    ElMessage.success('已按KPI等级调整系数')
    kpiDialog.value = false
    loadConfig()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

// ---------- 发放 ----------
const payDialog = ref(false)
const payTarget = ref<ProjectBonus | null>(null)
const payForm = reactive({ payType: 1, payPeriod: '', remark: '' })

function openPayDialog(row: ProjectBonus) {
  payTarget.value = row
  Object.assign(payForm, { payType: 1, payPeriod: '', remark: '' })
  payDialog.value = true
}

async function pay() {
  if (!payTarget.value) return
  if (!/^\d{4}-\d{2}$/.test(payForm.payPeriod)) {
    ElMessage.warning('请输入正确的周期格式 YYYY-MM')
    return
  }
  saving.value = true
  try {
    await payBonusApi(payTarget.value.id, { ...payForm })
    ElMessage.success('发放成功')
    payDialog.value = false
    loadConfig()
    loadRecords()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

// ---------- 发放记录 ----------
const records = ref<BonusRecord[]>([])
const recordTotal = ref(0)
const recordQuery = reactive({ pageNum: 1, pageSize: 10, payType: undefined as number | undefined, payPeriod: '' })

async function loadRecords() {
  recordLoading.value = true
  try {
    const res = await listBonusRecordsApi(recordQuery)
    records.value = res.data.records
    recordTotal.value = res.data.total
  } finally {
    recordLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadConfig(), loadRecords()])
  try {
    const [pRes, uRes] = await Promise.all([
      getProjectListApi({ pageNum: 1, pageSize: 500 }),
      getUserOptionsApi()
    ])
    projects.value = pRes.data.records
    users.value = uRes.data
  } catch (e) {}
})
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
.search-bar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
</style>
