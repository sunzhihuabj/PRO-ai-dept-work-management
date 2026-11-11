<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>档案管理（DA 档号 · 电子实物双轨 · 借阅逾期 · 鉴定销毁）</span>
          <el-button type="primary" @click="registerVisible = true">档案登记</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="档案库" name="archives">
          <el-form inline>
            <el-form-item label="状态">
              <el-select v-model="queryStatus" clearable placeholder="全部" style="width: 130px">
                <el-option v-for="(label, key) in ARCHIVE_STATUS_LABEL" :key="key" :label="label" :value="key" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadArchives">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="archives" v-loading="loading" border>
            <el-table-column prop="archiveNo" label="档号" width="150" />
            <el-table-column prop="title" label="档案标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="projectId" label="项目" width="80" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ ARCHIVE_TYPE_LABEL[row.archiveType] || row.archiveType }}</template>
            </el-table-column>
            <el-table-column label="载体" width="90">
              <template #default="{ row }">{{ MEDIUM_LABEL[row.medium] || row.medium }}</template>
            </el-table-column>
            <el-table-column prop="location" label="存放位置" width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="ARCHIVE_STATUS_TYPE[row.status]" size="small">{{ ARCHIVE_STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'NORMAL' && row.medium !== 'ELECTRONIC'" size="small" type="primary" @click="openBorrow(row)">借阅</el-button>
                <el-button size="small" @click="showBorrows(row)">借阅记录</el-button>
                <el-button v-if="row.status !== 'DESTROYED'" size="small" type="danger" @click="openDestroy(row)">销毁</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total"
                         layout="total, prev, pager, next" style="margin-top: 12px" @current-change="loadArchives" />
        </el-tab-pane>

        <el-tab-pane label="逾期借阅" name="overdue">
          <el-button type="primary" plain @click="loadOverdue" style="margin-bottom: 12px">刷新</el-button>
          <el-alert type="warning" :closable="false" style="margin-bottom: 12px"
                    title="应还日期已过且未归还的借阅记录自动标记 OVERDUE（归还动作即时刷新）" />
          <el-table :data="overdue" v-loading="overdueLoading" border>
            <el-table-column prop="id" label="借阅ID" width="90" />
            <el-table-column prop="archiveId" label="档案" width="90" />
            <el-table-column prop="borrower" label="借阅人" width="110" />
            <el-table-column prop="borrowDate" label="借出日期" width="110" />
            <el-table-column prop="dueDate" label="应还日期" width="110" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag type="danger" size="small">{{ BORROW_STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status !== 'RETURNED'" size="small" type="success" @click="doReturn(row)">归还</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 档案登记 -->
    <el-dialog v-model="registerVisible" title="档案登记（DA 档号自动生成；纸质/双轨必填存放位置）" width="560px">
      <el-form label-width="100px">
        <el-form-item label="项目ID" required>
          <el-input-number v-model="registerForm.projectId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="档案标题" required>
          <el-input v-model="registerForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="档案类型">
          <el-select v-model="registerForm.archiveType" style="width: 200px">
            <el-option label="合同" value="CONTRACT" />
            <el-option label="方案" value="SCHEME" />
            <el-option label="图纸" value="DRAWING" />
            <el-option label="记录" value="RECORD" />
            <el-option label="验收" value="ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="载体">
          <el-radio-group v-model="registerForm.medium">
            <el-radio value="ELECTRONIC">电子</el-radio>
            <el-radio value="PAPER">纸质</el-radio>
            <el-radio value="DUAL">双轨</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="存放位置" v-if="registerForm.medium !== 'ELECTRONIC'" required>
          <el-input v-model="registerForm.location" placeholder="纸质/双轨载体必填，如：档案室 A 区 03 柜" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="registerForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doRegister">登记</el-button>
      </template>
    </el-dialog>

    <!-- 借阅 -->
    <el-dialog v-model="borrowVisible" title="档案借阅（电子载体不可实物借出）" width="480px">
      <el-form label-width="90px">
        <el-form-item label="借阅人" required>
          <el-input v-model="borrowForm.borrower" style="width: 200px" />
        </el-form-item>
        <el-form-item label="借阅用途">
          <el-input v-model="borrowForm.purpose" type="textarea" :rows="2" maxlength="300" />
        </el-form-item>
        <el-form-item label="借期(天)">
          <el-input-number v-model="borrowForm.dueDays" :min="1" :max="90" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="批准人">
          <el-input v-model="borrowForm.approveBy" style="width: 200px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="borrowVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doBorrow">借出</el-button>
      </template>
    </el-dialog>

    <!-- 借阅记录 -->
    <el-dialog v-model="borrowsVisible" title="借阅记录" width="640px">
      <el-table :data="borrows" border>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="borrower" label="借阅人" width="100" />
        <el-table-column prop="borrowDate" label="借出" width="100" />
        <el-table-column prop="dueDate" label="应还" width="100" />
        <el-table-column prop="returnDate" label="归还" width="100" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RETURNED' ? 'success' : row.status === 'OVERDUE' ? 'danger' : 'warning'" size="small">
              {{ BORROW_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'RETURNED'" size="small" type="success" @click="doReturn(row); borrowsVisible = false">归还</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 销毁 -->
    <el-dialog v-model="destroyVisible" title="鉴定销毁（鉴定意见必填，不可恢复）" width="480px">
      <el-form label-width="90px">
        <el-form-item label="鉴定意见" required>
          <el-input v-model="destroyRemark" type="textarea" :rows="3" maxlength="500"
                    placeholder="如：已过保存期限，经鉴定无留存价值" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="destroyVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="doDestroy">确认销毁</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ARCHIVE_STATUS_LABEL, ARCHIVE_STATUS_TYPE, BORROW_STATUS_LABEL, MEDIUM_LABEL,
  registerArchiveApi, listArchivesApi, borrowArchiveApi, returnArchiveApi,
  listBorrowsApi, overdueBorrowsApi, destroyArchiveApi
} from '@/api/phase3'
import type { ArchiveInfo, ArchiveBorrow } from '@/api/phase3'

const ARCHIVE_TYPE_LABEL: Record<string, string> = { CONTRACT: '合同', SCHEME: '方案', DRAWING: '图纸', RECORD: '记录', ACCEPTANCE: '验收' }

const activeTab = ref('archives')
const loading = ref(false)
const overdueLoading = ref(false)
const saving = ref(false)
const archives = ref<ArchiveInfo[]>([])
const overdue = ref<ArchiveBorrow[]>([])
const borrows = ref<ArchiveBorrow[]>([])
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const queryStatus = ref('')

const registerVisible = ref(false)
const borrowVisible = ref(false)
const borrowsVisible = ref(false)
const destroyVisible = ref(false)
let current: ArchiveInfo | null = null

const registerForm = reactive({ projectId: 1, title: '', archiveType: 'CONTRACT', medium: 'ELECTRONIC', location: '', remark: '' })
const borrowForm = reactive({ borrower: '', purpose: '', dueDays: 7, approveBy: '' })
const destroyRemark = ref('')

async function loadArchives() {
  loading.value = true
  try {
    const res = await listArchivesApi(pageNum.value, pageSize.value, undefined, queryStatus.value || undefined)
    archives.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadOverdue() {
  overdueLoading.value = true
  try {
    const res = await overdueBorrowsApi()
    overdue.value = res.data
  } finally {
    overdueLoading.value = false
  }
}

async function doRegister() {
  if (!registerForm.title) {
    ElMessage.warning('请填写档案标题')
    return
  }
  if (registerForm.medium !== 'ELECTRONIC' && !registerForm.location) {
    ElMessage.warning('纸质/双轨载体必须填写实物存放位置')
    return
  }
  saving.value = true
  try {
    await registerArchiveApi({ ...registerForm })
    ElMessage.success('档案已登记（DA 档号已生成）')
    registerVisible.value = false
    await loadArchives()
  } finally {
    saving.value = false
  }
}

function openBorrow(row: any) {
  current = row
  Object.assign(borrowForm, { borrower: '', purpose: '', dueDays: 7, approveBy: '' })
  borrowVisible.value = true
}

async function doBorrow() {
  if (!current || !borrowForm.borrower) {
    ElMessage.warning('请填写借阅人')
    return
  }
  saving.value = true
  try {
    await borrowArchiveApi(current.id, { ...borrowForm })
    ElMessage.success('借出已登记')
    borrowVisible.value = false
    await loadArchives()
  } finally {
    saving.value = false
  }
}

async function showBorrows(row: any) {
  current = row
  const res = await listBorrowsApi(row.id)
  borrows.value = res.data
  borrowsVisible.value = true
}

async function doReturn(row: any) {
  await returnArchiveApi(row.id)
  ElMessage.success('归还完成')
  await Promise.all([loadArchives(), loadOverdue()])
}

function openDestroy(row: any) {
  current = row
  destroyRemark.value = ''
  destroyVisible.value = true
}

async function doDestroy() {
  if (!current || !destroyRemark.value) {
    ElMessage.warning('鉴定意见为必填')
    return
  }
  saving.value = true
  try {
    await destroyArchiveApi(current.id, destroyRemark.value)
    ElMessage.success('档案已鉴定销毁')
    destroyVisible.value = false
    await loadArchives()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadArchives()
  loadOverdue()
})
</script>

<style scoped>
.page-container { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
