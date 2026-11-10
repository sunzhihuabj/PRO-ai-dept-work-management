<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">文件上传</span></template>
    </el-page-header>

    <el-form :model="form" label-width="120px">
      <el-form-item label="项目" required>
        <el-select v-model="form.projectId" placeholder="请选择项目" filterable @change="loadPhases">
          <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="阶段" required>
        <el-select v-model="form.phaseId" placeholder="请选择阶段" :disabled="!form.projectId">
          <el-option v-for="ph in phases" :key="ph.id" :label="ph.phaseName" :value="ph.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="文件类型" required>
        <el-input v-model="form.fileType" placeholder="如: 需求文档 / 图纸 / 方案" />
      </el-form-item>

      <el-form-item label="描述" required>
        <el-input v-model="form.description" placeholder="版本说明" />
      </el-form-item>

      <el-form-item label="文件" required>
        <el-upload
          ref="uploadRef"
          action="#"
          :http-request="handleUpload"
          :multiple="false"
          :auto-upload="false"
          :limit="1"
          :on-change="onFileChange"
          :on-remove="() => fileList = []"
          drag
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">
              支持的文件类型: .doc/.docx/.xls/.xlsx/.ppt/.pptx/.pdf/.txt/.dwg/.jpg/.png/.gif/.zip/.rar，单个文件不超过 500 MB
            </div>
          </template>
        </el-upload>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="uploading" @click="submit">上传</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { getProjectListApi, getProjectPhasesApi } from '@/api/project'
import { uploadFileApi } from '@/api/file'

const form = reactive({
  projectId: undefined as number | undefined,
  phaseId: undefined as number | undefined,
  fileType: '',
  description: ''
})

// 本地选中的文件（element-plus upload 内部会管理实际的 el-upload 列表）
const fileList = ref<any[]>([])
const uploadRef = ref<any>(null)
const uploading = ref(false)

const projects = ref<any[]>([])
const phases = ref<any[]>([])

async function loadProjects() {
  try {
    const res = await getProjectListApi({ pageNum: 1, pageSize: 1000 })
    projects.value = res?.data?.records ?? []
  } catch (e) {
    ElMessage.error('加载项目失败')
  }
}

async function loadPhases() {
  if (!form.projectId) {
    phases.value = []
    form.phaseId = undefined
    return
  }
  try {
    const res = await getProjectPhasesApi(form.projectId)
    phases.value = res?.data ?? []
  } catch (e) {
    ElMessage.error('加载阶段失败')
  }
}

function onFileChange(file: any, files: any[]) {
  fileList.value = files
}

function handleUpload(option: any) {
  const fd = new FormData()
  fd.append('projectId', String(form.projectId))
  fd.append('phaseId', String(form.phaseId))
  fd.append('fileType', form.fileType)
  fd.append('description', form.description)
  fd.append('file', option.file)

  return uploadFileApi(fd)
    .then(() => {
      ElMessage.success('文件上传成功')
      reset()
    })
    .catch(() => Promise.reject())
}

function submit() {
  if (!form.projectId || !form.phaseId || !form.fileType || !form.description) {
    ElMessage.error('请完整填写表单')
    return
  }
  if (!fileList.value.length) {
    ElMessage.error('请选择文件')
    return
  }
  uploading.value = true
  uploadRef.value?.submit()
  setTimeout(() => (uploading.value = false), 800)
}

function reset() {
  form.projectId = undefined
  form.phaseId = undefined
  form.fileType = ''
  form.description = ''
  fileList.value = []
  uploadRef.value?.clearFiles()
}

onMounted(loadProjects)
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
</style>
