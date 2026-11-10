<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">价值流分析</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <el-tabs v-model="activeTab">
        <!-- 价值流记录 -->
        <el-tab-pane label="价值流记录" name="vs">
          <div class="search-bar">
            <el-select v-model="vsQuery.projectId" placeholder="项目" clearable filterable style="width: 220px" @change="loadVs">
              <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
            <el-button type="primary" @click="loadVs">查询</el-button>
            <el-button type="success" @click="openVsDialog()">登记流程</el-button>
          </div>

          <el-table v-loading="vsLoading" :data="vsList" border stripe>
            <el-table-column prop="processName" label="流程" min-width="150" show-overflow-tooltip />
            <el-table-column prop="processType" label="类型" width="100" />
            <el-table-column prop="recordDate" label="日期" width="110" />
            <el-table-column prop="cycleTime" label="周期(h)" width="90" align="right" />
            <el-table-column prop="valueAddedTime" label="增值(h)" width="90" align="right" />
            <el-table-column prop="nonValueAddedTime" label="非增值(h)" width="100" align="right" />
            <el-table-column label="效率" width="100" align="center">
              <template #default="{ row }: any">
                <el-tag :type="((row.efficiencyRate ?? 0) >= 60 ? 'success' : (row.efficiencyRate ?? 0) >= 30 ? 'warning' : 'danger') as 'success' | 'warning' | 'danger'" size="small">
                  {{ row.efficiencyRate ?? '-' }}%
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" align="center" fixed="right">
              <template #default="{ row }: any">
                <el-button link type="primary" size="small" @click="openVsDialog(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeVs(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="vsQuery.pageNum" v-model:page-size="vsQuery.pageSize"
            :total="vsTotal" :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadVs" @current-change="loadVs"
            style="margin-top: 16px; justify-content: flex-end; display: flex;"
          />
        </el-tab-pane>

        <!-- 浪费登记 -->
        <el-tab-pane label="浪费登记" name="waste">
          <div class="search-bar">
            <el-select v-model="wQuery.projectId" placeholder="项目" clearable filterable style="width: 220px" @change="loadWaste">
              <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
            <el-select v-model="wQuery.wasteType" placeholder="浪费类型" clearable style="width: 130px" @change="loadWaste">
              <el-option v-for="t in WASTE_TYPES" :key="t" :label="t" :value="t" />
            </el-select>
            <el-button type="primary" @click="loadWaste">查询</el-button>
            <el-button type="success" @click="openWasteDialog()">登记浪费</el-button>
          </div>

          <el-table v-loading="wLoading" :data="wasteList" border stripe>
            <el-table-column prop="wasteType" label="类型" width="100" align="center" />
            <el-table-column prop="wasteDesc" label="描述" min-width="160" show-overflow-tooltip />
            <el-table-column prop="wasteSource" label="来源" min-width="120" show-overflow-tooltip />
            <el-table-column label="严重度" width="80" align="center">
              <template #default="{ row }: any">
                <el-tag :type="row.severity === 1 ? 'danger' : row.severity === 2 ? 'warning' : 'info'" size="small">
                  {{ ['高', '中', '低'][row.severity - 1] || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }: any">
                <el-tag :type="(['danger','primary','success','info'] as const)[row.status] || 'info'" size="small">{{ WASTE_STATUS[row.status] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="improvementAction" label="改进措施" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作" width="190" align="center" fixed="right">
              <template #default="{ row }: any">
                <el-button link type="primary" size="small" @click="openWasteDialog(row)">编辑</el-button>
                <el-button v-if="row.status < 3" link type="warning" size="small" @click="advanceW(row)">
                  {{ ['受理', '消除', '关闭'][row.status] }}
                </el-button>
                <el-button link type="danger" size="small" @click="removeW(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="wQuery.pageNum" v-model:page-size="wQuery.pageSize"
            :total="wTotal" :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadWaste" @current-change="loadWaste"
            style="margin-top: 16px; justify-content: flex-end; display: flex;"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 价值流编辑 -->
    <el-dialog v-model="vsDialog" :title="vsEditing ? '编辑流程' : '登记流程'" width="560px">
      <el-form ref="vsFormRef" :model="vsForm" :rules="vsRules" label-width="100px">
        <el-form-item label="所属项目" prop="projectId">
          <el-select v-model="vsForm.projectId" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程名称" prop="processName">
          <el-input v-model="vsForm.processName" maxlength="200" />
        </el-form-item>
        <el-form-item label="流程类型">
          <el-input v-model="vsForm.processType" maxlength="50" placeholder="如：图纸设计 / 现场施工" />
        </el-form-item>
        <el-form-item label="记录日期">
          <el-date-picker v-model="vsForm.recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="周期时间(h)">
          <el-input-number v-model="vsForm.cycleTime" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="增值时间(h)">
          <el-input-number v-model="vsForm.valueAddedTime" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="非增值(h)">
          <el-input-number v-model="vsForm.nonValueAddedTime" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="前置时间(天)">
          <el-input-number v-model="vsForm.leadTime" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="vsForm.description" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="vsDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveVs">保存</el-button>
      </template>
    </el-dialog>

    <!-- 浪费编辑 -->
    <el-dialog v-model="wDialog" :title="wEditing ? '编辑浪费' : '登记浪费'" width="560px">
      <el-form ref="wFormRef" :model="wForm" :rules="wRules" label-width="100px">
        <el-form-item label="所属项目" prop="projectId">
          <el-select v-model="wForm.projectId" filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="浪费类型" prop="wasteType">
          <el-select v-model="wForm.wasteType" style="width: 100%">
            <el-option v-for="t in WASTE_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="浪费描述">
          <el-input v-model="wForm.wasteDesc" maxlength="500" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="wForm.wasteSource" maxlength="200" />
        </el-form-item>
        <el-form-item label="严重程度">
          <el-radio-group v-model="wForm.severity">
            <el-radio :value="1">高</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="改进措施">
          <el-input v-model="wForm.improvementAction" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveW">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listValueStreamApi, saveValueStreamApi, deleteValueStreamApi,
  listWasteApi, saveWasteApi, advanceWasteApi, deleteWasteApi,
  WASTE_TYPES, WASTE_STATUS,
  type ValueStreamRecord, type WasteRecord
} from '@/api/lean'
import { getProjectListApi, type ProjectItem } from '@/api/project'

const activeTab = ref('vs')
const saving = ref(false)
const projects = ref<ProjectItem[]>([])

// ---------- 价值流 ----------
const vsLoading = ref(false)
const vsList = ref<ValueStreamRecord[]>([])
const vsTotal = ref(0)
const vsQuery = reactive({ pageNum: 1, pageSize: 10, projectId: undefined as number | undefined })
const vsDialog = ref(false)
const vsEditing = ref<ValueStreamRecord | null>(null)
const vsFormRef = ref<FormInstance>()
const vsForm = reactive<Partial<ValueStreamRecord>>({
  projectId: undefined, processName: '', processType: '', recordDate: '',
  cycleTime: 0, valueAddedTime: 0, nonValueAddedTime: 0, leadTime: 0, description: ''
})
const vsRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  processName: [{ required: true, message: '请输入流程名称', trigger: 'blur' }]
}

async function loadVs() {
  vsLoading.value = true
  try {
    const res = await listValueStreamApi(vsQuery)
    vsList.value = res.data.records
    vsTotal.value = res.data.total
  } finally {
    vsLoading.value = false
  }
}

function openVsDialog(row?: ValueStreamRecord) {
  vsEditing.value = row ?? null
  Object.assign(vsForm, row ? {
    id: row.id, projectId: row.projectId, processName: row.processName, processType: row.processType,
    recordDate: row.recordDate, cycleTime: row.cycleTime ?? 0, valueAddedTime: row.valueAddedTime ?? 0,
    nonValueAddedTime: row.nonValueAddedTime ?? 0, leadTime: row.leadTime ?? 0, description: row.description
  } : {
    id: undefined, projectId: undefined, processName: '', processType: '', recordDate: '',
    cycleTime: 0, valueAddedTime: 0, nonValueAddedTime: 0, leadTime: 0, description: ''
  })
  vsDialog.value = true
}

async function saveVs() {
  if (!vsFormRef.value) return
  await vsFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await saveValueStreamApi({ ...vsForm })
      ElMessage.success(vsEditing.value ? '更新成功' : '登记成功')
      vsDialog.value = false
      loadVs()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function removeVs(row: ValueStreamRecord) {
  try {
    await ElMessageBox.confirm(`确认删除流程【${row.processName}】？`, '提示', { type: 'warning' })
    await deleteValueStreamApi(row.id)
    ElMessage.success('删除成功')
    loadVs()
  } catch (e) {}
}

// ---------- 浪费 ----------
const wLoading = ref(false)
const wasteList = ref<WasteRecord[]>([])
const wTotal = ref(0)
const wQuery = reactive({ pageNum: 1, pageSize: 10, projectId: undefined as number | undefined, wasteType: undefined as string | undefined })
const wDialog = ref(false)
const wEditing = ref<WasteRecord | null>(null)
const wFormRef = ref<FormInstance>()
const wForm = reactive<Partial<WasteRecord>>({
  projectId: undefined, wasteType: '等待', wasteDesc: '', wasteSource: '', severity: 2, improvementAction: ''
})
const wRules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  wasteType: [{ required: true, message: '请选择浪费类型', trigger: 'change' }]
}

async function loadWaste() {
  wLoading.value = true
  try {
    const res = await listWasteApi(wQuery)
    wasteList.value = res.data.records
    wTotal.value = res.data.total
  } finally {
    wLoading.value = false
  }
}

function openWasteDialog(row?: WasteRecord) {
  wEditing.value = row ?? null
  Object.assign(wForm, row ? {
    id: row.id, projectId: row.projectId, wasteType: row.wasteType, wasteDesc: row.wasteDesc,
    wasteSource: row.wasteSource, severity: row.severity ?? 2, improvementAction: row.improvementAction
  } : {
    id: undefined, projectId: undefined, wasteType: '等待', wasteDesc: '', wasteSource: '', severity: 2, improvementAction: ''
  })
  wDialog.value = true
}

async function saveW() {
  if (!wFormRef.value) return
  await wFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      await saveWasteApi({ ...wForm })
      ElMessage.success(wEditing.value ? '更新成功' : '登记成功')
      wDialog.value = false
      loadWaste()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function advanceW(row: WasteRecord) {
  try {
    await advanceWasteApi(row.id, (row.status ?? 0) + 1)
    ElMessage.success('推进成功')
    loadWaste()
  } catch (e) {}
}

async function removeW(row: WasteRecord) {
  try {
    await ElMessageBox.confirm('确认删除该浪费记录？', '提示', { type: 'warning' })
    await deleteWasteApi(row.id)
    ElMessage.success('删除成功')
    loadWaste()
  } catch (e) {}
}

onMounted(async () => {
  await Promise.all([loadVs(), loadWaste()])
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
