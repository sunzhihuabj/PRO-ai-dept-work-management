<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">文件列表</span></template>
    </el-page-header>

    <el-form :inline="true" :model="query" class="filter-form" style="margin-bottom: 20px;">
      <el-form-item label="项目">
        <el-select v-model="query.projectId" placeholder="请选择项目" @change="loadPhases">
          <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="阶段">
        <el-select v-model="query.phaseId" placeholder="请选择阶段" :disabled="!query.projectId">
          <el-option v-for="ph in phases" :key="ph.id" :label="ph.phaseName" :value="ph.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetchList">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
      <el-form-item label="关键字">
        <el-input v-model="query.keyword" placeholder="文件名或描述关键词" clearable @keyup.enter="fetchList" />
      </el-form-item>
      <el-form-item>
        <el-button type="success" @click="$router.push('/file/upload')">上传文件</el-button>
        <el-button @click="$router.push('/file/categories')">分类管理</el-button>
        <el-button @click="ledgerDrawer = true; loadLedger()">归档台账</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="fileList" style="width: 100%" v-loading="loading">
      <el-table-column prop="fileName" label="文件名" />
      <el-table-column prop="fileType" label="类型" width="80" />
      <el-table-column prop="fileSize" label="大小 (KB)" width="120" :formatter="sizeFormatter" />
      <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="isArchive" label="归档状态" width="100">
          <template #default="{ row }: any">
            <el-tag v-if="row.isArchive === 1" type="success">已归档</el-tag>
            <el-button v-else type="primary" size="small" @click="archiveFile(row.id)">归档</el-button>
          </template>
        </el-table-column>
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="createBy" label="上传者" width="120" />
      <el-table-column prop="createTime" label="上传时间" width="180" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }: any">
          <el-button link type="primary" size="small" @click="$router.push(`/file/detail/${row.id}`)">详情</el-button>
          <el-button link type="primary" size="small" @click="download(row)">下载</el-button>
        </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchList"
        @current-change="fetchList"
        style="margin-top: 16px; justify-content: flex-end; display: flex;"
      />

      <el-drawer v-model="ledgerDrawer" title="归档台账" size="640px">
        <el-table :data="ledger" size="small" border>
          <el-table-column prop="fileId" label="文件ID" width="80" />
          <el-table-column prop="archiveType" label="类型" width="80" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }: any">
              <el-tag :type="row.archiveStatus === 1 ? 'success' : 'info'" size="small">
                {{ ['未归档', '已归档', '归档中'][row.archiveStatus ?? 0] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="archiveBy" label="归档人" width="100" />
          <el-table-column prop="archiveTime" label="归档时间" width="170" />
          <el-table-column prop="description" label="说明" />
        </el-table>
        <el-empty v-if="!ledger.length" description="暂无归档记录" />
      </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProjectListApi, getProjectPhasesApi } from '@/api/project'
import { listFilesApi, archiveFileApi, downloadFileApi, listArchiveRecordsApi, type FileArchiveRecord } from '@/api/file'

interface FileInfo {
  id: number
  projectId: number
  phaseId: number
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  version: string
  versionNumber: number
  isArchive: number
  archiveTime?: string
  description: string
  createBy: string
  createTime: string
}

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  phaseId: undefined as number | undefined,
  keyword: ''
})
const projects = ref<any[]>([])
const phases = ref<any[]>([])
const fileList = ref<FileInfo[]>([])
const total = ref(0)
const loading = ref(false)

function sizeFormatter(row: any, column: any, cellValue: number) {
  return (cellValue / 1024).toFixed(2)
}

async function loadProjects() {
  const res = await getProjectListApi({ pageNum: 1, pageSize: 1000 })
  projects.value = (res?.data?.records ?? [])
}

async function loadPhases() {
  if (!query.projectId) {
    phases.value = []
    query.phaseId = undefined
    return
  }
  const res = await getProjectPhasesApi(query.projectId)
  phases.value = res?.data ?? []
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listFilesApi({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      projectId: query.projectId,
      phaseId: query.phaseId,
      keyword: query.keyword
    })
    fileList.value = res?.data?.records ?? []
    total.value = res?.data?.total ?? 0
  } catch (e) {
    ElMessage.error('获取文件列表失败')
  } finally {
    loading.value = false
  }
}

function reset() {
  query.projectId = undefined
  query.phaseId = undefined
  query.keyword = ''
  query.pageNum = 1
  phases.value = []
  fileList.value = []
  total.value = 0
}

/** 通过 Blob 下载（带 Authorization 头） */
async function download(row: FileInfo) {
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

async function archiveFile(id: number) {
  try {
    await archiveFileApi(id)
    ElMessage.success('文件已归档')
    await fetchList()
  } catch (e) {}
}

// ---------- 归档台账 ----------
const ledgerDrawer = ref(false)
const ledger = ref<FileArchiveRecord[]>([])

async function loadLedger() {
  try {
    const res = await listArchiveRecordsApi({ projectId: query.projectId })
    ledger.value = res?.data ?? []
  } catch (e) {}
}

onMounted(() => {
  loadProjects()
})
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.filter-form { display: flex; align-items: center; }
</style>
