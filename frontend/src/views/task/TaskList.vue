<template>
  <div class="page-container">
    <el-card shadow="hover">
      <div class="search-bar">
        <el-input v-model="query.keyword" placeholder="任务名称" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="待办" :value="0" />
          <el-option label="进行中" :value="1" />
          <el-option label="待审核" :value="2" />
          <el-option label="已完成" :value="3" />
          <el-option label="已关闭" :value="4" />
        </el-select>
        <el-select v-model="query.priority" placeholder="优先级" clearable style="width: 120px">
          <el-option label="高" :value="1" />
          <el-option label="中" :value="2" />
          <el-option label="低" :value="3" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="success" :icon="Plus" @click="openCreate">创建任务</el-button>
        <el-button type="warning" @click="openPoolDialog">从任务池领取</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe @row-click="(row: any) => goDetail(row.id)">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="taskName" label="任务名称" show-overflow-tooltip min-width="200" />
        <el-table-column prop="taskType" label="类型" width="100" />
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }: any">
            <el-tag :type="priorityTag(row.priority)" size="small">{{ priorityText(row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }: any">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progress" label="进度" width="120">
          <template #default="{ row }: any">
            <el-progress :percentage="row.progress || 0" :stroke-width="6" />
          </template>
        </el-table-column>
        <el-table-column prop="planHours" label="计划工时" width="100" align="center" />
        <el-table-column prop="actualHours" label="实际工时" width="100" align="center" />
        <el-table-column prop="deadline" label="截止日期" width="120" />
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" @click.stop="goDetail(row.id)">详情</el-button>
            <el-button link type="success" @click.stop="openEdit(row as TaskItem)">编辑</el-button>
            <el-button v-if="row.status === 0" link type="primary" @click.stop="changeStatus(row as TaskItem, 1, '开始执行')">开始</el-button>
            <el-button v-if="row.status === 1" link type="warning" @click.stop="changeStatus(row as TaskItem, 2, '提交审核')">提交</el-button>
            <el-button v-if="row.status === 2" link type="success" @click.stop="changeStatus(row as TaskItem, 3, '审核通过')">通过</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑任务' : '创建任务'" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入任务名称" maxlength="100" />
        </el-form-item>
        <el-form-item label="所属项目" prop="projectId">
          <el-select v-model="form.projectId" placeholder="请选择项目" filterable style="width: 100%">
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" placeholder="请选择" style="width: 100%">
            <el-option label="技术方案" value="技术方案" />
            <el-option label="图纸绘制" value="图纸绘制" />
            <el-option label="BOM编制" value="BOM编制" />
            <el-option label="现场勘测" value="现场勘测" />
            <el-option label="售后支持" value="售后支持" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.assigneeId" placeholder="请选择" filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio :value="1">高</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="计划工时">
          <el-input-number v-model="form.planHours" :min="0.5" :max="999" :precision="1" />
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 从任务池领取 -->
    <el-dialog v-model="poolDialog" title="从任务池领取任务" width="520px">
      <el-form label-width="90px">
        <el-form-item label="池任务" required>
          <el-select v-model="poolForm.poolId" v-loading="poolLoading" filterable style="width: 100%"
                     placeholder="选择标准任务">
            <el-option v-for="p in poolList" :key="p.id"
                       :label="`${p.taskName}（${p.taskType || '通用'} · ${p.planHours ?? 0}h）`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标项目" required>
          <el-select v-model="poolForm.projectId" filterable style="width: 100%" placeholder="选择项目">
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="poolForm.assigneeId" filterable style="width: 100%" placeholder="默认自己">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="poolDialog = false">取消</el-button>
        <el-button type="primary" :loading="claiming" @click="claimTask">领取</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getTaskListApi, createTaskApi, updateTaskApi, changeTaskStatusApi, listTaskPoolApi, claimPoolTaskApi, type TaskItem, type TaskDTO } from '@/api/task'
import { getProjectListApi, type ProjectItem } from '@/api/project'
import { getUserOptionsApi, type SysUser } from '@/api/user'

const router = useRouter()
const loading = ref(false)
const tableData = ref<TaskItem[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: undefined as number | undefined, priority: undefined as number | undefined })

const dialogVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const projectOptions = ref<ProjectItem[]>([])
const userOptions = ref<SysUser[]>([])

const form = reactive({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  taskName: '',
  taskType: '',
  assigneeId: undefined as number | undefined,
  priority: 2,
  planHours: 8,
  startDate: '',
  deadline: '',
  description: ''
})

const rules: FormRules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }]
}

const priorityText = (p?: number) => p === 1 ? '高' : p === 3 ? '低' : '中'
const priorityTag = (p?: number) => p === 1 ? 'danger' as const : p === 3 ? 'info' as const : 'warning' as const
const statusText = (s?: number) => ['待办', '进行中', '待审核', '已完成', '已关闭'][s ?? 0] || '未知'
const statusTag = (s?: number) => (['info', 'primary', 'warning', 'success', 'danger'] as const)[s ?? 0] || 'info'

async function loadData() {
  loading.value = true
  try {
    const res = await getTaskListApi(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.status = undefined
  query.priority = undefined
  query.pageNum = 1
  loadData()
}

const goDetail = (id: number) => router.push(`/task/detail/${id}`)

function openCreate() {
  editing.value = false
  Object.assign(form, { id: undefined, projectId: undefined, taskName: '', taskType: '', assigneeId: undefined, priority: 2, planHours: 8, startDate: '', deadline: '', description: '' })
  dialogVisible.value = true
}

function openEdit(row: TaskItem) {
  editing.value = true
  Object.assign(form, {
    id: row.id, projectId: row.projectId, taskName: row.taskName, taskType: row.taskType || '',
    assigneeId: row.assigneeId, priority: row.priority, planHours: row.planHours,
    startDate: row.startDate, deadline: row.deadline, description: row.description || ''
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const data = { ...form } as TaskDTO
      if (editing.value && form.id) {
        await updateTaskApi(form.id, data)
        ElMessage.success('任务更新成功')
      } else {
        await createTaskApi(data)
        ElMessage.success('任务创建成功')
      }
      dialogVisible.value = false
      loadData()
    } finally {
      submitting.value = false
    }
  })
}

async function changeStatus(row: TaskItem, target: number, action: string) {
  try {
    await changeTaskStatusApi(row.id, target, action)
    ElMessage.success('状态流转成功')
    loadData()
  } catch (e) {
    // 忽略
  }
}

// ---------- 任务池领取 ----------
const poolDialog = ref(false)
const poolList = ref<any[]>([])
const poolLoading = ref(false)
const poolForm = reactive({ poolId: undefined as number | undefined, projectId: undefined as number | undefined, assigneeId: undefined as number | undefined })
const claiming = ref(false)

async function openPoolDialog() {
  poolDialog.value = true
  poolLoading.value = true
  try {
    const res = await listTaskPoolApi({ status: 1 })
    poolList.value = res?.data ?? []
  } catch (e) {
  } finally {
    poolLoading.value = false
  }
}

async function claimTask() {
  if (!poolForm.poolId || !poolForm.projectId) {
    ElMessage.warning('请选择池任务与目标项目')
    return
  }
  claiming.value = true
  try {
    const me = userOptions.value.find(u => u.id) // 默认领取人：当前用户（后端从 token 已知身份）
    await claimPoolTaskApi(poolForm.poolId, poolForm.projectId, me ? me.id : 1)
    ElMessage.success('领取成功，任务已生成')
    poolDialog.value = false
    loadData()
  } catch (e) {
  } finally {
    claiming.value = false
  }
}

onMounted(async () => {
  loadData()
  const [projRes, userRes] = await Promise.all([getProjectListApi({ pageSize: 100 }), getUserOptionsApi()])
  projectOptions.value = projRes.data.records
  userOptions.value = userRes.data
})
</script>

<style lang="scss" scoped>
.search-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}
</style>
