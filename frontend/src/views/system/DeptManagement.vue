<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">组织架构</span></template>
    </el-page-header>

    <el-card shadow="never">
      <div class="toolbar" style="justify-content: space-between;">
        <span class="card-title">架构树<el-badge :value="totalCount" type="primary" class="card-badge" /></span>
        <el-button type="primary" size="small" :icon="Plus" @click="openCreate()">新增组织</el-button>
      </div>

      <el-table v-loading="loading" :data="treeData" row-key="id" default-expand-all border stripe
                :empty-text="'暂无组织节点，点击右上角「新增组织」建立根节点'">
        <el-table-column prop="deptName" label="组织名称" min-width="220">
          <template #default="{ row }: any">
            <el-icon class="org-icon"><OfficeBuilding /></el-icon>
            <span class="org-name">{{ row.deptName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="leader" label="负责人" width="110">
          <template #default="{ row }: any">{{ row.leader || '—' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" width="130">
          <template #default="{ row }: any">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip>
          <template #default="{ row }: any">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column prop="orderNum" label="排序" width="80" align="center" />
        <el-table-column label="层级" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag size="small" effect="plain">{{ levelOf(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" size="small" :icon="Plus" @click="openCreate(row)">下级组织</el-button>
            <el-button link type="primary" size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑组织节点' : '新增组织节点'" width="480px">
      <el-alert type="info" :closable="false" style="margin-bottom: 14px"
                title="组织按树形层级维护：选择上级组织建立层级关系，删除节点前须先清空下级与挂靠用户" />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级组织">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ label: 'deptName', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            placeholder="不选则为根节点"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="组织名称" prop="deptName">
          <el-input v-model="form.deptName" maxlength="100" placeholder="如：塔筒制造一部" />
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
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Edit, Delete, OfficeBuilding } from '@element-plus/icons-vue'
import { listDeptsApi, createDeptApi, updateDeptApi, deleteDeptApi, type SysDept } from '@/api/dept'

const loading = ref(false)
const saving = ref(false)
const treeData = ref<any[]>([])
const flatList = ref<SysDept[]>([])
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
  deptName: [{ required: true, message: '请输入组织名称', trigger: 'blur' }]
}
const totalCount = computed(() => flatList.value.length)

/** 节点层级（根=1级，按 ancestors 链路或父级递归） */
function levelOf(row: any): string {
  const depth = (id: number, guard = 0): number => {
    if (guard > 20) return 1
    const node = flatList.value.find((d) => d.id === id)
    if (!node || !node.parentId || node.parentId === 0) return 1
    return 1 + depth(node.parentId, guard + 1)
  }
  return `${depth(row.id)} 级`
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
    flatList.value = res.data || []
    treeData.value = buildTree(flatList.value)
    parentOptions.value = buildTree(flatList.value, editing.value?.id)
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
        ElMessage.success('组织节点已更新')
      } else {
        await createDeptApi(payload)
        ElMessage.success('组织节点已创建')
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
    await ElMessageBox.confirm(
      `确认删除组织节点【${row.deptName}】？存在下级组织或挂靠用户时将被拒绝。`,
      '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
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
.toolbar { margin-bottom: 12px; }
.org-icon { vertical-align: -2px; margin-right: 6px; color: #409eff; }
.org-name { font-weight: 500; color: #303133; }
</style>
