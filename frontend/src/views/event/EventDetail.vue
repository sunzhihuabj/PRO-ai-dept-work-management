<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">事件详情</span></template>
    </el-page-header>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>{{ event?.eventTitle }}</span>
              <el-tag :type="statusTag(event?.status)" size="small">{{ statusText(event?.status) }}</el-tag>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="事件ID">{{ event?.id }}</el-descriptions-item>
            <el-descriptions-item label="事件类型">{{ typeText(event?.eventType) }}</el-descriptions-item>
            <el-descriptions-item label="紧急程度">
              <el-tag :type="urgencyTag(event?.urgency)" size="small">{{ urgencyText(event?.urgency) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="所属项目">{{ projectName }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ applicantName }}</el-descriptions-item>
            <el-descriptions-item label="处理人">{{ handlerName }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ event?.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="关闭时间">{{ event?.closeTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="事件描述" :span="2">{{ event?.description || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div style="margin-top: 16px; display: flex; gap: 8px; flex-wrap: wrap;">
            <template v-if="isHandler">
              <el-button v-if="event?.status === 0" type="primary" @click="openHandleDialog('受理')">受理</el-button>
              <el-button v-if="event?.status === 0" type="danger" @click="openHandleDialog('驳回')">驳回</el-button>
              <el-button v-if="event?.status === 1" type="warning" @click="openHandleDialog('转派')">转派</el-button>
              <el-button v-if="event?.status === 1" @click="openHandleDialog('处理')">记录进展</el-button>
              <el-button v-if="event?.status === 1" type="success" @click="openHandleDialog('完成')">标记完成</el-button>
            </template>
            <el-button
              v-if="event && [0, 1, 2, 4].includes(event.status) && (isHandler || isApplicant)"
              type="info"
              plain
              @click="openHandleDialog('关闭')"
            >关闭事件</el-button>
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><span>处理记录</span></template>
          <el-timeline>
            <el-timeline-item v-for="h in handles" :key="h.id" :timestamp="h.handleTime" placement="top" type="primary">
              <p style="margin: 0 0 4px;">
                <el-tag size="small">{{ h.handleType }}</el-tag>
                <span style="margin-left: 8px; color: #606266; font-size: 13px;">{{ handlerNameOf(h.handlerId) }}</span>
              </p>
              <p v-if="h.handleDesc" style="margin: 0 0 2px; color: #303133; font-size: 13px;">{{ h.handleDesc }}</p>
              <p v-if="h.handleResult" style="margin: 0; color: #909399; font-size: 12px;">结果：{{ h.handleResult }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!handles.length" description="暂无处理记录" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="handleDialogVisible" :title="`${dialogHandleType}事件`" width="480px">
      <el-form label-width="80px">
        <el-form-item v-if="dialogHandleType === '转派'" label="新处理人" required>
          <el-select v-model="handleForm.transferTo" placeholder="请选择新处理人" filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理说明">
          <el-input v-model="handleForm.handleDesc" type="textarea" :rows="3" maxlength="500" show-word-limit
                    :placeholder="dialogHandleType === '驳回' ? '请填写驳回原因' : '请填写说明'" />
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input v-model="handleForm.handleResult" maxlength="200" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getEventDetailApi, getEventHandlesApi, handleEventApi,
  EVENT_STATUS, EVENT_TYPE, EVENT_URGENCY,
  type EventItem, type EventHandleItem
} from '@/api/event'
import { getProjectListApi, type ProjectItem } from '@/api/project'
import { getUserOptionsApi, type SysUser } from '@/api/user'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()
const eventId = Number(route.params.id)

const loading = ref(false)
const submitting = ref(false)
const event = ref<EventItem | null>(null)
const handles = ref<EventHandleItem[]>([])
const projects = ref<ProjectItem[]>([])
const userOptions = ref<SysUser[]>([])

const handleDialogVisible = ref(false)
const dialogHandleType = ref('')
const handleForm = reactive({ handleDesc: '', handleResult: '', transferTo: undefined as number | undefined })

const statusText = (s?: number) => EVENT_STATUS[s ?? 0] ?? '未知'
const statusTag = (s?: number) => (['warning', 'primary', 'success', 'info', 'danger'] as const)[s ?? 0] || 'info'
const typeText = (t?: number) => EVENT_TYPE[t ?? 99] ?? '其他'
const urgencyText = (u?: number) => EVENT_URGENCY[u ?? 2] ?? '一般'
const urgencyTag = (u?: number) => u === 1 ? 'danger' as const : u === 3 ? 'info' as const : 'warning' as const

const currentUserId = computed(() => userStore.userInfo?.id)
const isHandler = computed(() => !!event.value && event.value.handlerId === currentUserId.value)
const isApplicant = computed(() => !!event.value && event.value.applicantId === currentUserId.value)

const projectName = computed(() => projects.value.find(p => p.id === event.value?.projectId)?.projectName || `#${event.value?.projectId ?? '-'}`)
const applicantName = computed(() => userOptions.value.find(u => u.id === event.value?.applicantId)?.realName || `#${event.value?.applicantId ?? '-'}`)
const handlerName = computed(() => userOptions.value.find(u => u.id === event.value?.handlerId)?.realName || '未指派')
const handlerNameOf = (id?: number) => userOptions.value.find(u => u.id === id)?.realName || (id ? `#${id}` : '系统')

async function loadData() {
  loading.value = true
  try {
    const [eventRes, handleRes] = await Promise.all([
      getEventDetailApi(eventId),
      getEventHandlesApi(eventId)
    ])
    event.value = eventRes.data
    handles.value = handleRes.data || []
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  try {
    const [pRes, uRes] = await Promise.all([
      getProjectListApi({ pageNum: 1, pageSize: 500 }),
      getUserOptionsApi()
    ])
    projects.value = pRes.data.records
    userOptions.value = uRes.data
  } catch (e) {}
}

function openHandleDialog(type: string) {
  dialogHandleType.value = type
  Object.assign(handleForm, { handleDesc: '', handleResult: '', transferTo: undefined })
  handleDialogVisible.value = true
}

async function submitHandle() {
  submitting.value = true
  try {
    await handleEventApi(eventId, {
      handleType: dialogHandleType.value,
      handleDesc: handleForm.handleDesc,
      handleResult: handleForm.handleResult,
      transferTo: handleForm.transferTo
    })
    ElMessage.success('操作成功')
    handleDialogVisible.value = false
    await loadData()
  } catch (e) {
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadData()
  loadOptions()
})
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-header-title {
  font-size: 16px;
  font-weight: bold;
}
</style>
