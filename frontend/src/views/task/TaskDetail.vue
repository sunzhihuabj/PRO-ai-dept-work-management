<template>
  <div class="page-container" v-loading="loading">
    <!-- 页头：核心信息一眼可见 -->
    <div class="page-head">
      <div class="page-head-left">
        <el-button :icon="Back" circle text @click="$router.back()" />
        <div class="page-title-wrap">
          <div class="page-title-row">
            <h2 class="page-title">{{ task?.taskName || '任务详情' }}</h2>
            <el-tag :type="statusTag(task?.status)" effect="dark" size="small">{{ statusText(task?.status) }}</el-tag>
            <el-tag :type="priorityTag(task?.priority)" effect="plain" size="small">
              {{ priorityText(task?.priority) }}优先级
            </el-tag>
            <el-tag v-if="deadlineNear" type="danger" effect="light" size="small">
              {{ deadlineText }}
            </el-tag>
          </div>
          <div class="page-sub">#{{ taskId }} · {{ task?.taskType || '常规任务' }} · 创建人 {{ task?.createBy || '-' }}</div>
        </div>
      </div>
      <!-- 操作按钮区：状态机主导操作，页面最上方直达 -->
      <div class="page-actions">
        <el-button v-if="task?.status === 0" type="primary" :icon="VideoPlay" @click="changeStatus(1, '开始执行')">开始执行</el-button>
        <el-button v-if="task?.status === 1" :icon="Upload" @click="showProgressDialog = true">更新进度</el-button>
        <el-button v-if="task?.status === 1 || task?.status === 2" type="primary" plain :icon="Timer" @click="showWorkHourDialog = true">录入工时</el-button>
        <el-button v-if="task?.status === 1" type="warning" :icon="Promotion" @click="changeStatus(2, '提交审核')">提交审核</el-button>
        <template v-if="task?.status === 2">
          <el-button type="success" :icon="CircleCheck" @click="changeStatus(3, '审核通过')">审核通过</el-button>
          <el-button type="danger" plain :icon="CircleClose" @click="changeStatus(1, '审核驳回')">驳回</el-button>
        </template>
      </div>
    </div>

    <el-row :gutter="16">
      <!-- 主列：状态进度 + 核心信息 + 描述 -->
      <el-col :xs="24" :md="16" :lg="16">
        <el-card shadow="never" class="section-card">
          <!-- 状态进度区 -->
          <div class="section-block">
            <div class="section-title">执行进度</div>
            <el-steps :active="stepActive" align-center :process-status="stepProcessStatus" finish-status="success"
                      class="task-steps">
              <el-step title="待办" />
              <el-step title="进行中" />
              <el-step title="待审核" />
              <el-step :title="task?.status === 4 ? '已关闭' : '已完成'" />
            </el-steps>
            <div class="progress-row">
              <el-progress :percentage="task?.progress || 0" :stroke-width="12" striped
                           :status="(task?.progress || 0) >= 100 ? 'success' : undefined" class="progress-bar" />
              <span class="progress-num">{{ task?.progress || 0 }}%</span>
            </div>
          </div>

          <el-divider style="margin: 8px 0 16px" />

          <!-- 核心指标网格 -->
          <div class="section-block">
            <div class="section-title">关键信息</div>
            <el-row :gutter="12" class="metric-grid">
              <el-col :xs="12" :md="8">
                <div class="metric-cell">
                  <div class="metric-label"><el-icon><Calendar /></el-icon>截止日期</div>
                  <div class="metric-value" :class="{ 'metric-danger': deadlineNear }">
                    {{ task?.deadline || '未设置' }}
                  </div>
                </div>
              </el-col>
              <el-col :xs="12" :md="8">
                <div class="metric-cell">
                  <div class="metric-label"><el-icon><Calendar /></el-icon>开始日期</div>
                  <div class="metric-value">{{ task?.startDate || '未设置' }}</div>
                </div>
              </el-col>
              <el-col :xs="12" :md="8">
                <div class="metric-cell">
                  <div class="metric-label"><el-icon><Timer /></el-icon>工时（计划/实际）</div>
                  <div class="metric-value">
                    {{ task?.planHours || 0 }} h
                    <span class="metric-sub">/ {{ task?.actualHours || 0 }} h</span>
                  </div>
                </div>
              </el-col>
              <el-col :xs="12" :md="8">
                <div class="metric-cell">
                  <div class="metric-label"><el-icon><Document /></el-icon>任务ID</div>
                  <div class="metric-value">#{{ task?.id }}</div>
                </div>
              </el-col>
              <el-col :xs="12" :md="8">
                <div class="metric-cell">
                  <div class="metric-label"><el-icon><Clock /></el-icon>创建时间</div>
                  <div class="metric-value metric-sm">{{ task?.createTime || '-' }}</div>
                </div>
              </el-col>
              <el-col :xs="12" :md="8">
                <div class="metric-cell">
                  <div class="metric-label"><el-icon><User /></el-icon>创建人</div>
                  <div class="metric-value">{{ task?.createBy || '-' }}</div>
                </div>
              </el-col>
            </el-row>
          </div>

          <el-divider style="margin: 8px 0 16px" />

          <!-- 任务描述 -->
          <div class="section-block">
            <div class="section-title">任务描述</div>
            <div v-if="task?.description" class="task-desc">{{ task.description }}</div>
            <div v-else class="desc-empty">未填写描述</div>
          </div>
        </el-card>

        <!-- 任务资料 -->
        <el-card shadow="never" class="section-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">任务资料<el-badge :value="taskFiles.length" :max="99" type="primary" class="card-badge" /></span>
              <span class="sub-note">上传后按任务名重命名并归属本项目</span>
              <el-button type="primary" size="small" :icon="Upload" @click="tfDialog = true">上传资料</el-button>
            </div>
          </template>
          <el-table v-if="taskFiles.length" :data="taskFiles" size="small" class="files-table">
            <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip>
              <template #default="{ row }: any">
                <el-icon class="file-icon"><Document /></el-icon>{{ row.fileName }}
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="70" align="center">
              <template #default="{ row }: any"><el-tag size="small" effect="plain">v{{ row.version }}</el-tag></template>
            </el-table-column>
            <el-table-column label="大小" width="90" align="right">
              <template #default="{ row }: any">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
            </el-table-column>
            <el-table-column prop="createBy" label="上传人" width="90" />
            <el-table-column prop="createTime" label="上传时间" width="170" />
            <el-table-column label="操作" width="210" align="center" fixed="right">
              <template #default="{ row }: any">
                <el-button link type="primary" size="small" :icon="Download" @click="download(row)">下载</el-button>
                <el-button link type="warning" size="small" :icon="MagicStick" :loading="diffingId === row.id" @click="aiDiff(row)">AI 差异分析</el-button>
                <el-button link size="small" :icon="View" @click="$router.push(`/file/detail/${row.id}`)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无资料，点击「上传资料」添加" :image-size="72" />
        </el-card>

        <!-- 操作日志（低频追溯，折叠收起减少滚动） -->
        <el-card shadow="never" class="section-card">
          <el-collapse v-model="logCollapse">
            <el-collapse-item name="logs">
              <template #title>
                <span class="card-title">操作日志<el-badge :value="taskLogs.length" :max="99" type="info" class="card-badge" /></span>
              </template>
              <el-timeline v-if="taskLogs.length" class="log-timeline">
                <el-timeline-item v-for="log in taskLogs" :key="log.id" :timestamp="log.operTime" placement="top" type="primary">
                  <p class="log-head"><strong>{{ log.operType }}</strong> · {{ log.operBy }}</p>
                  <p class="log-desc">{{ log.operDesc }}</p>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无操作日志" :image-size="60" />
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>

      <!-- 侧列：协作互动区（按使用频率从上到下） -->
      <el-col :xs="24" :md="8" :lg="8">
        <el-card shadow="never" class="side-card">
          <template #header>
            <span class="card-title">子任务<el-badge :value="subDoneCount" :max="99" :type="subAllDone ? 'success' : 'primary'"
                                                    :content="`${subDoneCount}/${subs.length}`" class="card-badge" /></span>
          </template>
          <div class="sub-list">
            <div v-for="s in subs" :key="s.id" class="sub-row">
              <el-checkbox :model-value="s.status === 2" @change="(v: any) => toggleSub(s, v ? 2 : 0)" />
              <span class="sub-name" :class="{ done: s.status === 2 }">{{ s.subName }}</span>
              <span v-if="s.planHours" class="sub-meta">{{ s.planHours }}h</span>
            </div>
            <el-empty v-if="!subs.length && !newSubName" description="暂无子任务" :image-size="54" />
          </div>
          <div class="inline-add">
            <el-input v-model="newSubName" size="small" placeholder="添加子任务，回车确认" @keyup.enter="addSub" />
            <el-button type="primary" size="small" :icon="Plus" @click="addSub" />
          </div>
        </el-card>

        <el-card shadow="never" class="side-card">
          <template #header><span class="card-title">参与成员</span></template>
          <div v-if="participants.length" class="member-list">
            <div v-for="p in participants" :key="p.id" class="member-item">
              <el-avatar :size="30" :style="{ background: avatarColor(p.id) }">{{ avatarText(p.name) }}</el-avatar>
              <span class="member-name">{{ p.name }}</span>
              <span class="member-role">{{ p.role }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无参与记录" :image-size="54" />
        </el-card>

        <el-card shadow="never" class="side-card">
          <template #header>
            <span class="card-title">工时记录<el-badge :value="totalHours" :max="999" type="primary" :content="`${totalHours}h`" class="card-badge" /></span>
          </template>
          <el-timeline v-if="workHours.length" class="wh-timeline">
            <el-timeline-item v-for="wh in workHours" :key="wh.id" :timestamp="wh.workDate" placement="top" type="warning">
              <p class="wh-hours">{{ wh.hours }} h</p>
              <p class="wh-desc">{{ wh.description }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无工时记录" :image-size="54" />
        </el-card>

        <el-card shadow="never" class="side-card">
          <template #header><span class="card-title">评论<el-badge :value="comments.length" :max="99" type="primary" class="card-badge" /></span></template>
          <div class="cmt-list">
            <div v-for="c in comments" :key="c.id" class="cmt-row">
              <el-avatar :size="26" :style="{ background: avatarColor(c.userId) }" class="cmt-avatar">{{ avatarText(userNameOf(c.userId)) }}</el-avatar>
              <div class="cmt-body">
                <div class="cmt-head">
                  <span class="cmt-user">{{ userNameOf(c.userId) }}</span>
                  <span class="cmt-time">{{ c.createTime }}</span>
                  <el-button v-if="c.userId === currentUserId" link type="danger" size="small"
                             class="cmt-del" @click="removeComment(c)">删除</el-button>
                </div>
                <div class="cmt-content">{{ c.content }}</div>
              </div>
            </div>
            <el-empty v-if="!comments.length" description="暂无评论，来抢沙发" :image-size="54" />
          </div>
          <div class="cmt-input">
            <el-input v-model="newComment" type="textarea" :rows="2" placeholder="发表评论…" maxlength="500" />
            <el-button type="primary" class="cmt-send" :icon="Promotion" @click="addComment">发布</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 上传任务资料 -->
    <el-dialog v-model="tfDialog" title="上传任务资料" width="480px">
      <el-form label-width="80px">
        <el-form-item label="说明">
          <el-input v-model="tfForm.description" maxlength="200" placeholder="资料说明（可选）" />
        </el-form-item>
        <el-form-item label="资料文件" required>
          <el-upload v-model:file-list="tfFiles" :auto-upload="false" multiple drag style="width: 100%">
            <el-button type="primary" plain>选择文件（可多选）</el-button>
            <template #tip>
              <div class="el-upload__tip">保存时将按任务名重命名并归属项目；同名续传自动递增版本</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tfDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploadingTf" @click="uploadTaskFiles">上传</el-button>
      </template>
    </el-dialog>

    <!-- AI 差异分析结果 -->
    <el-dialog v-model="diffDialog" :title="`AI 差异分析：${diffFile?.fileName ?? ''}（${diffResult?.prevVersion} → ${diffResult?.curVersion}）`" width="720px">
      <pre class="diff-result">{{ diffResult?.analysis }}</pre>
      <template #footer>
        <el-button @click="diffDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showProgressDialog" title="更新进度" width="400px">
      <el-slider v-model="progressValue" :min="0" :max="100" :step="5" show-input />
      <template #footer>
        <el-button @click="showProgressDialog = false">取消</el-button>
        <el-button type="primary" @click="updateProgress">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showWorkHourDialog" title="录入工时" width="480px">
      <el-form ref="whFormRef" :model="whForm" :rules="whRules" label-width="80px">
        <el-form-item label="工时" prop="hours">
          <el-input-number v-model="whForm.hours" :min="0.5" :max="24" :precision="1" :step="0.5" />
        </el-form-item>
        <el-form-item label="日期" prop="workDate">
          <el-date-picker v-model="whForm.workDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="工作内容" prop="description">
          <el-input v-model="whForm.description" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showWorkHourDialog = false">取消</el-button>
        <el-button type="primary" @click="submitWorkHour">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  Back, VideoPlay, Upload, Timer, Promotion, CircleCheck, CircleClose, Plus,
  Calendar, Document, Clock, User, Download, MagicStick, View
} from '@element-plus/icons-vue'
import { getTaskDetailApi, changeTaskStatusApi, updateTaskProgressApi, addWorkHourApi, getWorkHoursApi, getTaskLogsApi, type TaskItem } from '@/api/task'
import { get as httpGet, post as httpPost, put as httpPut, del as httpDel } from '@/utils/request'
import { listFilesApi, uploadFileApi, downloadFileApi, diffFileApi } from '@/api/file'
import { useUserStore } from '@/store/user'

const route = useRoute()
const taskId = Number(route.params.id)
const loading = ref(false)
const task = ref<TaskItem | null>(null)
const workHours = ref<any[]>([])
const taskLogs = ref<any[]>([])
const showProgressDialog = ref(false)
const progressValue = ref(0)
const showWorkHourDialog = ref(false)
const whFormRef = ref<FormInstance>()
const whForm = reactive({ hours: 8, workDate: '', description: '' })
const whRules: FormRules = {
  hours: [{ required: true, message: '请输入工时', trigger: 'blur' }],
  workDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  description: [{ required: true, message: '请输入工作内容', trigger: 'blur' }]
}

const priorityText = (p?: number) => p === 1 ? '高' : p === 3 ? '低' : '中'
const priorityTag = (p?: number) => p === 1 ? 'danger' as const : p === 3 ? 'info' as const : 'warning' as const
const statusText = (s?: number) => ['待办', '进行中', '待审核', '已完成', '已关闭'][s ?? 0] || '未知'
const statusTag = (s?: number) => (['info', 'primary', 'warning', 'success', 'danger'] as const)[s ?? 0] || 'info'

/** 状态机步骤条：0待办→1进行中→2待审核→3已完成（4已关闭单列） */
const stepActive = computed(() => {
  const s = task.value?.status ?? 0
  return s === 4 ? 3 : s
})
const stepProcessStatus = computed(() => ((task.value?.status ?? 0) === 4 ? 'error' : 'process') as 'error' | 'process')

/** 截止日期临近（3 天内且未完成/关闭） */
const deadlineNear = computed(() => {
  const dl = task.value?.deadline
  const s = task.value?.status
  if (!dl || (s !== undefined && s >= 3)) return false
  const diff = Math.ceil((new Date(dl).getTime() - Date.now()) / 86400000)
  return diff <= 3
})
const deadlineText = computed(() => {
  const dl = task.value?.deadline
  if (!dl) return ''
  const diff = Math.ceil((new Date(dl).getTime() - Date.now()) / 86400000)
  return diff < 0 ? `已逾期 ${-diff} 天` : diff === 0 ? '今日截止' : `${diff} 天后截止`
})

/** 参与成员：从创建人/操作日志/评论聚合去重 */
const participants = computed(() => {
  const map = new Map<number | string, { id: number | string; name: string; role: string }>()
  if (task.value?.createBy) map.set(task.value.createBy, { id: task.value.createBy, name: String(task.value.createBy), role: '创建人' })
  taskLogs.value.forEach((l) => {
    if (l.operBy && !map.has(l.operBy)) map.set(l.operBy, { id: l.operBy, name: String(l.operBy), role: '操作记录' })
  })
  comments.value.forEach((c) => {
    const name = userNameOf(c.userId)
    if (c.userId !== undefined && !map.has(name)) map.set(name, { id: name, name, role: '评论参与' })
  })
  return Array.from(map.values())
})

const totalHours = computed(() => Math.round(workHours.value.reduce((sum, w) => sum + (Number(w.hours) || 0), 0) * 10) / 10)
const subDoneCount = computed(() => subs.value.filter((s) => s.status === 2).length)
const subAllDone = computed(() => subs.value.length > 0 && subDoneCount.value === subs.value.length)

const AVATAR_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#9254de', '#13c2c2', '#fa8c16']
const avatarColor = (id: number | string | undefined) => AVATAR_COLORS[Math.abs(hashOf(String(id ?? ''))) % AVATAR_COLORS.length]
const avatarText = (name: string) => (name || '?').slice(0, 1)
function hashOf(s: string) {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return h
}

async function loadData() {
  loading.value = true
  try {
    const [taskRes, whRes, logRes] = await Promise.all([
      getTaskDetailApi(taskId), getWorkHoursApi(taskId), getTaskLogsApi(taskId)
    ])
    task.value = taskRes.data
    workHours.value = whRes.data || []
    taskLogs.value = logRes.data || []
    progressValue.value = task.value?.progress || 0
    whForm.workDate = new Date().toISOString().slice(0, 10)
  } finally {
    loading.value = false
  }
}

async function changeStatus(target: number, comment: string) {
  try {
    await changeTaskStatusApi(taskId, target, comment)
    ElMessage.success('状态流转成功')
    loadData()
  } catch (e) {}
}

async function updateProgress() {
  try {
    await updateTaskProgressApi(taskId, progressValue.value)
    ElMessage.success('进度更新成功')
    showProgressDialog.value = false
    loadData()
  } catch (e) {}
}

async function submitWorkHour() {
  if (!whFormRef.value) return
  await whFormRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await addWorkHourApi(taskId, { ...whForm })
      ElMessage.success('工时录入成功')
      showWorkHourDialog.value = false
      whForm.description = ''
      loadData()
    } catch (e) {}
  })
}

onMounted(() => {
  loadData()
  loadSubs()
  loadComments()
  loadTaskFiles()
})

// ---------- 子任务 / 评论 ----------
const userStore = useUserStore()
const subs = ref<any[]>([])
const comments = ref<any[]>([])
const newSubName = ref('')
const newComment = ref('')
const currentUserId = computed(() => userStore.userInfo?.id)
const userNameOf = (id?: number) => (id === currentUserId.value ? '我' : `用户#${id}`)

async function loadSubs() {
  try {
    const res = await httpGet(`/v1/tasks/${taskId}/subs`)
    subs.value = res?.data ?? []
  } catch (e) {}
}

async function addSub() {
  if (!newSubName.value.trim()) return
  try {
    await httpPost(`/v1/tasks/${taskId}/subs`, { subName: newSubName.value })
    newSubName.value = ''
    loadSubs()
  } catch (e) {}
}

async function toggleSub(s: any, status: number) {
  try {
    await httpPut(`/v1/tasks/subs/${s.id}/toggle?status=${status}`)
    loadSubs()
  } catch (e) {}
}

async function loadComments() {
  try {
    const res = await httpGet(`/v1/tasks/${taskId}/comments`)
    comments.value = res?.data ?? []
  } catch (e) {}
}

async function addComment() {
  if (!newComment.value.trim()) return
  try {
    await httpPost(`/v1/tasks/${taskId}/comments`, { content: newComment.value })
    newComment.value = ''
    loadComments()
  } catch (e) {}
}

async function removeComment(c: any) {
  try {
    await httpDel(`/v1/tasks/comments/${c.id}`)
    loadComments()
  } catch (e) {}
}

// ---------- 任务资料 ----------
const taskFiles = ref<any[]>([])
const tfDialog = ref(false)
const tfFiles = ref<any[]>([])
const tfForm = reactive({ description: '' })
const uploadingTf = ref(false)
const diffDialog = ref(false)
const diffFile = ref<any>(null)
const diffResult = ref<{ prevVersion: string; curVersion: string; analysis: string } | null>(null)
const diffingId = ref<number>()
const logCollapse = ref<string[]>([])

async function loadTaskFiles() {
  try {
    const res = await listFilesApi({ taskId, pageSize: 100 })
    taskFiles.value = res?.data?.records ?? []
  } catch (e) {}
}

async function uploadTaskFiles() {
  if (!tfFiles.value.length) {
    ElMessage.warning('请选择资料文件')
    return
  }
  uploadingTf.value = true
  let ok = 0
  let fail = 0
  try {
    for (const f of tfFiles.value) {
      try {
        const fd = new FormData()
        fd.append('projectId', String(task.value?.projectId ?? ''))
        fd.append('taskId', String(taskId))
        fd.append('fileType', '任务资料')
        fd.append('description', tfForm.description || '任务资料')
        fd.append('file', f.raw as File)
        await uploadFileApi(fd)
        ok++
      } catch (e) {
        fail++
      }
    }
    if (fail === 0) {
      ElMessage.success(`${ok} 份资料已按任务名重命名并保存到项目服务器`)
    } else {
      ElMessage.warning(`${ok} 份成功、${fail} 份失败`)
    }
    tfDialog.value = false
    tfFiles.value = []
    loadTaskFiles()
  } finally {
    uploadingTf.value = false
  }
}

async function download(row: any) {
  try {
    const res: any = await downloadFileApi(row.id)
    const blob = res instanceof Blob ? res : new Blob([res?.data ?? res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.fileName
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

async function aiDiff(row: any) {
  diffingId.value = row.id
  diffFile.value = row
  try {
    const res = await diffFileApi(row.id)
    diffResult.value = res?.data
    diffDialog.value = true
  } catch (e) {
  } finally {
    diffingId.value = undefined
  }
}
</script>

<style lang="scss" scoped>
.page-container { padding: 0 0 8px; }

/* ===== 页头 ===== */
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  padding: 8px 4px 16px;
}
.page-head-left { display: flex; align-items: flex-start; gap: 10px; min-width: 0; }
.page-title-wrap { min-width: 0; }
.page-title-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.page-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  word-break: break-all;
}
.page-sub { margin-top: 4px; font-size: 12px; color: #909399; }
.page-actions { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }

/* ===== 区块卡片 ===== */
.section-card { margin-bottom: 16px; border-radius: 8px; }
.side-card { margin-bottom: 16px; border-radius: 8px; }
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.card-title { font-weight: 600; font-size: 14px; display: inline-flex; align-items: center; }
.card-badge { margin-left: 8px; }
.sub-note { font-size: 12px; color: #909399; font-weight: normal; flex: 1; }

/* ===== 状态进度区 ===== */
.section-block { padding: 4px 0; }
.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
  line-height: 1.3;
}
.task-steps { margin: 8px 0 4px; }
.progress-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.progress-bar { flex: 1; }
.progress-num {
  font-size: 16px;
  font-weight: 700;
  color: #409eff;
  min-width: 48px;
  text-align: right;
}

/* ===== 核心指标网格 ===== */
.metric-grid { margin-top: 4px; }
.metric-cell {
  background: #f7f9fc;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
  transition: background-color .2s;
  &:hover { background: #eef3fb; }
}
.metric-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.metric-value { font-size: 15px; font-weight: 600; color: #303133; }
.metric-sm { font-size: 13px; font-weight: 500; }
.metric-sub { font-size: 12px; color: #909399; font-weight: 400; }
.metric-danger { color: #f56c6c; }

/* ===== 描述 ===== */
.task-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
  white-space: pre-wrap;
  background: #f7f9fc;
  border-radius: 6px;
  padding: 10px 14px;
}
.desc-empty { font-size: 13px; color: #c0c4cc; }

/* ===== 任务资料 ===== */
.files-table {
  :deep(.el-button + .el-button) { margin-left: 6px; }
}
.file-icon { vertical-align: -2px; margin-right: 6px; color: #409eff; }

/* ===== 操作日志 ===== */
.log-timeline { padding-left: 4px; }
.log-head { margin: 0 0 4px; font-size: 13px; }
.log-desc { margin: 0; color: #606266; font-size: 13px; }

/* ===== 子任务 ===== */
.sub-list { min-height: 8px; }
.sub-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 6px;
  transition: background-color .2s;
  &:hover { background: #f5f7fa; }
}
.sub-name { flex: 1; font-size: 13px; word-break: break-all; }
.sub-name.done { text-decoration: line-through; color: #909399; }
.sub-meta { color: #909399; font-size: 12px; }
.inline-add { display: flex; gap: 8px; margin-top: 10px; }

/* ===== 参与成员 ===== */
.member-list { display: flex; flex-direction: column; gap: 4px; }
.member-item {
  display: flex; align-items: center; gap: 10px;
  padding: 5px 8px; border-radius: 6px;
  transition: background-color .2s;
  &:hover { background: #f5f7fa; }
}
.member-name { font-size: 13px; font-weight: 500; flex: 1; }
.member-role { font-size: 12px; color: #909399; }

/* ===== 工时记录 ===== */
.wh-timeline { padding-left: 4px; }
.wh-hours { margin: 0 0 4px; font-weight: 600; font-size: 14px; color: #e6a23c; }
.wh-desc { margin: 0; color: #909399; font-size: 13px; }

/* ===== 评论 ===== */
.cmt-list { max-height: 360px; overflow: auto; }
.cmt-row {
  display: flex; gap: 8px;
  padding: 10px 6px;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color .2s;
  &:hover { background: #f5f7fa; }
  &:hover .cmt-del { opacity: 1; }
  &:last-child { border-bottom: none; }
}
.cmt-avatar { flex-shrink: 0; }
.cmt-body { flex: 1; min-width: 0; }
.cmt-head { display: flex; align-items: center; gap: 8px; }
.cmt-user { font-weight: 600; font-size: 13px; }
.cmt-time { color: #c0c4cc; font-size: 12px; flex: 1; }
.cmt-del { opacity: 0; transition: opacity .2s; }
.cmt-content {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
}
.cmt-input { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }
.cmt-send { align-self: flex-end; }

/* ===== AI 差异分析 ===== */
.diff-result {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.7;
  max-height: 420px;
  overflow: auto;
  margin: 0;
}

/* ===== 响应式适配 ===== */
@media (max-width: 768px) {
  .page-head { flex-direction: column; align-items: stretch; }
  .page-actions { justify-content: flex-start; }
  .page-actions .el-button { margin-left: 0; flex: 1 1 45%; }
  .progress-row { flex-direction: column; align-items: stretch; gap: 4px; }
  .progress-num { text-align: left; }
  .stat-block { width: 100%; }
}
</style>
