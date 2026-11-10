<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">文件分类管理</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <div style="margin-bottom: 12px; display: flex; justify-content: space-between;">
        <span>分类树</span>
        <el-button type="primary" size="small" @click="openCreate()">新增分类</el-button>
      </div>

      <el-table v-loading="loading" :data="treeData" row-key="id" default-expand-all border>
        <el-table-column prop="categoryName" label="分类名称" min-width="200" />
        <el-table-column prop="categoryCode" label="编码" width="150" />
        <el-table-column prop="orderNum" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }: any">
            <el-button link type="primary" size="small" @click="openCreate(row)">新增子分类</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分类' : '新增分类'" width="440px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="父分类">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'categoryName', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            placeholder="默认根节点"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input v-model="form.categoryName" maxlength="100" />
        </el-form-item>
        <el-form-item label="分类编码">
          <el-input v-model="form.categoryCode" maxlength="50" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.orderNum" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
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
  listFileCategoriesApi, createFileCategoryApi, updateFileCategoryApi, deleteFileCategoryApi,
  type FileCategory
} from '@/api/file'

const loading = ref(false)
const saving = ref(false)
const categories = ref<FileCategory[]>([])
const treeData = ref<any[]>([])
const parentOptions = ref<any[]>([])

const dialogVisible = ref(false)
const editing = ref<FileCategory | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  parentId: undefined as number | undefined,
  categoryName: '',
  categoryCode: '',
  orderNum: 0,
  status: 1
})
const rules: FormRules = {
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

function buildTree(list: FileCategory[], excludeId?: number): any[] {
  const map: Record<number, any> = {}
  const roots: any[] = []
  list.filter(c => c.id !== excludeId).forEach(c => {
    map[c.id] = { ...c, children: [] }
  })
  for (const id of Object.keys(map)) {
    const item = map[Number(id)]
    if (item.parentId && map[item.parentId] && Number(item.parentId) !== Number(item.id)) {
      map[item.parentId].children.push(item)
    } else {
      roots.push(item)
    }
  }
  // 排序
  const sortRec = (arr: any[]) => {
    arr.sort((a, b) => (a.orderNum ?? 0) - (b.orderNum ?? 0))
    arr.forEach(item => sortRec(item.children))
  }
  sortRec(roots)
  return roots
}

async function load() {
  loading.value = true
  try {
    const res = await listFileCategoriesApi()
    categories.value = res.data || []
    treeData.value = buildTree(categories.value)
    parentOptions.value = buildTree(categories.value, editing.value?.id)
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: FileCategory) {
  editing.value = null
  Object.assign(form, {
    parentId: parent?.id ?? undefined,
    categoryName: '',
    categoryCode: '',
    orderNum: 0,
    status: 1
  })
  dialogVisible.value = true
}

function openEdit(row: FileCategory) {
  editing.value = row
  Object.assign(form, {
    parentId: row.parentId === 0 ? undefined : row.parentId,
    categoryName: row.categoryName,
    categoryCode: row.categoryCode || '',
    orderNum: row.orderNum ?? 0,
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function save() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = {
        ...form,
        parentId: form.parentId ?? 0
      }
      if (editing.value) {
        await updateFileCategoryApi(editing.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await createFileCategoryApi(payload)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      await load()
    } catch (e) {
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row: FileCategory) {
  try {
    await ElMessageBox.confirm(`确认删除分类【${row.categoryName}】？`, '提示', { type: 'warning' })
    await deleteFileCategoryApi(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch (e) {}
}

onMounted(load)
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
</style>
