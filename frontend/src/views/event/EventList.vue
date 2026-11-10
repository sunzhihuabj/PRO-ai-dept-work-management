<template>
  <div class="page-container">
    <el-card shadow="hover">
      <div class="search-bar">
        <el-input v-model="query.keyword" placeholder="事件标题/描述" clearable style="width: 200px" @keyup.enter="loadData" />
        <el-select v-model="query.eventType" placeholder="类型" clearable style="width: 110px">
          <el-option label="售后" :value="1" />
          <el-option label="变更" :value="2" />
          <el-option label="异常" :value="3" />
          <el-option label="其他" :value="99" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px">
          <el-option v-for="(label, i) in EVENT_STATUS" :key="i" :label="label" :value="i" />
        </el-select>
        <el-select v-model="query.urgency" placeholder="紧急度" clearable style="width: 100px">
          <el-option label="紧急" :value="1" />
          <el-option label="一般" :value="2" />
          <el-option label="低" :value="3" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="success" :icon="Plus" @click="openCreate">新建事件</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe @row-click="(row: any) => goDetail(row.id)">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="eventTitle" label="事件标题" show-overflow-tooltip min-width="200" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }: any">{{ typeText(row.eventType) }}</template>
        </el-table-column>
        <el-table-column label="紧急度" width="80" align="center">
          <template #default="{ row }: any">
            <el-tag :type="urgencyTag(row.urgency)" size="small">{{ urgencyText(row.urgency) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" @click.stop="goDetail(row.id)">处理</el-button>
            <el-button v-if="row.status === 0 || row.status === 4" link type="success" @click.stop="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0 || row.status === 4" link type="danger" @click.stop="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑事件' : '新建事件'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="事件标题" prop="eventTitle">
          <el-input v-model="form.eventTitle" placeholder="请输入事件标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="所属项目" prop="projectId">
          <el-select v-model="form.projectId" placeholder="请选择项目" filterable style="width: 100%">
            <el-option v-for="p in projectOptions" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="事件类型" prop="eventType">
          <el-select v-model="form.eventType" placeholder="请选择类型" style="width: 100%">
            <el-option label="售后" :value="1" />
            <el-option label="变更" :value="2" />
            <el-option label="异常" :value="3" />
            <el-option label="其他" :value="99" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-radio-group v-model="form.urgency">
            <el-radio :value="1">紧急</el-radio>
            <el-radio :value="2">一般</el-radio>
            <el-radio :value="3">低</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理人">
          <el-select v-model="form.handlerId" placeholder="请选择处理人" filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="事件描述">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" show-word-limit />
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
import {
  getEventListApi, createEventApi, updateEventApi, deleteEventApi,
  EVENT_STATUS, EVENT_TYPE, EVENT_URGENCY,
  type EventItem, type EventCreateDTO
} from '@/api/event'
import { getProjectListApi, type ProjectItem } from '@/api/project'
import { getUserOptionsApi, type SysUser } from '@/api/user'

const router = useRouter()
const loading = ref(false)
const tableData = ref<EventItem[]>([])
const total = ref(0)
const query = reactive({
  pageNum: 1, pageSize: 10, keyword: '',
  eventType: undefined as number | undefined,
  status: undefined as number | undefined,
  urgency: undefined as number | undefined
})

const dialogVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const projectOptions = ref<ProjectItem[]>([])
const userOptions = ref<SysUser[]>([])
const editingId = ref<number>()

const form = reactive<EventCreateDTO>({
  projectId: undefined as unknown as number,
  eventType: undefined as unknown as number,
  eventTitle: '',
  urgency: 2,
  handlerId: undefined,
  description: ''
})

const rules: FormRules = {
  eventTitle: [{ required: true, message: '请输入事件标题', trigger: 'blur' }],
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  eventType: [{ required: true, message: '请选择事件类型', trigger: 'change' }]
}

const statusText = (s?: number) => EVENT_STATUS[s ?? 0] ?? '未知'
const statusTag = (s?: number) => (['warning', 'primary', 'success', 'info', 'danger'] as const)[s ?? 0] || 'info'
const typeText = (t?: number) => EVENT_TYPE[t ?? 99] ?? '其他'
const urgencyText = (u?: number) => EVENT_URGENCY[u ?? 2] ?? '一般'
const urgencyTag = (u?: number) => u === 1 ? 'danger' as const : u === 3 ? 'info' as const : 'warning' as const

async function loadData() {
  loading.value = true
  try {
    const res = await getEventListApi(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.eventType = undefined
  query.status = undefined
  query.urgency = undefined
  query.pageNum = 1
  loadData()
}

const goDetail = (id: number) => router.push(`/event/detail/${id}`)

function openCreate() {
  editing.value = false
  editingId.value = undefined
  Object.assign(form, { projectId: undefined, eventType: undefined, eventTitle: '', urgency: 2, handlerId: undefined, description: '' })
  dialogVisible.value = true
}

function openEdit(row: EventItem) {
  editing.value = true
  editingId.value = row.id
  Object.assign(form, {
    projectId: row.projectId, eventType: row.eventType, eventTitle: row.eventTitle,
    urgency: row.urgency, handlerId: row.handlerId, description: row.description || ''
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (editing.value && editingId.value) {
        await updateEventApi(editingId.value, { ...form })
        ElMessage.success('事件更新成功')
      } else {
        await createEventApi({ ...form })
        ElMessage.success('事件创建成功')
      }
      dialogVisible.value = false
      loadData()
    } catch (e) {
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row: EventItem) {
  try {
    await ElMessageBox.confirm(`确认删除事件【${row.eventTitle}】？`, '提示', { type: 'warning' })
    await deleteEventApi(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    // 取消或失败
  }
}

async function loadOptions() {
  try {
    const [pRes, uRes] = await Promise.all([
      getProjectListApi({ pageNum: 1, pageSize: 200 }),
      getUserOptionsApi()
    ])
    projectOptions.value = pRes.data.records
    userOptions.value = uRes.data
  } catch (e) {}
}

onMounted(() => {
  loadData()
  loadOptions()
})
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.search-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}
</style>
