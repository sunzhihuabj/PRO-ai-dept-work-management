<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content>
        <span class="page-header-title">Hermes Agent 参数配置</span>
        <el-tag :type="health?.hermesAvailable ? 'success' : 'danger'" size="small" style="margin-left: 10px;">
          {{ health?.hermesAvailable ? 'Hermes 在线' : 'Hermes 离线' }}
        </el-tag>
      </template>
    </el-page-header>

    <el-alert type="info" :closable="false" style="margin-bottom: 16px;"
              title="参数保存后即时生效（缓存刷新上限 10 秒）。chat 总开关关闭时自动回落网关直连通道，业务不断链。" />

    <el-card shadow="hover" v-loading="loading">
      <el-form label-width="220px">
        <el-divider content-position="left">迁移与运行模式</el-divider>
        <el-form-item v-for="(item, key) in items" :key="key" :label="labelOf(key as string)">
          <template v-if="isBool(key as string)">
            <el-switch v-model="items[key].value" active-value="true" inactive-value="false" />
          </template>
          <template v-else-if="isInt(key as string)">
            <el-input-number v-model="intModels[key]" :min="minOf(key as string)" :step="stepOf(key as string)"
                             @update:model-value="(v: number | undefined) => items[key].value = String(v ?? '')" />
          </template>
          <template v-else>
            <el-input v-model="items[key].value" :placeholder="item.description" style="max-width: 480px" />
          </template>
          <div class="desc">{{ item.description }}</div>
        </el-form-item>
      </el-form>
      <div style="display: flex; gap: 10px;">
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        <el-button @click="load">重置读取</el-button>
        <el-button type="warning" plain :loading="probing" @click="probe">探活 Hermes</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getHermesConfigApi, saveHermesConfigApi, getHermesHealthApi, type HermesHealth } from '@/api/hermesConfig'

const loading = ref(false)
const saving = ref(false)
const probing = ref(false)
const health = ref<HermesHealth | null>(null)
const items = ref<Record<string, { value: string; description: string }>>({})
const intModels = reactive<Record<string, number>>({})

const BOOL_KEYS = ['migrate.chatViaHermes', 'run.autoStopOnTimeout', 'delegate.enabled']
const INT_KEYS: Record<string, { min: number; step: number }> = {
  'migrate.syncTimeoutSec': { min: 30, step: 30 },
  'run.pollIntervalMs': { min: 500, step: 500 },
  'delegate.minLength': { min: 0, step: 5 },
  'output.maxResultChars': { min: 100, step: 500 }
}
const LABELS: Record<string, string> = {
  'migrate.chatViaHermes': 'chat 经 Hermes（总迁移开关）',
  'migrate.syncTimeoutSec': '同步等待超时（秒）',
  'run.pollIntervalMs': 'run 轮询间隔（毫秒）',
  'run.autoStopOnTimeout': '超时自动停止 run',
  'delegate.enabled': 'Hermes 委托路由',
  'delegate.triggerWords': '委托触发词',
  'delegate.minLength': '多步串联最短长度',
  'output.language': '输出语言',
  'output.maxResultChars': '结果摘要最大字符'
}
const isBool = (k: string) => BOOL_KEYS.includes(k)
const isInt = (k: string) => !!INT_KEYS[k]
const minOf = (k: string) => INT_KEYS[k]?.min ?? 0
const stepOf = (k: string) => INT_KEYS[k]?.step ?? 1
const labelOf = (k: string) => LABELS[k] ?? k

async function load() {
  loading.value = true
  try {
    const res = await getHermesConfigApi()
    items.value = res.data || {}
    Object.keys(items.value).forEach(k => {
      if (isInt(k)) {
        intModels[k] = parseInt(items.value[k].value || '0', 10)
      }
    })
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const payload: Record<string, string> = {}
    Object.keys(items.value).forEach(k => {
      payload[k] = isInt(k) ? String(intModels[k] ?? items.value[k].value) : items.value[k].value
    })
    await saveHermesConfigApi(payload)
    ElMessage.success('配置已保存并即时生效')
    load()
  } catch (e) {
  } finally {
    saving.value = false
  }
}

async function probe() {
  probing.value = true
  try {
    const res = await getHermesHealthApi()
    health.value = res.data
    ElMessage[health.value?.hermesAvailable ? 'success' : 'warning'](
      health.value?.hermesAvailable ? 'Hermes 在线' : 'Hermes 离线')
  } finally {
    probing.value = false
  }
}

onMounted(async () => {
  await load()
  try {
    const res = await getHermesHealthApi()
    health.value = res.data
  } catch (e) {}
})
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
.desc { color: #909399; font-size: 12px; line-height: 1.4; margin-top: 4px; }
</style>
