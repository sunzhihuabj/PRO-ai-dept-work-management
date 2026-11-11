<template>
  <div class="page-container">
    <!-- 统计概览 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <div class="stat-card" :class="{ clickable: true }" @click="switchTab('archives', 'NORMAL')">
          <div class="stat-icon stat-icon--success"><el-icon><Collection /></el-icon></div>
          <div class="stat-meta">
            <div class="stat-value">{{ stats.normal }}</div>
            <div class="stat-label">在库档案</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card" @click="switchTab('archives', 'BORROWED')">
          <div class="stat-icon stat-icon--warning"><el-icon><Position /></el-icon></div>
          <div class="stat-meta">
            <div class="stat-value">{{ stats.borrowed }}</div>
            <div class="stat-label">借出中</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card" @click="switchTab('overdue')">
          <div class="stat-icon stat-icon--danger"><el-icon><AlarmClock /></el-icon></div>
          <div class="stat-meta">
            <div class="stat-value">{{ stats.overdue }}</div>
            <div class="stat-label">逾期借阅</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card" @click="switchTab('archives', 'DESTROYED')">
          <div class="stat-icon stat-icon--info"><el-icon><Delete /></el-icon></div>
          <div class="stat-meta">
            <div class="stat-value">{{ stats.destroyed }}</div>
            <div class="stat-label">已鉴定销毁</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>档案管理（DA 档号 · 电子实物双轨 · 借阅逾期 · 鉴定销毁）</span>
          <el-button type="primary" :icon="Plus" @click="openRegister">档案登记</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="档案库" name="archives">
          <div class="toolbar">
            <el-input v-model="queryKeyword" placeholder="搜索档号 / 标题 / 项目ID" clearable
                      :prefix-icon="Search" style="width: 260px" @input="pageNum = 1" />
            <el-select v-model="queryStatus" clearable placeholder="全部状态" style="width: 130px" @change="pageNum = 1; loadArchives()">
              <el-option v-for="(label, key) in ARCHIVE_STATUS_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
            <el-select v-model="queryType" clearable placeholder="全部类型" style="width: 120px" @change="pageNum = 1">
              <el-option v-for="(label, key) in ARCHIVE_TYPE_LABEL" :key="key" :label="label" :value="key" />
            </el-select>
            <el-button type="primary" :icon="Search" @click="loadArchives">查询</el-button>
          </div>
          <el-table :data="filteredArchives" v-loading="loading" border stripe
                    :empty-text="queryKeyword ? '无匹配档案，请调整关键词' : '暂无档案，点击右上角「档案登记」建档'">
            <el-table-column label="档号" width="160">
              <template #default="{ row }">
                <span class="archive-no">{{ row.archiveNo }}</span>
              </template>
            </el-table-column>
            <el-table-column label="档案标题" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="archive-title">{{ row.title }}</span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">
                <el-tag :color="ARCHIVE_TYPE_COLOR[row.archiveType]" effect="light" size="small" class="type-tag">
                  {{ ARCHIVE_TYPE_LABEL[row.archiveType] || row.archiveType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="载体" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="MEDIUM_TYPE[row.medium]" effect="plain" size="small">
                  {{ MEDIUM_LABEL[row.medium] || row.medium }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="存放位置" width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.location"><el-icon class="loc-icon"><Location /></el-icon>{{ row.location }}</span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="ARCHIVE_STATUS_TYPE[row.status]" size="small">{{ ARCHIVE_STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="归档时间" width="120" align="center">
              <template #default="{ row }">{{ (row.createTime || '').slice(0, 10) || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 'NORMAL' && row.medium !== 'ELECTRONIC'" size="small" type="primary" link
                           :icon="Position" @click="openBorrow(row)">借阅</el-button>
                <el-button size="small" link :icon="Document" @click="showBorrows(row)">记录</el-button>
                <el-button v-if="row.status !== 'DESTROYED'" size="small" type="danger" link
                           :icon="Delete" @click="openDestroy(row)">销毁</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :total="total"
                         layout="total, prev, pager, next" style="margin-top: 12px" @current-change="loadArchives" />
        </el-tab-pane>

        <el-tab-pane :label="`逾期借阅（${overdue.length}）`" name="overdue">
          <el-alert type="warning" :closable="false" style="margin-bottom: 12px"
                    title="应还日期已过且未归还的借阅记录自动标记 OVERDUE（归还动作即时刷新）" />
          <el-table :data="overdue" v-loading="overdueLoading" border stripe
                    :empty-text="overdueLoading ? '加载中' : '无逾期借阅，状态良好'">
            <el-table-column prop="id" label="借阅ID" width="90" />
            <el-table-column prop="archiveId" label="档案ID" width="90" />
            <el-table-column prop="borrower" label="借阅人" width="110">
              <template #default="{ row }"><span class="borrower">{{ row.borrower }}</span></template>
            </el-table-column>
            <el-table-column prop="borrowDate" label="借出日期" width="110" />
            <el-table-column prop="dueDate" label="应还日期" width="110" />
            <el-table-column label="逾期天数" width="110" align="center">
              <template #default="{ row }">
                <el-tag v-if="overdueDays(row) > 0" type="danger" effect="dark" size="small">超 {{ overdueDays(row) }} 天</el-tag>
                <el-tag v-else type="danger" size="small">{{ BORROW_STATUS_LABEL[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="purpose" label="借阅用途" min-width="160" show-overflow-tooltip />
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
    <el-dialog v-model="registerVisible" title="档案登记" width="560px">
      <el-alert type="info" :closable="false" style="margin-bottom: 14px"
                title="DA 档号由编码引擎自动生成；纸质/双轨载体必须填写实物存放位置" />
      <el-form label-width="100px">
        <el-form-item label="项目ID" required>
          <el-input-number v-model="registerForm.projectId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="档案标题" required>
          <el-input v-model="registerForm.title" maxlength="200" placeholder="如：3 号风场竣工验收记录" />
        </el-form-item>
        <el-form-item label="档案类型">
          <el-select v-model="registerForm.archiveType" style="width: 200px">
            <el-option v-for="(label, key) in ARCHIVE_TYPE_LABEL" :key="key" :label="label" :value="key" />
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
          <el-input v-model="registerForm.location" placeholder="如：档案室 A 区 03 柜" />
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
    <el-dialog v-model="borrowVisible" title="档案借阅" width="500px">
      <el-alert type="warning" :closable="false" style="margin-bottom: 14px" title="电子载体档案不可实物借出" />
      <el-descriptions v-if="current" :column="2" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="档号">{{ current.archiveNo }}</el-descriptions-item>
        <el-descriptions-item label="载体">{{ MEDIUM_LABEL[current.medium] }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="借阅人" required>
          <el-input v-model="borrowForm.borrower" style="width: 200px" placeholder="实际借阅人姓名" />
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
    <el-dialog v-model="borrowsVisible" :title="`借阅记录${current ? ' · ' + current.archiveNo : ''}`" width="680px">
      <el-table :data="borrows" border stripe :empty-text="'该档案暂无借阅记录'">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="borrower" label="借阅人" width="100" />
        <el-table-column prop="borrowDate" label="借出" width="100" />
        <el-table-column prop="dueDate" label="应还" width="100" />
        <el-table-column prop="returnDate" label="归还" width="100" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'RETURNED' ? 'success' : row.status === 'OVERDUE' ? 'danger' : 'warning'" size="small">
              {{ BORROW_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'RETURNED'" size="small" type="success"
                       @click="doReturn(row); borrowsVisible = false">归还</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 销毁 -->
    <el-dialog v-model="destroyVisible" title="鉴定销毁" width="500px">
      <el-alert type="error" :closable="false" style="margin-bottom: 14px"
                title="销毁操作不可恢复，请确认鉴定意见完整" />
      <el-descriptions v-if="current" :column="2" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="档号">{{ current.archiveNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ ARCHIVE_TYPE_LABEL[current.archiveType] }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
      </el-descriptions>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Plus, Search, Position, Document, Delete, Location, Collection, AlarmClock
} from '@element-plus/icons-vue'
import {
  ARCHIVE_STATUS_LABEL, ARCHIVE_STATUS_TYPE, BORROW_STATUS_LABEL, MEDIUM_LABEL,
  registerArchiveApi, listArchivesApi, borrowArchiveApi, returnArchiveApi,
  listBorrowsApi, overdueBorrowsApi, destroyArchiveApi
} from '@/api/phase3'
import type { ArchiveInfo, ArchiveBorrow } from '@/api/phase3'

const ARCHIVE_TYPE_LABEL: Record<string, string> = { CONTRACT: '合同', SCHEME: '方案', DRAWING: '图纸', RECORD: '记录', ACCEPTANCE: '验收' }
const ARCHIVE_TYPE_COLOR: Record<string, string> = {
  CONTRACT: '#e6a23c', SCHEME: '#409eff', DRAWING: '#67c23a', RECORD: '#909399', ACCEPTANCE: '#9254de'
}
const MEDIUM_TYPE: Record<string, any> = { ELECTRONIC: 'primary', PAPER: 'warning', DUAL: 'success' }

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
const queryType = ref('')
const queryKeyword = ref('')
const stats = reactive({ normal: 0, borrowed: 0, overdue: 0, destroyed: 0 })

const registerVisible = ref(false)
const borrowVisible = ref(false)
const borrowsVisible = ref(false)
const destroyVisible = ref(false)
let current: ArchiveInfo | null = null

const registerForm = reactive({ projectId: 1, title: '', archiveType: 'CONTRACT', medium: 'ELECTRONIC', location: '', remark: '' })
const borrowForm = reactive({ borrower: '', purpose: '', dueDays: 7, approveBy: '' })
const destroyRemark = ref('')

/** 关键词+类型本地过滤（状态由服务端过滤） */
const filteredArchives = computed(() => {
  const kw = queryKeyword.value.trim().toLowerCase()
  return archives.value.filter((a) => {
    const typeOk = !queryType.value || a.archiveType === queryType.value
    if (!kw) return typeOk
    const hit = [a.archiveNo, a.title, String(a.projectId)].some((v) => String(v || '').toLowerCase().includes(kw))
    return typeOk && hit
  })
})

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

/** 统计概览：一次大页取全量本地聚合（档案量级小） */
async function loadStats() {
  try {
    const res = await listArchivesApi(1, 500)
    const all = res.data.records
    stats.normal = all.filter((a) => a.status === 'NORMAL').length
    stats.borrowed = all.filter((a) => a.status === 'BORROWED').length
    stats.destroyed = all.filter((a) => a.status === 'DESTROYED').length
  } catch {
    /* 统计失败不阻塞主列表 */
  }
}

async function loadOverdue() {
  overdueLoading.value = true
  try {
    const res = await overdueBorrowsApi()
    overdue.value = res.data
    stats.overdue = res.data.length
  } finally {
    overdueLoading.value = false
  }
}

/** 逾期天数（自然日） */
function overdueDays(row: any): number {
  if (!row.dueDate) return 0
  const due = new Date(String(row.dueDate)).getTime()
  return Math.max(0, Math.floor((Date.now() - due) / 86400000))
}

function switchTab(tab: string, status?: string) {
  activeTab.value = tab
  if (status !== undefined) {
    queryStatus.value = status
    pageNum.value = 1
    loadArchives()
  }
  if (tab === 'overdue') loadOverdue()
}

function openRegister() {
  Object.assign(registerForm, { projectId: 1, title: '', archiveType: 'CONTRACT', medium: 'ELECTRONIC', location: '', remark: '' })
  registerVisible.value = true
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
    await Promise.all([loadArchives(), loadStats()])
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
    await Promise.all([loadArchives(), loadStats()])
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
  await Promise.all([loadArchives(), loadOverdue(), loadStats()])
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
    await Promise.all([loadArchives(), loadStats()])
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadArchives()
  loadOverdue()
  loadStats()
})
</script>

<style scoped>
.page-container { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }

.stat-row { margin-bottom: 12px; }
.stat-card {
  display: flex; align-items: center; gap: 12px;
  background: #fff; border: 1px solid #e4e7ed; border-radius: 8px;
  padding: 14px 16px; cursor: pointer; transition: box-shadow .2s, transform .2s;
}
.stat-card:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, .08); transform: translateY(-2px); }
.stat-icon {
  width: 42px; height: 42px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; color: #fff; flex-shrink: 0;
}
.stat-icon--success { background: linear-gradient(135deg, #67c23a, #4f9e28); }
.stat-icon--warning { background: linear-gradient(135deg, #e6a23c, #cf8a29); }
.stat-icon--danger  { background: linear-gradient(135deg, #f56c6c, #d94848); }
.stat-icon--info    { background: linear-gradient(135deg, #909399, #6b6e75); }
.stat-value { font-size: 22px; font-weight: 600; line-height: 1.2; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }

.toolbar { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }

.archive-no {
  font-family: Consolas, Monaco, monospace;
  color: #409eff; font-weight: 600; letter-spacing: .5px;
}
.archive-title { font-weight: 500; }
.type-tag { border: none; color: #fff; }
.loc-icon { vertical-align: -2px; margin-right: 3px; color: #909399; }
.borrower { font-weight: 500; }
.text-muted { color: #c0c4cc; }
</style>
