<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">部门管理</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <div style="margin-bottom: 12px; display: flex; justify-content: space-between;">
        <span>部门树</span>
        <el-button type="primary" size="small" @click="openCreate()">新增部门</el-button>
      </div>

      <el-table v-loading="loading" :data="treeData" row-key="id" default-expand-all border>
        <el-table-column prop="deptName" label="部门名称" min-width="200" />
        <el-table-column prop="leader" label="负责人" width="110" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column prop="email" label="邮箱" width="180" />
        <el-table-column prop="orderNum" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center">
          <template #default="{ row }: any">
            <el-button link type="primary" size="small" @click="openCreate(row)">新增子部门</el-button>
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑部门' : '新增部门'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="父部门">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'deptName', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            placeholder="默认根节点"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="部门名称" prop="deptName">
          <el-input v-model="form.deptName" maxlength="100" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.leader" maxlength="50" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" maxlength="100" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.orderNum" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
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
import { listDeptsApi, createDeptApi, updateDeptApi, deleteDeptApi, type SysDept } from '@/api/dept'

const loading = ref(false)
const saving = ref(false)
const treeData = ref<any[]>([])
const parentOptions = ref<any[]>([])

const dialogVisible = ref(false)
const editing = ref<SysDept | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  parentId: undefined as number | undefined,
  deptName: '',
  leader: '',
  phone: '',
  email: '',
  orderNum: 0,
  status: 1
})
const rules: FormRules = {
  deptName: [{ required: true, message: '请输入部门名称', trigger: 'blur' }]
}

function buildTree(list: SysDept[], excludeId?: number): any[] {
  const map: Record<number, any> = {}
  const roots: any[] = []
  list.filter(c => c.id !== excludeId).forEach(c => { map[c.id] = { ...c, children: [] } })
  for (const id of Object.keys(map)) {
    const item = map[Number(id)]
    if (item.parentId && map[item.parentId] && Number(item.parentId) !== Number(item.id)) {
      map[item.parentId].children.push(item)
    } else {
      roots.push(item)
    }
  }
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
    const res = await listDeptsApi()
    treeData.value = buildTree(res.data || [])
    parentOptions.value = buildTree(res.data || [], editing.value?.id)
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: SysDept) {
  editing.value = null
  Object.assign(form, { parentId: parent?.id ?? undefined, deptName: '', leader: '', phone: '', email: '', orderNum: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(row: SysDept) {
  editing.value = row
  Object.assign(form, {
    parentId: row.parentId === 0 ? undefined : row.parentId,
    deptName: row.deptName, leader: row.leader || '', phone: row.phone || '',
    email: row.email || '', orderNum: row.orderNum ?? 0, status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function save() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form, parentId: form.parentId ?? 0 }
      if (editing.value) {
        await updateDeptApi(editing.value.id, payload)
        ElMessage.success('更新成功')
      } else {
        await createDeptApi(payload)
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

async function handleDelete(row: SysDept) {
  try {
    await ElMessageBox.confirm(`确认删除部门【${row.deptName}】？`, '提示', { type: 'warning' })
    await deleteDeptApi(row.id)
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
