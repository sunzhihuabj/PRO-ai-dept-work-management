<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getModelOptions, getModels, getSkills, getToolsets } from '../api/hermes'

const models = ref<any[]>([])
const modelOptions = ref<any>(null)
const toolsets = ref<any>(null)
const skills = ref<any[]>([])
const loading = ref(true)
const includeUnconfigured = ref(false)
const optionsLoading = ref(false)

function extractList(d: any): any[] {
  if (!d) return []
  if (Array.isArray(d)) return d
  return d.data || d.models || d.skills || d.toolsets || []
}

async function loadAll() {
  loading.value = true
  try {
    const [m, t, sk] = await Promise.allSettled([getModels(), getToolsets(), getSkills()])
    if (m.status === 'fulfilled') models.value = extractList(m.value)
    if (t.status === 'fulfilled') toolsets.value = t.value
    if (sk.status === 'fulfilled') skills.value = extractList(sk.value)
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  optionsLoading.value = true
  try {
    modelOptions.value = await getModelOptions({ include_unconfigured: includeUnconfigured.value })
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    optionsLoading.value = false
  }
}

onMounted(async () => {
  await loadAll()
  loadOptions()
})
</script>

<template>
  <div v-loading="loading">
    <div class="hs-card">
      <div class="hs-card-title"><el-icon><Cpu /></el-icon>模型（/v1/models）</div>
      <el-table :data="models" size="small">
        <el-table-column prop="id" label="模型 ID" min-width="240">
          <template #default="{ row }"><span class="mono">{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column prop="owned_by" label="owned_by" width="120" />
        <el-table-column prop="created" label="created" width="160">
          <template #default="{ row }">{{ row.created ? new Date(row.created * 1000).toLocaleString('zh-CN', { hour12: false }) : '-' }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div class="hs-card">
      <div class="hs-card-title" style="justify-content: space-between">
        <span style="display: flex; align-items: center; gap: 8px"><el-icon><Setting /></el-icon>模型选择器目录（/api/model/options）</span>
        <span>
          <el-checkbox v-model="includeUnconfigured" label="含未配置" @change="loadOptions" />
          <el-button size="small" :loading="optionsLoading" @click="loadOptions(true)">强制刷新</el-button>
        </span>
      </div>
      <pre v-if="modelOptions" class="mono raw-pre">{{ JSON.stringify(modelOptions, null, 2) }}</pre>
      <el-empty v-else description="无数据" :image-size="60" />
    </div>

    <div class="hs-card">
      <div class="hs-card-title"><el-icon><Grid /></el-icon>工具集（/v1/toolsets）</div>
      <el-collapse>
        <el-collapse-item v-for="(t, i) in extractList(toolsets)" :key="i" :name="i">
          <template #title>
            <b class="mono">{{ t.name || t.id || i }}</b>
            <el-tag size="small" :type="t.enabled === false ? 'info' : 'success'" effect="plain" style="margin-left: 8px">
              {{ t.enabled === false ? '停用' : '启用' }}
            </el-tag>
            <span v-if="t.tool_count" class="muted" style="margin-left: 8px">{{ t.tool_count }} 工具</span>
          </template>
          <pre class="mono raw-pre">{{ JSON.stringify(t, null, 2) }}</pre>
        </el-collapse-item>
      </el-collapse>
      <el-empty v-if="!extractList(toolsets).length" description="无工具集数据" :image-size="60" />
    </div>

    <div class="hs-card">
      <div class="hs-card-title"><el-icon><Collection /></el-icon>技能（/v1/skills）</div>
      <el-table :data="skills" size="small">
        <el-table-column prop="name" label="技能名" min-width="180">
          <template #default="{ row }"><span class="mono">{{ row.name || row.id || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="描述" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || row.summary || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!skills.length" description="无技能数据" :image-size="60" />
    </div>
  </div>
</template>

<style scoped>
.raw-pre {
  margin: 0;
  background: #1e1e28;
  color: #d4d4d8;
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  max-height: 320px;
  overflow: auto;
}
</style>
