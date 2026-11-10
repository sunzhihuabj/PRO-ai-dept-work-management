<template>
  <div class="page-container">
    <el-card shadow="hover">
      <div class="search-bar">
        <el-input v-model="query.username" placeholder="用户名" clearable style="width: 180px" @keyup.enter="loadData" />
        <el-input v-model="query.realName" placeholder="真实姓名" clearable style="width: 180px" @keyup.enter="loadData" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button type="success" :icon="Plus" @click="openCreate">新增用户</el-button>
      </div>

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="真实姓名" width="100" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="角色" width="180">
          <template #default="{ row }: any">
            <el-tag v-for="role in parseRoles(row.roleCodes)" :key="role" size="small" style="margin-right: 4px;">{{ roleName(role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }: any">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" @click="openEdit(row as SysUser)">编辑</el-button>
            <el-button link type="warning" @click="handleResetPwd(row as SysUser)">重置密码</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row as SysUser)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            <el-button link type="danger" @click="handleDelete(row as SysUser)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑用户' : '新增用户'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username" v-if="!editing">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入真实姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCodes">
          <el-select v-model="form.roleCodes" placeholder="请选择角色" style="width: 100%">
            <el-option label="部门领导" value="ROLE_DEPT_LEADER" />
            <el-option label="项目负责人" value="ROLE_PROJECT_LEADER" />
            <el-option label="技改负责人" value="ROLE_REFORM_LEADER" />
            <el-option label="技术员" value="ROLE_TECHNICIAN" />
            <el-option label="研发人员" value="ROLE_DEVELOPER" />
            <el-option label="档案管理员" value="ROLE_ARCHIVIST" />
            <el-option label="审核员" value="ROLE_AUDITOR" />
          </el-select>
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
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getUserListApi, createUserApi, updateUserApi, deleteUserApi, resetPasswordApi, toggleUserStatusApi, type SysUser } from '@/api/user'

const loading = ref(false)
const tableData = ref<SysUser[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, username: '', realName: '', status: undefined as number | undefined })

const dialogVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  username: '', realName: '', phone: '', email: '',
  roleCodes: '', status: 1
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  roleCodes: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const ROLE_NAMES: Record<string, string> = {
  ROLE_DEPT_LEADER: '部门领导', ROLE_PROJECT_LEADER: '项目负责人',
  ROLE_REFORM_LEADER: '技改负责人', ROLE_TECHNICIAN: '技术员',
  ROLE_DEVELOPER: '研发人员', ROLE_ARCHIVIST: '档案管理员', ROLE_AUDITOR: '审核员'
}

const parseRoles = (codes?: string): string[] => {
  if (!codes) return []
  try { return JSON.parse(codes) } catch {
    return codes.replace(/[[\]"]/g, '').split(',').map(s => s.trim()).filter(Boolean)
  }
}

const roleName = (code: string) => ROLE_NAMES[code] || code

async function loadData() {
  loading.value = true
  try {
    const res = await getUserListApi(query)
    tableData.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.username = ''; query.realName = ''; query.status = undefined; query.pageNum = 1
  loadData()
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: undefined, username: '', realName: '', phone: '', email: '', roleCodes: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row: SysUser) {
  editing.value = true
  const roles = parseRoles(row.roleCodes)
  Object.assign(form, {
    id: row.id, username: row.username, realName: row.realName || '',
    phone: row.phone || '', email: row.email || '',
    roleCodes: roles[0] || '', status: row.status
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
        await updateUserApi(form.id, data)
        ElMessage.success('用户更新成功')
      } else {
        await createUserApi(data)
        ElMessage.success('用户创建成功')
      }
      dialogVisible.value = false
      loadData()
    } finally {
      submitting.value = false
    }
  })
}

/** 生成12位随机密码（去掉易混淆字符，含字母和数字） */
function genRandomPwd(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'
  const arr = new Uint32Array(12)
  crypto.getRandomValues(arr)
  let pwd = ''
  for (const n of arr) pwd += chars[n % chars.length]
  return pwd
}

async function handleResetPwd(row: SysUser) {
  try {
    const { value } = await ElMessageBox.prompt(
      `为用户"${row.realName}"设置新密码（至少8位，需包含字母和数字），重置后请线下告知该用户`,
      '重置密码',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputValue: genRandomPwd(),
        inputPattern: /^(?=.*[A-Za-z])(?=.*\d).{8,}$/,
        inputErrorMessage: '密码至少8位，且需同时包含字母和数字',
        type: 'warning'
      }
    )
    await resetPasswordApi(row.id, value)
    ElMessage.success('密码已重置成功，请将新密码线下告知用户')
  } catch (e) {}
}

async function toggleStatus(row: SysUser) {
  const newStatus = row.status === 1 ? 0 : 1
  await toggleUserStatusApi(row.id, newStatus)
  ElMessage.success('状态切换成功')
  loadData()
}

async function handleDelete(row: SysUser) {
  try {
    await ElMessageBox.confirm(`确定要删除用户"${row.realName}"吗？`, '提示', { type: 'warning' })
    await deleteUserApi(row.id)
    ElMessage.success('用户删除成功')
    loadData()
  } catch (e) {}
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.search-bar { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin-bottom: 16px; }
</style>
