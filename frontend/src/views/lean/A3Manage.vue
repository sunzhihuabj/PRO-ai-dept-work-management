<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">A3 报告</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <div class="search-bar">
        <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 220px" @change="loadData">
          <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="loadData">
          <el-option v-for="(l, i) in A3_STATUS" :key="i" :label="l" :value="i" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="success" @click="openDialog()">新建 A3</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="goal" label="目标" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag :type="(['info','primary','success','info'] as const)[row.status] || 'info'" size="small">{{ A3_STATUS[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="row.status < 3" link type="warning" size="small" @click="advance(row)">
              {{ ['开始', '完成', '关闭'][row.status] }}
            </el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
        :total="total" :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData" @current-change="loadData"
        style="margin-top: 16px; justify-content: flex-end; display: flex;"
      />
    </el-card>

    <el-dialog v-model="dialog" :title="editing ? '编辑A3报告' : '新建A3报告'" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="所属项目">
          <el-select v-model="form.projectId" clearable filterable style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="背景描述">
          <el-input v-model="form.background" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="现状描述">
          <el-input v-model="form.currentCondition" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="目标设定">
          <el-input v-model="form.goal" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="根因分析">
          <el-input v-model="form.rootCause" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="对策措施">
          <el-input v-model="form.countermeasures" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="效果验证">
          <el-input v-model="form.checkResult" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="跟进标准化">
          <el-input v-model="form.followUp" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  listA3Api, createA3Api, updateA3Api, advanceA3Api, deleteA3Api,
  A3_STATUS, type A3Report
} from '@/api/lean'
import { getProjectListApi, type ProjectItem } from '@/api/project'

const loading = ref(false)
const saving = ref(false)
const list = ref<A3Report[]>([])
const total = ref(0)
const projects = ref<ProjectItem[]>([])
const query = reactive({ pageNum: 1, pageSize: 10, projectId: undefined as number | undefined, status: undefined as number | undefined })
const dialog = ref(false)
const editing = ref<A3Report | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<Partial<A3Report>>({
  title: '', projectId: undefined, background: '', currentCondition: '', goal: '',
  rootCause: '', countermeasures: '', checkResult: '', followUp: ''
})
const rules: FormRules = { title: [{ required: true, message: '请输入标题', trigger: 'blur' }] }

async function loadData() {
  loading.value = true
  try {
    const res = await listA3Api(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openDialog(row?: A3Report) {
  editing.value = row ?? null
  Object.assign(form, row ? {
    title: row.title, projectId: row.projectId, background: row.background,
    currentCondition: row.currentCondition, goal: row.goal, rootCause: row.rootCause,
    countermeasures: row.countermeasures, checkResult: row.checkResult, followUp: row.followUp
  } : {
    title: '', projectId: undefined, background: '', currentCondition: '', goal: '',
    rootCause: '', countermeasures: '', checkResult: '', followUp: ''
  })
  dialog.value = true
}

async function save() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (editing.value) {
        await updateA3Api(editing.value.id, { ...form })
        ElMessage.success('更新成功')
      } else {
        await createA3Api({ ...form })
        ElMessage.success('创建成功')
      }
      dialog.value = false
      loadData()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function advance(row: A3Report) {
  try {
    await advanceA3Api(row.id, (row.status ?? 0) + 1)
    ElMessage.success('推进成功')
    loadData()
  } catch (e) {}
}

async function remove(row: A3Report) {
  try {
    await ElMessageBox.confirm(`确认删除【${row.title}】？`, '提示', { type: 'warning' })
    await deleteA3Api(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

onMounted(async () => {
  await loadData()
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
