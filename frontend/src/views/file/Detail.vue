<template>
  <div class="page-container">
    <el-page-header @back="onBack" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">文件详情</span></template>
    </el-page-header>

    <el-descriptions v-if="fileInfo" title="基本信息" :column="2" border>
      <el-descriptions-item label="文件名">{{ fileInfo.fileName }}</el-descriptions-item>
      <el-descriptions-item label="类型">{{ fileInfo.fileType }}</el-descriptions-item>
      <el-descriptions-item label="大小 (KB)">{{ (fileInfo.fileSize / 1024).toFixed(2) }}</el-descriptions-item>
      <el-descriptions-item label="当前版本">{{ fileInfo.version }}</el-descriptions-item>
      <el-descriptions-item label="描述">{{ fileInfo.description }}</el-descriptions-item>
      <el-descriptions-item label="上传者">{{ fileInfo.createBy }}</el-descriptions-item>
      <el-descriptions-item label="上传时间">{{ fileInfo.createTime }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="fileInfo.isArchive === 1 ? 'success' : 'info'" size="small">
          {{ fileInfo.isArchive === 1 ? '已归档' : '未归档' }}
        </el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <el-divider>历史版本</el-divider>
    <el-table :data="versions" size="small" border style="margin-bottom: 20px">
      <el-table-column prop="versionLabel" label="版本" width="100" />
      <el-table-column label="大小 (KB)" width="120">
        <template #default="{ row }: any">{{ (row.fileSize / 1024).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="changeDesc" label="变更说明" />
      <el-table-column prop="createBy" label="上传人" width="110" />
      <el-table-column prop="createTime" label="上传时间" width="180" />
      <el-table-column label="操作" width="90" align="center">
        <template #default="{ row }: any">
          <el-button link type="primary" size="small" @click="downloadVersion(row)">下载</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无历史版本" :image-size="50" /></template>
    </el-table>

    <el-divider>审核记录</el-divider>
    <el-table v-if="auditList.length" :data="auditList" style="width: 100%" border>
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="comment" label="意见" />
      <el-table-column prop="auditTime" label="时间" width="180" />
      <el-table-column prop="auditorId" label="审核人ID" width="100" />
    </el-table>
    <el-empty v-else description="暂无审核记录" />

    <el-button v-if="canAudit" type="primary" style="margin-top: 20px;" @click="openAuditDialog">发起审核</el-button>

    <el-dialog v-model="auditDialogVisible" title="文件审核" width="400px">
      <el-form :model="auditForm" label-width="80px">
        <el-form-item label="状态" required>
          <el-radio-group v-model="auditForm.status">
            <el-radio label="APPROVED">通过</el-radio>
            <el-radio label="REJECTED">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input type="textarea" v-model="auditForm.comment" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAudit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getFileDetailApi, auditFileApi, listFileAuditsApi,
  listFileVersionsApi, downloadFileVersionApi,
  type FileVersion
} from '@/api/file'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const fileId = Number(route.params.id)

const fileInfo = ref<any>(null)
const versions = ref<FileVersion[]>([])
const auditList = ref<any[]>([])
const auditDialogVisible = ref(false)
const auditForm = reactive({ status: 'APPROVED', comment: '' })

// 允许发起审核的角色
const AUDIT_ROLES = ['ROLE_ARCHIVIST', 'ROLE_AUDITOR', 'ROLE_DEPT_LEADER']
const canAudit = computed(() => AUDIT_ROLES.some(r => userStore.roles.includes(r)))

async function loadDetail() {
  try {
    const res = await getFileDetailApi(fileId)
    fileInfo.value = res?.data
  } catch (e) {
    ElMessage.error('获取文件信息失败')
  }
}

async function loadVersions() {
  try {
    const res = await listFileVersionsApi(fileId)
    versions.value = res?.data ?? []
  } catch (e) {
    versions.value = []
  }
}

async function loadAudits() {
  try {
    const res = await listFileAuditsApi(fileId)
    auditList.value = res?.data ?? []
  } catch (e) {
    ElMessage.error('获取审核记录失败')
  }
}

function onBack() {
  router.back()
}

function openAuditDialog() {
  auditForm.status = 'APPROVED'
  auditForm.comment = ''
  auditDialogVisible.value = true
}

/** Blob 方式下载历史版本（带 Authorization 头） */
async function downloadVersion(row: FileVersion) {
  try {
    const res: any = await downloadFileVersionApi(row.id)
    const blob = res instanceof Blob ? res : new Blob([res?.data ?? res])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.versionLabel || 'v' + row.versionNumber}_${fileInfo.value?.fileName ?? row.versionLabel}`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

async function submitAudit() {
  try {
    await auditFileApi(fileId, { status: auditForm.status, comment: auditForm.comment })
    ElMessage.success('审核已提交')
    auditDialogVisible.value = false
    await loadAudits()
  } catch (e) {
    ElMessage.error('审核提交失败')
  }
}

onMounted(async () => {
  await Promise.all([loadDetail(), loadVersions(), loadAudits()])
})
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
</style>
