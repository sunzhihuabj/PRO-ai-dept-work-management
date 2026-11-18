<template>
  <div class="doc-workbench">
    <!-- 状态过滤 -->
    <div class="wb-toolbar">
      <el-radio-group v-model="statusFilter" size="small" @change="load">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="DRAFT">草稿</el-radio-button>
        <el-radio-button label="REVIEW">待审</el-radio-button>
        <el-radio-button label="APPROVED">已审</el-radio-button>
      </el-radio-group>
      <el-button size="small" type="primary" plain @click="load">刷新</el-button>
    </div>

    <!-- 草稿列表 -->
    <el-scrollbar class="wb-list">
      <div v-for="d in docs" :key="d.id" class="doc-item" @click="openDetail(d)">
        <el-icon class="doc-icon" :size="16"><Document /></el-icon>
        <div class="doc-main">
          <div class="doc-title">{{ d.title }}</div>
          <div class="doc-meta">
            <el-tag :type="STATUS_TYPE[d.status] || 'info'" size="small">{{ STATUS_LABEL[d.status] || d.status }}</el-tag>
            <span class="meta-text">v{{ d.version }}</span>
            <span class="meta-text">{{ d.docNo }}</span>
            <span class="meta-text">{{ d.source }}</span>
          </div>
        </div>
        <span class="doc-time">{{ fmtTime(d.updateTime) }}</span>
      </div>
      <el-empty v-if="!docs.length" description="暂无 AI 起草文档——在对话中委托「起草XX方案」，草稿将自动落库到这里" :image-size="60" />
    </el-scrollbar>

    <!-- 详情抽屉：预览/编辑/合规面板/送审 -->
    <el-drawer v-model="drawer" :title="current?.title || '文档详情'" size="62%">
      <template v-if="current">
        <div class="dt-head">
          <el-tag :type="STATUS_TYPE[current.status] || 'info'" size="small">{{ STATUS_LABEL[current.status] || current.status }}</el-tag>
          <span class="dt-meta">{{ current.docNo }} · v{{ current.version }} · 来源 {{ current.source }} · {{ fmtTime(current.updateTime) }}</span>
          <div class="dt-ops">
            <el-button size="small" @click="startEdit" :disabled="editing">编辑</el-button>
            <el-button size="small" type="primary" plain :loading="checking" @click="runCompliance">合规检查</el-button>
            <el-button v-if="current.status === 'DRAFT'" size="small" type="warning" plain @click="submitReview">送审</el-button>
            <el-button v-if="current.status === 'REVIEW'" size="small" type="success" plain @click="approve">审核通过</el-button>
          </div>
        </div>

        <!-- 预览 / 编辑 -->
        <el-input v-if="editing" v-model="editTitle" class="edit-title" placeholder="文档标题" />
        <el-input v-if="editing" v-model="editContent" type="textarea" :rows="16" class="edit-area" placeholder="Markdown 正文" />
        <div v-else class="doc-view markdown-body" v-html="rendered"></div>
        <div v-if="editing" class="edit-actions">
          <el-button size="small" @click="editing = false">取消</el-button>
          <el-button size="small" type="primary" @click="saveEdit">保存（版本 +1）</el-button>
        </div>

        <!-- 合规风险清单面板 -->
        <div v-if="compliance" class="compliance-panel">
          <div class="cp-head">
            <span class="cp-title">合规风险清单（v{{ compliance.version }}）</span>
            <el-tag size="small" :type="compliance.items.length ? 'danger' : 'success'">
              {{ compliance.items.length ? `${compliance.items.length} 项风险` : '未发现风险' }}
            </el-tag>
          </div>
          <div class="cp-conclusion">{{ compliance.conclusion }}</div>
          <el-table :data="compliance.items" size="small" class="cp-table">
            <el-table-column prop="section" label="章节" width="140" show-overflow-tooltip />
            <el-table-column prop="risk" label="风险项" min-width="180" show-overflow-tooltip />
            <el-table-column prop="basis" label="依据" width="140" show-overflow-tooltip />
            <el-table-column prop="suggestion" label="建议改法" min-width="180" show-overflow-tooltip />
          </el-table>
          <div class="cp-tip">修改文档后可再次发起「合规检查」验证风险清零；文档修改在工作台人工完成（AI 不代改）。</div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document } from '@element-plus/icons-vue'
import { renderMarkdown } from '@/utils/sanitize'
import {
  listDocsApi, getDocApi, updateDocApi, submitDocReviewApi, approveDocApi, complianceCheckApi,
  type AiDocDraft, type DocRiskItem
} from '@/api/aiws'

const STATUS_LABEL: Record<string, string> = { DRAFT: '草稿', REVIEW: '待审', APPROVED: '已审' }
const STATUS_TYPE: Record<string, 'warning' | 'primary' | 'success' | 'info'> = {
  DRAFT: 'info', REVIEW: 'warning', APPROVED: 'success'
}

const statusFilter = ref('')
const docs = ref<AiDocDraft[]>([])
const drawer = ref(false)
const current = ref<AiDocDraft | null>(null)
const editing = ref(false)
const editTitle = ref('')
const editContent = ref('')
const checking = ref(false)
const compliance = ref<{ conclusion: string; items: DocRiskItem[]; version: number } | null>(null)

const rendered = computed(() => renderMarkdown(current.value?.contentMd || ''))

async function load() {
  try {
    const res = await listDocsApi(statusFilter.value || undefined)
    docs.value = res.data || []
  } catch { /* 静默 */ }
}

async function openDetail(d: AiDocDraft) {
  const res = await getDocApi(d.id)
  current.value = res.data || d
  compliance.value = null
  editing.value = false
  drawer.value = true
}

function startEdit() {
  editTitle.value = current.value?.title || ''
  editContent.value = current.value?.contentMd || ''
  editing.value = true
}

async function saveEdit() {
  if (!current.value) return
  const res = await updateDocApi(current.value.id, { title: editTitle.value, contentMd: editContent.value })
  if (res.data) {
    current.value = res.data
    editing.value = false
    ElMessage.success(`已保存（v${res.data.version}）`)
    load()
  }
}

async function runCompliance() {
  if (!current.value) return
  checking.value = true
  try {
    const res = await complianceCheckApi(current.value.id)
    compliance.value = res.data
    ElMessage.success('合规检查完成')
  } catch {
    ElMessage.error('合规检查失败（kb/模型通道异常）')
  } finally {
    checking.value = false
  }
}

async function submitReview() {
  if (!current.value) return
  await ElMessageBox.confirm(`确认将「${current.value.title}」（v${current.value.version}）送审？`, '送审确认', { type: 'warning' })
  const res = await submitDocReviewApi(current.value.id)
  if (res.data) { current.value = res.data; load(); ElMessage.success('已送审') }
}

async function approve() {
  if (!current.value) return
  const res = await approveDocApi(current.value.id)
  if (res.data) { current.value = res.data; load(); ElMessage.success('审核通过') }
}

function fmtTime(t?: string): string {
  if (!t) return ''
  const d = new Date(t.replace(' ', 'T'))
  return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

defineExpose({ load })

onMounted(load)
</script>

<style scoped>
.doc-workbench { display: flex; flex-direction: column; height: 100%; min-height: 0; padding: 8px 4px; }
.wb-toolbar { display: flex; align-items: center; justify-content: space-between; padding: 0 8px 8px; }
.wb-list { flex: 1; min-height: 0; }
.doc-item {
  display: flex; align-items: center; gap: 10px; padding: 9px 12px; border-radius: 8px;
  cursor: pointer; margin: 0 4px 2px;
  &:hover { background: var(--el-fill-color); }
}
.doc-icon { color: var(--el-color-primary); flex-shrink: 0; }
.doc-main { flex: 1; min-width: 0; }
.doc-title { font-size: 13px; color: var(--el-text-color-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.doc-meta { display: flex; align-items: center; gap: 6px; margin-top: 3px; }
.meta-text { font-size: 11px; color: var(--el-text-color-secondary); }
.doc-time { font-size: 11px; color: var(--el-text-color-secondary); flex-shrink: 0; }

.dt-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.dt-meta { font-size: 12px; color: var(--el-text-color-secondary); flex: 1; }
.dt-ops { display: flex; gap: 6px; }
.edit-title { margin-bottom: 8px; }
.edit-area { margin-bottom: 8px; }
.edit-actions { display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 12px; }
.doc-view { font-size: 13px; line-height: 1.7; }

.compliance-panel {
  margin-top: 16px; padding: 12px; border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px; background: var(--el-fill-color-extra-light);
}
.cp-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.cp-title { font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); }
.cp-conclusion { font-size: 12px; color: var(--el-text-color-regular); margin-bottom: 10px; }
.cp-table { margin-bottom: 8px; }
.cp-tip { font-size: 11px; color: var(--el-text-color-secondary); }
</style>
