<template>
  <div class="page-container">
    <el-card shadow="hover">
      <div class="search-bar">
        <el-input v-model="query.name" placeholder="项目名称" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="待启动" :value="0" />
          <el-option label="进行中" :value="1" />
          <el-option label="已完成" :value="2" />
          <el-option label="已关闭" :value="3" />
          <el-option label="暂停" :value="4" />
        </el-select>
        <el-select v-model="query.projectType" placeholder="项目类型" clearable style="width: 140px">
          <el-option label="整机厂" :value="1" />
          <el-option label="技改" :value="2" />
          <el-option label="研发" :value="3" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="success" :icon="Plus" @click="openCreate">新建项目</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe @row-click="(row: any) => goDetail(row.id)">
        <el-table-column prop="projectCode" label="项目编号" width="180" />
        <el-table-column prop="projectName" label="项目名称" show-overflow-tooltip min-width="200" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }: any">{{ typeText(row.projectType) }}</template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" width="140" />
        <el-table-column prop="projectLeader" label="负责人" width="100" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }: any">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contractAmount" label="合同金额" width="120" align="right">
          <template #default="{ row }: any">{{ row.contractAmount ? '¥' + row.contractAmount : '-' }}</template>
        </el-table-column>
        <el-table-column prop="planStartDate" label="开始日期" width="120" />
        <el-table-column prop="planEndDate" label="结束日期" width="120" />
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" @click.stop="goDetail(row.id)">详情</el-button>
            <el-button link type="success" @click.stop="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="loadData"
        @current-change="loadData"
        style="margin-top: 16px; justify-content: flex-end; display: flex;"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑项目' : '新建项目'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="请输入项目名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="项目类型" prop="projectType">
          <el-radio-group v-model="form.projectType">
            <el-radio :value="1">整机厂</el-radio>
            <el-radio :value="2">技改</el-radio>
            <el-radio :value="3">研发</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName">
          <el-input v-model="form.customerName" placeholder="请输入客户名称" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.projectLeader" placeholder="请输入负责人" />
        </el-form-item>
        <el-form-item label="合同金额">
          <el-input-number v-model="form.contractAmount" :min="0" :precision="2" style="width: 200px" />
        </el-form-item>
        <el-form-item label="计划开始日期" prop="planStartDate">
          <el-date-picker v-model="form.planStartDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="计划结束日期" prop="planEndDate">
          <el-date-picker v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item v-if="!editing" label="项目资料">
          <el-upload
            v-model:file-list="materialFiles"
            :auto-upload="false"
            multiple
            :on-exceed="() => ElMessage.warning('最多 10 份资料')"
            :limit="10"
            style="width: 100%"
          >
            <el-button type="primary" plain>选择资料文件</el-button>
            <template #tip>
              <div class="el-upload__tip">
                创建项目后将自动保存到项目服务器（{{ materialFiles.length }}/10 份，支持 docx/xlsx/pdf/dwg/图片/压缩包）
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getProjectListApi, createProjectApi, updateProjectApi, deleteProjectApi, type ProjectItem } from '@/api/project'
import { uploadFileApi } from '@/api/file'
import type { UploadUserFile } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const tableData = ref<ProjectItem[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, name: '', status: undefined as number | undefined, projectType: undefined as number | undefined })

const dialogVisible = ref(false)
const editing = ref(false)
const materialFiles = ref<UploadUserFile[]>([])
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  id: undefined as number | undefined,
  projectName: '', projectType: 1, customerName: '', projectLeader: '',
  contractAmount: undefined as number | undefined, targetCost: undefined as number | undefined,
  planStartDate: '', planEndDate: '', description: ''
})

const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  projectType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  customerName: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  planStartDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  planEndDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }]
}

const typeText = (t?: number) => ['整机厂', '技改', '研发'][((t ?? 1) - 1)] || '未知'
const statusText = (s?: number) => ['待启动', '进行中', '已完成', '已关闭', '暂停'][s ?? 0] || '未知'
const statusTag = (s?: number) => (['info', 'primary', 'success', 'danger', 'warning'] as const)[s ?? 0] || 'info'

async function loadData() {
  loading.value = true
  try {
    const res = await getProjectListApi(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.name = ''; query.status = undefined; query.projectType = undefined; query.pageNum = 1
  loadData()
}

const goDetail = (id: number) => router.push(`/project/detail/${id}`)

function openCreate() {
  editing.value = false
  Object.assign(form, { id: undefined, projectName: '', projectType: 1, customerName: '', projectLeader: '', contractAmount: undefined, targetCost: undefined, planStartDate: '', planEndDate: '', description: '' })
  dialogVisible.value = true
}

function openEdit(row: ProjectItem) {
  editing.value = true
  Object.assign(form, {
    id: row.id, projectName: row.projectName, projectType: row.projectType,
    customerName: row.customerName || '', projectLeader: row.projectLeader || '',
    contractAmount: row.contractAmount, targetCost: row.targetCost,
    planStartDate: row.planStartDate || '', planEndDate: row.planEndDate || '',
    description: row.description || ''
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const data = { ...form }
      if (editing.value && form.id) {
        await updateProjectApi(form.id, data)
        ElMessage.success('项目更新成功')
      } else {
        const res = await createProjectApi(data)
        const projectId = res.data
        // 项目资料随创建流程上传，按项目保存到服务器
        let uploaded = 0
        let failed = 0
        for (const f of materialFiles.value) {
          try {
            const fd = new FormData()
            fd.append('projectId', String(projectId))
            fd.append('fileType', '项目资料')
            fd.append('description', '项目立项资料')
            fd.append('file', f.raw as File)
            await uploadFileApi(fd)
            uploaded++
          } catch (e) {
            failed++
          }
        }
        if (materialFiles.value.length > 0) {
          if (failed === 0) {
            ElMessage.success(`项目创建成功，${uploaded} 份项目资料已保存到项目服务器`)
          } else {
            ElMessage.warning(`项目已创建，资料 ${uploaded} 份成功、${failed} 份失败（可在项目详情页重新上传）`)
          }
        } else {
          ElMessage.success('项目创建成功')
        }
        materialFiles.value = []
      }
      dialogVisible.value = false
      loadData()
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row: ProjectItem) {
  try {
    await ElMessageBox.confirm(`确定要删除项目"${row.projectName}"吗？`, '提示', { type: 'warning' })
    await deleteProjectApi(row.id)
    ElMessage.success('项目删除成功')
    loadData()
  } catch (e) {}
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.search-bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 16px; }
</style>
