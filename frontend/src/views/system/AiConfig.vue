<template>
  <div class="ai-config">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>AI 服务配置</span>
          <span class="hint">通道按优先级升序形成降级链；API Key 加密存储，仅写入不回显</span>
        </div>
      </template>

      <el-table :data="configs" v-loading="loading" stripe>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="providerName" label="服务商" min-width="140" />
        <el-table-column prop="providerCode" label="编码" width="110" />
        <el-table-column prop="providerType" label="类型" width="140" />
        <el-table-column prop="model" label="模型" min-width="130" />
        <el-table-column prop="apiKeyHint" label="API Key" width="130">
          <template #default="{ row }">{{ row.apiKeyHint || '未配置' }}</template>
        </el-table-column>
        <el-table-column prop="rpmLimit" label="次/分钟" width="90" />
        <el-table-column prop="dailyLimit" label="日上限" width="80" />
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :loading="testingId === row.id" @click="handleTest(row)">测试连接</el-button>
            <el-button size="small" :loading="probingId === row.id" @click="handleProbeChannel(row)">探测模型</el-button>
            <el-button size="small" type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px">
        <el-button type="primary" @click="openCreate">新增通道配置</el-button>
      </div>
    </el-card>

    <el-dialog :close-on-click-modal="false" v-model="dialogVisible" :title="editingId ? '编辑通道配置' : '新增通道配置'" width="560px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="配置编码" required>
          <el-input v-model="form.providerCode" :disabled="!!editingId" placeholder="如 DEEPSEEK / ZHIPU" />
        </el-form-item>
        <el-form-item label="服务商名称" required>
          <el-input v-model="form.providerName" placeholder="如 DeepSeek / 智谱 GLM" />
        </el-form-item>
        <el-form-item label="通道类型" required>
          <el-select v-model="form.providerType" style="width: 100%">
            <el-option label="OpenAI 兼容（DeepSeek/智谱/通义/Kimi 等）" value="OPENAI_COMPAT" />
            <el-option label="Mock 测试通道" value="MOCK" />
            <el-option label="本地 Ollama" value="OLLAMA" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.providerType !== 'MOCK'" label="API 地址" required>
          <el-input v-model="form.endpoint" placeholder="如 https://api.deepseek.com/v1（Ollama 填根地址如 http://localhost:11434）" />
        </el-form-item>
        <el-form-item label="模型名">
          <div class="model-row">
            <el-select v-model="form.model" filterable allow-create default-first-option clearable
                       placeholder="如 deepseek-chat / glm-4-flash（可探测后选择）" style="flex: 1">
              <el-option v-for="m in probeOptions" :key="m.id" :label="m.id" :value="m.id">
                <span>{{ m.id }}</span>
                <span class="opt-sub">{{ m.ownedBy || '' }}</span>
              </el-option>
            </el-select>
            <el-button :disabled="form.providerType === 'MOCK' || !form.endpoint" :loading="probing"
                       @click="handleProbe">探测模型</el-button>
          </div>
          <span v-if="probeNote" class="field-hint">{{ probeNote }}</span>
        </el-form-item>
        <el-form-item v-if="form.providerType === 'OPENAI_COMPAT'" label="API Key" :required="!editingId">
          <el-input v-model="form.apiKey" type="password" show-password
                    :placeholder="editingId ? '留空表示不修改' : 'sk-...'" />
          <span v-if="editingId && savedKeyHint" class="field-hint">已保存密钥：{{ savedKeyHint }}（加密存储，仅写入不回显）</span>
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" />
          <span class="field-hint">数值小者优先调用，形成降级链</span>
        </el-form-item>
        <el-form-item label="每分钟上限">
          <el-input-number v-model="form.rpmLimit" :min="1" />
          <span class="field-hint">商用 API 建议保持 35 次/分钟</span>
        </el-form-item>
        <el-form-item label="每日上限">
          <el-input-number v-model="form.dailyLimit" :min="1" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabledBool" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAiConfigsApi, createAiConfigApi, updateAiConfigApi, deleteAiConfigApi, testAiConfigApi,
  probeModelsApi, listChannelModelsApi,
  type AiProviderConfig, type TestResult, type ProbeResult
} from '@/api/aiConfig'

const loading = ref(false)
const saving = ref(false)
const configs = ref<AiProviderConfig[]>([])
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const testingId = ref<number | null>(null)
const savedKeyHint = ref('')
const probing = ref(false)
const probingId = ref<number | null>(null)
const probeOptions = ref<ProbeResult['models']>([])
const probeNote = ref('')

const form = reactive({
  providerCode: '', providerName: '', providerType: 'OPENAI_COMPAT' as AiProviderConfig['providerType'],
  endpoint: '', model: '', apiKey: '', priority: 100, rpmLimit: 35, dailyLimit: 500,
  enabledBool: false, remark: ''
})

async function load() {
  loading.value = true
  try {
    const res = await getAiConfigsApi()
    configs.value = (res.data as any) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  savedKeyHint.value = ''
  resetProbe()
  Object.assign(form, {
    providerCode: '', providerName: '', providerType: 'OPENAI_COMPAT',
    endpoint: '', model: '', apiKey: '', priority: 100, rpmLimit: 35, dailyLimit: 500,
    enabledBool: false, remark: ''
  })
  dialogVisible.value = true
}

function openEdit(row: Record<string, any>) {
  editingId.value = row.id || null
  savedKeyHint.value = row.apiKeyHint || ''
  resetProbe()
  Object.assign(form, {
    providerCode: row.providerCode, providerName: row.providerName, providerType: row.providerType,
    endpoint: row.endpoint || '', model: row.model || '', apiKey: '',
    priority: row.priority || 100, rpmLimit: row.rpmLimit || 35, dailyLimit: row.dailyLimit || 500,
    enabledBool: row.enabled === 1, remark: row.remark || ''
  })
  dialogVisible.value = true
}

function resetProbe() {
  probeOptions.value = []
  probeNote.value = ''
}

/** 表单内探测：按当前填写的 API 地址（+ API Key）主动拉取模型列表，成功后下拉可选 */
async function handleProbe() {
  if (!form.endpoint) {
    ElMessage.warning('请先填写 API 地址')
    return
  }
  probing.value = true
  resetProbe()
  try {
    const res = await probeModelsApi({
      providerType: form.providerType,
      endpoint: form.endpoint,
      apiKey: form.apiKey || undefined
    })
    const result: ProbeResult = (res.data as any) || { success: false, count: 0 }
    if (result.success) {
      probeOptions.value = result.models || []
      probeNote.value = `探测到 ${result.count} 个模型（${result.protocol || '-'}，${result.durationMs ?? 0}ms${result.cached ? '，缓存' : ''}），下拉可直接选择`
      if (result.note) {
        ElMessage.warning(result.note)
      }
    } else {
      probeNote.value = ''
      ElMessage.error(`${result.errorMessage || '探测失败'}（${result.errorType || '-'}）`)
    }
  } finally {
    probing.value = false
  }
}

/** 行级探测：已保存通道服务端解密 Key 探测，弹窗展示模型清单 */
async function handleProbeChannel(row: Record<string, any>) {
  probingId.value = row.id || null
  try {
    const res = await listChannelModelsApi(row.id!)
    const result: ProbeResult = (res.data as any) || { success: false, count: 0 }
    if (result.success) {
      const list = (result.models || []).map((m) => m.id).join('\n')
      const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      ElMessageBox.alert(
        `<div style="font-size:12px;color:#909399;margin-bottom:8px">协议：${esc(result.protocol || '-')} ｜ 端点：${esc(result.endpointUsed || '-')} ｜ 耗时：${result.durationMs ?? 0}ms${result.cached ? ' ｜ 缓存' : ''}</div><pre style="max-height:420px;overflow:auto;margin:0;font-size:13px;line-height:1.7">${esc(list || '（空）')}</pre>`,
        `「${row.providerName}」探测到 ${result.count} 个模型`,
        { confirmButtonText: '知道了', dangerouslyUseHTMLString: true }
      ).catch(() => {})
    } else {
      ElMessage.error(`${result.errorMessage || '探测失败'}（${result.errorType || '-'}）`)
    }
  } finally {
    probingId.value = null
  }
}

async function handleSave() {
  if (!form.providerCode || !form.providerName) {
    ElMessage.warning('请填写配置编码与服务商名称')
    return
  }
  if (form.providerType !== 'MOCK' && !form.endpoint) {
    ElMessage.warning('该通道类型必须填写 API 地址')
    return
  }
  if (form.providerType === 'OPENAI_COMPAT' && !editingId.value && !form.apiKey) {
    ElMessage.warning('新增商用通道必须填写 API Key')
    return
  }
  saving.value = true
  try {
    const payload: AiProviderConfig = {
      providerCode: form.providerCode, providerName: form.providerName, providerType: form.providerType,
      endpoint: form.endpoint, model: form.model,
      apiKey: form.apiKey || undefined,
      priority: form.priority, rpmLimit: form.rpmLimit, dailyLimit: form.dailyLimit,
      enabled: form.enabledBool ? 1 : 0, remark: form.remark
    }
    if (editingId.value) {
      await updateAiConfigApi(editingId.value, payload)
      ElMessage.success('配置已更新')
    } else {
      await createAiConfigApi(payload)
      ElMessage.success('配置已创建')
    }
    dialogVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: Record<string, any>) {
  await ElMessageBox.confirm(`确定删除通道"${row.providerName}"吗？`, '提示', { type: 'warning' })
  await deleteAiConfigApi(row.id)
  ElMessage.success('已删除')
  await load()
}

async function handleTest(row: Record<string, any>) {
  testingId.value = row.id || null
  try {
    const res = await testAiConfigApi(row.id!)
    const result: TestResult = (res.data as any) || {}
    if (result.success) {
      ElMessage.success(`连接成功（${result.durationMs}ms）：${result.replyPreview || ''}`)
    } else {
      ElMessage.error(result.message || '连接失败')
    }
  } finally {
    testingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.hint {
  font-size: 12px;
  color: #909399;
}
.field-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}
.model-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.opt-sub {
  float: right;
  font-size: 12px;
  color: #909399;
}
</style>
