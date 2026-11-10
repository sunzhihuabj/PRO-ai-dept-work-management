<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">通知公告</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <div class="search-bar">
        <el-select v-model="query.noticeType" placeholder="类型" clearable style="width: 110px" @change="loadData">
          <el-option label="通知" :value="1" />
          <el-option label="公告" :value="2" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px" @change="loadData">
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
        </el-select>
        <el-button type="primary" @click="loadData">查询</el-button>
        <el-button type="success" @click="openDialog()">新建公告</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="noticeTitle" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.noticeType === 2 ? 'warning' : 'primary'" size="small">{{ row.noticeType === 2 ? '公告' : '通知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '已发布' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishBy" label="发布人" width="100" />
        <el-table-column prop="publishTime" label="发布时间" width="170" />
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button v-if="row.status === 0" link type="warning" size="small" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 0" link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog" :title="editing ? '编辑公告' : '新建公告'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="noticeTitle">
          <el-input v-model="form.noticeTitle" maxlength="200" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.noticeType">
            <el-radio :value="1">通知</el-radio>
            <el-radio :value="2">公告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.noticeContent" type="textarea" :rows="5" maxlength="2000" show-word-limit />
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
  listNoticesApi, createNoticeApi, updateNoticeApi, publishNoticeApi, deleteNoticeApi,
  type SysNotice
} from '@/api/notice'

const loading = ref(false)
const saving = ref(false)
const list = ref<SysNotice[]>([])
const query = reactive({ noticeType: undefined as number | undefined, status: undefined as number | undefined })
const dialog = ref(false)
const editing = ref<SysNotice | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<Partial<SysNotice>>({ noticeTitle: '', noticeType: 1, noticeContent: '' })
const rules: FormRules = { noticeTitle: [{ required: true, message: '请输入标题', trigger: 'blur' }] }

async function loadData() {
  loading.value = true
  try {
    const res = await listNoticesApi(query)
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDialog(row?: SysNotice) {
  editing.value = row ?? null
  Object.assign(form, row ? {
    noticeTitle: row.noticeTitle, noticeType: row.noticeType ?? 1, noticeContent: row.noticeContent
  } : { noticeTitle: '', noticeType: 1, noticeContent: '' })
  dialog.value = true
}

async function save() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (editing.value) {
        await updateNoticeApi(editing.value.id, { ...form })
        ElMessage.success('更新成功')
      } else {
        await createNoticeApi({ ...form })
        ElMessage.success('创建成功（保存为草稿，发布后全员可见）')
      }
      dialog.value = false
      loadData()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function publish(row: SysNotice) {
  try {
    await ElMessageBox.confirm(`发布后全员可见并收到站内消息，确认发布【${row.noticeTitle}】？`, '发布确认', { type: 'warning' })
    const res = await publishNoticeApi(row.id)
    ElMessage.success(`已发布，触达 ${res.data.reached} 人`)
    loadData()
  } catch (e) {}
}

async function remove(row: SysNotice) {
  try {
    await ElMessageBox.confirm(`确认删除【${row.noticeTitle}】？`, '提示', { type: 'warning' })
    await deleteNoticeApi(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {}
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
.search-bar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
</style>
