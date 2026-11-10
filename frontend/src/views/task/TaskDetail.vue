<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">任务详情</span></template>
    </el-page-header>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ task?.taskName }}</span>
              <el-tag :type="statusTag(task?.status)" size="small">{{ statusText(task?.status) }}</el-tag>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="任务ID">{{ task?.id }}</el-descriptions-item>
            <el-descriptions-item label="任务类型">{{ task?.taskType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="优先级">
              <el-tag :type="priorityTag(task?.priority)" size="small">{{ priorityText(task?.priority) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="进度">
              <el-progress :percentage="task?.progress || 0" />
            </el-descriptions-item>
            <el-descriptions-item label="计划工时">{{ task?.planHours || 0 }} h</el-descriptions-item>
            <el-descriptions-item label="实际工时">{{ task?.actualHours || 0 }} h</el-descriptions-item>
            <el-descriptions-item label="开始日期">{{ task?.startDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="截止日期">{{ task?.deadline || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ task?.createBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ task?.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="任务描述" :span="2">{{ task?.description || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div style="margin-top: 16px; display: flex; gap: 8px;">
            <el-button v-if="task?.status === 0" type="primary" @click="changeStatus(1, '开始执行')">开始执行</el-button>
            <el-button v-if="task?.status === 1" type="warning" @click="changeStatus(2, '提交审核')">提交审核</el-button>
            <el-button v-if="task?.status === 2" type="success" @click="changeStatus(3, '审核通过')">审核通过</el-button>
            <el-button v-if="task?.status === 2" type="danger" @click="changeStatus(1, '审核驳回')">驳回</el-button>
            <el-button v-if="task?.status === 1" @click="showProgressDialog = true">更新进度</el-button>
            <el-button v-if="task?.status === 1 || task?.status === 2" type="primary" plain @click="showWorkHourDialog = true">录入工时</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover" style="margin-bottom: 16px;">
          <template #header><span>工时记录</span></template>
          <el-timeline>
            <el-timeline-item v-for="wh in workHours" :key="wh.id" :timestamp="wh.workDate" placement="top">
              <p style="margin: 0 0 4px;"><strong>{{ wh.hours }} h</strong></p>
              <p style="margin: 0; color: #909399; font-size: 13px;">{{ wh.description }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!workHours.length" description="暂无工时记录" :image-size="60" />
        </el-card>

        <el-card shadow="hover">
          <template #header><span>操作日志</span></template>
          <el-timeline>
            <el-timeline-item v-for="log in taskLogs" :key="log.id" :timestamp="log.operTime" placement="top" type="primary">
              <p style="margin: 0 0 4px;"><strong>{{ log.operType }}</strong> - {{ log.operBy }}</p>
              <p style="margin: 0; color: #606266; font-size: 13px;">{{ log.operDesc }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!taskLogs.length" description="暂无操作日志" :image-size="60" />
        </el-card>

        <el-card shadow="hover" style="margin-bottom: 16px;">
          <template #header><span>子任务（{{ subs.length }}）</span></template>
          <div v-for="s in subs" :key="s.id" class="sub-row">
            <el-checkbox :model-value="s.status === 2" @change="(v: any) => toggleSub(s, v ? 2 : 0)" />
            <span class="sub-name" :class="{ done: s.status === 2 }">{{ s.subName }}</span>
            <span class="sub-meta">{{ s.planHours ? s.planHours + 'h' : '' }}</span>
          </div>
          <div style="display: flex; gap: 8px; margin-top: 12px;">
            <el-input v-model="newSubName" size="small" placeholder="添加子任务" @keyup.enter="addSub" />
            <el-button type="primary" size="small" @click="addSub">添加</el-button>
          </div>
          <el-empty v-if="!subs.length && !newSubName" description="暂无子任务" :image-size="50" />
        </el-card>

        <el-card shadow="hover">
          <template #header><span>评论（{{ comments.length }}）</span></template>
          <div v-for="c in comments" :key="c.id" class="cmt-row">
            <div class="cmt-head">
              <span class="cmt-user">{{ userNameOf(c.userId) }}</span>
              <span class="cmt-time">{{ c.createTime }}</span>
              <el-button v-if="c.userId === currentUserId" link type="danger" size="small" @click="removeComment(c)">删除</el-button>
            </div>
            <div class="cmt-content">{{ c.content }}</div>
          </div>
          <div style="display: flex; gap: 8px; margin-top: 12px;">
            <el-input v-model="newComment" type="textarea" :rows="2" placeholder="发表评论…" />
            <el-button type="primary" @click="addComment">发布</el-button>
          </div>
          <el-empty v-if="!comments.length" description="暂无评论" :image-size="50" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 任务资料 -->
    <el-card shadow="hover" style="margin-bottom: 16px;">
      <template #header>
        <div class="card-header">
          <span>任务资料（{{ taskFiles.length }}）<span class="sub-note">上传后按任务名重命名并归属本项目</span></span>
          <el-button type="primary" size="small" @click="tfDialog = true">上传资料</el-button>
        </div>
      </template>
      <el-table :data="taskFiles" size="small">
        <el-table-column prop="fileName" label="文件名（任务名_原名）" min-width="200" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="70" align="center" />
        <el-table-column label="大小" width="90" align="right">
          <template #default="{ row }: any">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
        </el-table-column>
        <el-table-column prop="createBy" label="上传人" width="90" />
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" size="small" @click="download(row)">下载</el-button>
            <el-button link type="warning" size="small" :loading="diffingId === row.id" @click="aiDiff(row)">AI 差异分析</el-button>
            <el-button link type="primary" size="small" @click="$router.push(`/file/detail/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!taskFiles.length" description="暂无资料，点击「上传资料」添加" :image-size="60" />
    </el-card>

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
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
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
}</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-header-title {
  font-size: 16px;
  font-weight: bold;
}
.sub-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}
.sub-name {
  flex: 1;
  font-size: 13px;
}
.sub-name.done {
  text-decoration: line-through;
  color: #909399;
}
.sub-meta {
  color: #909399;
  font-size: 12px;
}
.cmt-row {
  border-bottom: 1px solid #f0f0f0;
  padding: 8px 0;
}
.cmt-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cmt-user {
  font-weight: 600;
  font-size: 13px;
}
.cmt-time {
  color: #c0c4cc;
  font-size: 12px;
  flex: 1;
}
.cmt-content {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
  white-space: pre-wrap;
}
.sub-note {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
  font-weight: normal;
}
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
</style>
