<template>
  <div class="page-container">
    <el-page-header @back="$router.back()" style="margin-bottom: 16px;">
      <template #content><span class="page-header-title">AI 助手</span></template>
    </el-page-header>

    <el-card shadow="hover">
      <el-form label-width="90px">
        <el-form-item label="摘要类型">
          <el-radio-group v-model="type">
            <el-radio value="daily">日报</el-radio>
            <el-radio value="weekly">周报</el-radio>
            <el-radio value="meeting">会议纪要</el-radio>
            <el-radio value="generic">通用摘要</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原始内容">
          <el-input v-model="text" type="textarea" :rows="8" maxlength="5000" show-word-limit
                    placeholder="粘贴工作内容、会议记录等，单击生成 AI 摘要" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="runSummary">生成摘要</el-button>
          <el-button @click="text = ''">清空</el-button>
        </el-form-item>
      </el-form>

      <el-divider v-if="result" />
      <div v-if="result">
        <h4 style="margin: 0 0 8px;">AI 摘要结果</h4>
        <pre class="result-box">{{ result }}</pre>
        <el-button size="small" @click="copyResult">复制</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { summarizeApi } from '@/api/ai'

const text = ref('')
const type = ref<'daily' | 'weekly' | 'meeting' | 'generic'>('daily')
const result = ref('')
const loading = ref(false)

async function runSummary() {
  if (!text.value.trim()) {
    ElMessage.warning('请先输入内容')
    return
  }
  loading.value = true
  try {
    const res = await summarizeApi({ text: text.value, type: type.value })
    result.value = res.data.summary || ''
  } catch (e) {
    result.value = ''
  } finally {
    loading.value = false
  }
}

async function copyResult() {
  try {
    await navigator.clipboard.writeText(result.value)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<style scoped lang="scss">
.page-container { padding: 20px; }
.page-header-title { font-size: 16px; font-weight: bold; }
.result-box {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 12px;
}
</style>
