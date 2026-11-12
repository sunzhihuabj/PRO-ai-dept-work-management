<template>
  <div class="ai-workspace">
    <!-- ==================== 左侧栏 ==================== -->
    <aside class="ws-sidebar">
      <div class="sidebar-head">
        <el-button type="primary" class="new-conv-btn" @click="newConversation">
          <el-icon><Plus /></el-icon>
          <span>新建对话</span>
        </el-button>
      </div>

      <el-tabs v-model="sideTab" class="sidebar-tabs">
        <!-- 对话历史 -->
        <el-tab-pane label="对话" name="chat">
          <el-scrollbar class="side-list">
            <div v-for="c in conversations" :key="c.id"
                 class="conv-item" :class="{ active: c.id === activeConvId }"
                 @click="openConversation(c.id)">
              <el-icon class="conv-icon"><ChatDotRound /></el-icon>
              <div class="conv-main">
                <div class="conv-title">{{ c.title }}</div>
                <div class="conv-time">{{ formatTime(c.lastMsgTime) }}</div>
              </div>
              <div class="conv-ops" @click.stop>
                <el-icon size="13" @click="renameConversation(c)"><Edit /></el-icon>
                <el-icon size="13" @click="removeConversation(c)"><Delete /></el-icon>
              </div>
            </div>
            <el-empty v-if="!conversations.length" description="暂无对话" :image-size="60" />
          </el-scrollbar>
        </el-tab-pane>

        <!-- 业务流程 -->
        <el-tab-pane label="流程" name="workflow">
          <el-scrollbar class="side-list">
            <div v-for="w in workflows" :key="w.id"
                 class="conv-item wf-item" @click="openWorkflowView()">
              <el-icon class="conv-icon"><SetUp /></el-icon>
              <div class="conv-main">
                <div class="conv-title">{{ w.name }}</div>
                <div class="conv-time">
                  <el-tag :type="WF_STATUS[w.status]" size="small">{{ WF_LABEL[w.status] }}</el-tag>
                  <span class="wf-code">{{ w.wfCode }}</span>
                </div>
              </div>
            </div>
            <el-empty v-if="!workflows.length" description="暂无流程" :image-size="60" />
          </el-scrollbar>
        </el-tab-pane>

        <!-- Agent 任务 -->
        <el-tab-pane label="任务" name="task">
          <el-scrollbar class="side-list">
            <div v-for="t in agentTasks" :key="t.id" class="conv-item" @click="viewTask(t)">
              <el-icon class="conv-icon"><Cpu /></el-icon>
              <div class="conv-main">
                <div class="conv-title">{{ t.instruction }}</div>
                <div class="conv-time">
                  <el-tag :type="TASK_ST[t.status]" size="small">{{ TASK_SL[t.status] || t.status }}</el-tag>
                  <span class="wf-code">{{ t.taskNo }}</span>
                </div>
              </div>
            </div>
            <el-empty v-if="!agentTasks.length" description="暂无任务" :image-size="60" />
          </el-scrollbar>
        </el-tab-pane>
      </el-tabs>
    </aside>

    <!-- ==================== 主区域 ==================== -->
    <main class="ws-main">
      <!-- 流程视图 -->
      <template v-if="mainView === 'workflow'">
        <div class="main-header">
          <el-button size="small" @click="backToChat">
            <el-icon><Back /></el-icon>返回对话
          </el-button>
          <span class="main-title">业务流程编排</span>
        </div>
        <div class="workflow-host">
          <WorkflowPanel ref="workflowPanelRef" />
        </div>
      </template>

      <!-- 对话视图 -->
      <template v-else>
        <!-- 欢迎态 -->
        <div v-if="!activeConvId" class="chat-welcome">
          <div class="welcome-logo">
            <el-icon :size="40" color="#409eff"><MagicStick /></el-icon>
          </div>
          <h2 class="welcome-title">AI 助手 · 统一智能入口</h2>
          <p class="welcome-sub">对话问答 · Agent 任务执行 · 业务流程编排，都在这里</p>
          <div class="welcome-chips">
            <div v-for="s in SUGGESTIONS" :key="s" class="chip" @click="startWith(s)">{{ s }}</div>
          </div>
        </div>

        <!-- 会话态 -->
        <template v-else>
          <div class="main-header chat-header">
            <span class="main-title">{{ activeTitle }}</span>
          </div>
          <el-scrollbar ref="chatScrollRef" class="chat-scroll">
            <div class="chat-list">
              <div v-for="m in messages" :key="m.id" class="msg-row" :class="m.role">
                <el-avatar v-if="m.role === 'assistant'" :size="30" class="msg-avatar ai">
                  <el-icon><MagicStick /></el-icon>
                </el-avatar>
                <div class="msg-body">
                  <div v-if="m.role === 'user'" class="bubble user-bubble">{{ m.content }}</div>
                  <template v-else>
                    <div v-if="m.msgType === 'text'" class="bubble ai-bubble markdown-body" v-html="renderMd(m.content)"></div>
                    <div v-else-if="m.msgType === 'task' && m.refTaskId" class="bubble ai-bubble no-pad">
                      <TaskCard :task-id="m.refTaskId" />
                    </div>
                    <div v-else-if="m.msgType === 'workflow' && m.refWorkflowId" class="bubble ai-bubble">
                      <div class="wf-card">
                        <el-icon color="#409eff"><SetUp /></el-icon>
                        <div class="wf-card-text markdown-body" v-html="renderMd(m.content)"></div>
                        <el-button size="small" type="primary" plain @click="openWorkflowView()">
                          打开流程面板编辑
                        </el-button>
                      </div>
                    </div>
                  </template>
                </div>
              </div>

              <!-- 发送中占位 -->
              <div v-if="pending" class="msg-row assistant">
                <el-avatar :size="30" class="msg-avatar ai"><el-icon><MagicStick /></el-icon></el-avatar>
                <div class="msg-body">
                  <div class="bubble ai-bubble thinking">
                    <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                    <span class="thinking-text">{{ pendingText }}</span>
                  </div>
                </div>
              </div>
            </div>
          </el-scrollbar>

          <!-- 输入区 -->
          <div class="chat-input-area">
            <div class="input-box">
              <el-input v-model="input" type="textarea" :rows="1" :autosize="{ minRows: 1, maxRows: 6 }"
                        placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                        @keydown="onInputKeydown" :disabled="pending" />
              <el-button type="primary" class="send-btn" :disabled="!input.trim() || pending" :loading="pending"
                         @click="send">
                <el-icon v-if="!pending"><Promotion /></el-icon>
              </el-button>
            </div>
            <div class="input-tip">AI 生成内容仅供参考，请自行核实后采用</div>
          </div>
        </template>
      </template>
    </main>

    <!-- 任务详情抽屉 -->
    <el-drawer v-model="taskDrawer" title="任务详情" size="420px">
      <TaskCard v-if="drawerTaskId" :task-id="drawerTaskId" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Back, ChatDotRound, Cpu, Delete, Edit, MagicStick, Plus, Promotion, SetUp } from '@element-plus/icons-vue'
import { marked } from 'marked'
import TaskCard from './components/TaskCard.vue'
import WorkflowPanel from './components/WorkflowPanel.vue'
import {
  chatApi, createConversationApi, deleteConversationApi, listConversationsApi,
  listMessagesApi, renameConversationApi,
  type AiConversation, type AiMessage
} from '@/api/aiws'
import { listWorkflowsApi, type WfWorkflow } from '@/api/aiws'
import { getMyTasksApi, type AgentTask } from '@/api/agent'

const SUGGESTIONS = [
  '检索知识库：项目验收流程规定',
  '帮我生成今日日报摘要',
  '创建一个「设备采购审批」流程',
  '查一下本月项目交付情况'
]

const WF_LABEL: Record<string, string> = { draft: '草稿', published: '已发布', offline: '已下线' }
type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const WF_STATUS: Record<string, TagType> = { draft: 'info', published: 'success', offline: 'warning' }
const TASK_SL: Record<string, string> = {
  queued: '排队中', running: '执行中', pending_confirm: '待确认', done: '已完成',
  failed: '失败', escalated: '已升级', cancelled: '已取消'
}
const TASK_ST: Record<string, TagType> = {
  queued: 'info', running: 'primary', pending_confirm: 'warning', done: 'success',
  failed: 'danger', escalated: 'warning', cancelled: 'info'
}

// ==================== 状态 ====================
const sideTab = ref<'chat' | 'workflow' | 'task'>('chat')
const mainView = ref<'chat' | 'workflow'>('chat')
const conversations = ref<AiConversation[]>([])
const workflows = ref<WfWorkflow[]>([])
const agentTasks = ref<AgentTask[]>([])
const activeConvId = ref<number | null>(null)
const messages = ref<AiMessage[]>([])
const input = ref('')
const pending = ref(false)
const pendingText = ref('思考中')
const chatScrollRef = ref()
const taskDrawer = ref(false)
const drawerTaskId = ref<number | null>(null)
const workflowPanelRef = ref()

const activeTitle = computed(() => conversations.value.find(c => c.id === activeConvId.value)?.title || '新对话')

// ==================== 数据加载 ====================

async function loadConversations() {
  const res = await listConversationsApi()
  conversations.value = res.data || []
}

async function loadWorkflows() {
  const res = await listWorkflowsApi(true)
  workflows.value = res.data || []
}

async function loadAgentTasks() {
  try {
    const res = await getMyTasksApi()
    agentTasks.value = res.data || []
  } catch { /* 任务列表失败不阻塞 */ }
}

function refreshSide() {
  loadConversations()
  loadWorkflows()
  loadAgentTasks()
}

onActivated(refreshSide)

// ==================== 会话 ====================

async function newConversation() {
  const res = await createConversationApi()
  if (res.data) {
    await loadConversations()
    await openConversation(res.data.id)
  }
}

async function openConversation(id: number) {
  activeConvId.value = id
  mainView.value = 'chat'
  const res = await listMessagesApi(id)
  messages.value = res.data || []
  scrollBottom()
}

async function renameConversation(c: AiConversation) {
  const { value } = await ElMessageBox.prompt('修改会话标题', '重命名', {
    inputValue: c.title, inputPattern: /\S+/, inputErrorMessage: '标题不能为空'
  })
  await renameConversationApi(c.id, value.trim())
  c.title = value.trim()
}

async function removeConversation(c: AiConversation) {
  await ElMessageBox.confirm(`确认删除对话「${c.title}」？删除后不可恢复。`, '删除确认', { type: 'warning' })
  await deleteConversationApi(c.id)
  if (activeConvId.value === c.id) {
    activeConvId.value = null
    messages.value = []
  }
  loadConversations()
}

// ==================== 对话 ====================

function startWith(s: string) {
  input.value = s
  send()
}

async function send() {
  const content = input.value.trim()
  if (!content || pending.value) return
  // 无会话则先创建
  if (!activeConvId.value) {
    const res = await createConversationApi()
    if (!res.data) return
    activeConvId.value = res.data.id
    messages.value = []
    await loadConversations()
  }
  input.value = ''
  // 乐观插入用户消息 + 思考占位
  const tempUser: AiMessage = {
    id: -Date.now(), conversationId: activeConvId.value!, role: 'user', content,
    msgType: 'text', createTime: new Date().toISOString()
  }
  messages.value.push(tempUser)
  pending.value = true
  pendingText.value = guessPendingText(content)
  scrollBottom()
  try {
    const res = await chatApi(activeConvId.value!, content)
    pending.value = false
    if (res.data?.userMessage && res.data?.assistantMessage) {
      // 用服务端持久化结果替换乐观消息
      messages.value.splice(messages.value.indexOf(tempUser), 1, res.data.userMessage, res.data.assistantMessage)
    }
    loadConversations()
    loadAgentTasks()
    loadWorkflows()
  } catch (e) {
    pending.value = false
    messages.value.splice(messages.value.indexOf(tempUser), 1)
  }
  scrollBottom()
}

/** 根据内容预判占位文案（消息状态实时反馈） */
function guessPendingText(content: string): string {
  if (/创建|新建|搭建|生成/.test(content) && /流程/.test(content)) return '正在生成业务流程草稿…'
  if (/检索|查询|查一下|知识|规定|怎么办/.test(content)) return '正在调度 Agent 能力…'
  if (/总结|摘要|日报|周报|纪要/.test(content)) return '正在整理内容…'
  return '思考中'
}

function onInputKeydown(ev: Event) {
  const ke = ev as KeyboardEvent
  if (ke.key === 'Enter' && !ke.shiftKey) {
    ke.preventDefault()
    send()
  }
}

function scrollBottom() {
  nextTick(() => {
    const wrap = chatScrollRef.value?.wrapRef as HTMLElement | undefined
    if (wrap) wrap.scrollTop = wrap.scrollHeight
  })
}

// ==================== 流程 / 任务入口 ====================

function openWorkflowView() {
  mainView.value = 'workflow'
  workflowPanelRef.value?.openWorkflowList?.()
  loadWorkflows()
}

function backToChat() {
  mainView.value = 'chat'
  loadWorkflows()
}

function viewTask(t: AgentTask) {
  drawerTaskId.value = t.id
  taskDrawer.value = true
}

// ==================== 工具 ====================

/** Markdown 渲染（基础净化：去 script/事件属性） */
function renderMd(src: string): string {
  if (!src) return ''
  const html = marked.parse(src, { async: false, breaks: true }) as string
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+\s*=\s*"[^"]*"/gi, '')
    .replace(/\son\w+\s*=\s*'[^']*'/gi, '')
}

function formatTime(t?: string): string {
  if (!t) return ''
  const d = new Date(t.replace(' ', 'T'))
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  return sameDay ? hm : `${d.getMonth() + 1}-${d.getDate()} ${hm}`
}
</script>

<style scoped lang="scss">
.ai-workspace {
  display: flex; height: 100%; min-height: 0;
  background: var(--el-bg-color); border-radius: 12px; overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
}

// ---------- 左侧栏 ----------
.ws-sidebar {
  width: 264px; flex-shrink: 0; display: flex; flex-direction: column;
  border-right: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-extra-light);
}
.sidebar-head { padding: 12px; }
.new-conv-btn { width: 100%; border-radius: 8px; }
.sidebar-tabs {
  flex: 1; min-height: 0; display: flex; flex-direction: column; padding: 0 8px;
  :deep(.el-tabs__header) { margin: 0 0 6px; }
  :deep(.el-tabs__nav-wrap::after) { height: 1px; }
  :deep(.el-tabs__content) { flex: 1; min-height: 0; }
  :deep(.el-tab-pane) { height: 100%; }
}
.side-list { height: 100%; }
.conv-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 8px;
  cursor: pointer; margin-bottom: 2px; position: relative;
  &:hover { background: var(--el-fill-color); .conv-ops { opacity: 1; } }
  &.active { background: var(--el-color-primary-light-9); }
}
.conv-icon { color: var(--el-text-color-secondary); flex-shrink: 0; }
.conv-main { min-width: 0; flex: 1; }
.conv-title {
  font-size: 13px; color: var(--el-text-color-primary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.conv-time {
  font-size: 11px; color: var(--el-text-color-secondary); margin-top: 2px;
  display: flex; align-items: center; gap: 6px;
}
.wf-code { font-size: 11px; }
.conv-ops {
  position: absolute; right: 8px; display: flex; gap: 6px; opacity: 0;
  color: var(--el-text-color-secondary); background: inherit; padding: 2px;
  transition: opacity 0.15s;
  .el-icon:hover { color: var(--el-color-primary); }
}

// ---------- 主区域 ----------
.ws-main { flex: 1; min-width: 0; display: flex; flex-direction: column; min-height: 0; }
.main-header {
  display: flex; align-items: center; gap: 10px; padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter); flex-shrink: 0;
}
.main-title { font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.workflow-host { flex: 1; min-height: 0; padding: 12px 16px; display: flex; }

// ---------- 欢迎态 ----------
.chat-welcome {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px;
}
.welcome-logo {
  width: 76px; height: 76px; border-radius: 22px; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, var(--el-color-primary-light-9), var(--el-color-primary-light-8));
}
.welcome-title { margin: 8px 0 0; font-size: 20px; color: var(--el-text-color-primary); }
.welcome-sub { margin: 0 0 12px; font-size: 13px; color: var(--el-text-color-secondary); }
.welcome-chips {
  display: grid; grid-template-columns: repeat(2, minmax(0, 260px)); gap: 10px; margin-top: 10px;
  .chip {
    padding: 12px 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px;
    font-size: 13px; color: var(--el-text-color-regular); cursor: pointer; text-align: center;
    transition: all 0.15s;
    &:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); transform: translateY(-1px); }
  }
}

// ---------- 会话态 ----------
.chat-header { justify-content: space-between; }
.chat-scroll { flex: 1; min-height: 0; }
.chat-list { padding: 16px 24px; display: flex; flex-direction: column; gap: 18px; }
.msg-row { display: flex; gap: 10px; &.user { flex-direction: row-reverse; } }
.msg-avatar.ai { background: linear-gradient(135deg, #409eff, #7c3aed); color: #fff; flex-shrink: 0; }
.msg-body { max-width: min(76%, 760px); min-width: 0; }
.bubble {
  padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.65;
  word-break: break-word; overflow-wrap: anywhere;
  &.no-pad { padding: 8px; }
}
.user-bubble {
  background: var(--el-color-primary); color: #fff; border-top-right-radius: 4px;
  white-space: pre-wrap;
}
.ai-bubble {
  background: var(--el-fill-color-light); border-top-left-radius: 4px;
}
.wf-card { display: flex; flex-direction: column; gap: 8px; align-items: flex-start; .el-icon { font-size: 20px; } }

// 思考占位
.thinking { display: flex; align-items: center; gap: 5px; color: var(--el-text-color-secondary); }
.dot {
  width: 6px; height: 6px; border-radius: 50%; background: var(--el-color-primary-light-5);
  animation: blink 1.2s infinite;
  &:nth-child(2) { animation-delay: 0.2s; }
  &:nth-child(3) { animation-delay: 0.4s; }
}
.thinking-text { margin-left: 4px; font-size: 13px; }
@keyframes blink { 0%, 100% { opacity: 0.25; } 50% { opacity: 1; } }

// ---------- 输入区 ----------
.chat-input-area { flex-shrink: 0; padding: 12px 24px 14px; border-top: 1px solid var(--el-border-color-lighter); }
.input-box {
  display: flex; align-items: flex-end; gap: 10px;
  border: 1px solid var(--el-border-color); border-radius: 14px; padding: 8px 10px;
  transition: border-color 0.15s;
  &:focus-within { border-color: var(--el-color-primary); }
  :deep(.el-textarea__inner) { box-shadow: none; padding: 6px 4px; font-size: 14px; }
}
.send-btn { height: 34px; width: 44px; border-radius: 10px; }
.input-tip { font-size: 11px; color: var(--el-text-color-secondary); margin-top: 6px; text-align: center; }

// 对话区 Markdown 基础排版
.markdown-body {
  :deep(p) { margin: 0 0 8px; &:last-child { margin-bottom: 0; } }
  :deep(ul), :deep(ol) { margin: 0 0 8px; padding-left: 20px; }
  :deep(h1), :deep(h2), :deep(h3), :deep(h4) { margin: 8px 0 6px; font-size: 14px; }
  :deep(code) { background: rgba(0, 0, 0, 0.06); padding: 1px 5px; border-radius: 4px; font-size: 12px; }
  :deep(pre) { background: rgba(0, 0, 0, 0.05); padding: 10px; border-radius: 8px; overflow-x: auto; }
  :deep(table) { border-collapse: collapse; margin: 6px 0; }
  :deep(th), :deep(td) { border: 1px solid var(--el-border-color-lighter); padding: 4px 8px; font-size: 12px; }
  :deep(blockquote) { margin: 6px 0; padding: 4px 12px; border-left: 3px solid var(--el-color-primary-light-5); color: var(--el-text-color-secondary); }
}

// 移动端兜底（≤768px 侧栏收窄）
@media (max-width: 768px) {
  .ws-sidebar { width: 200px; }
  .chat-list { padding: 12px; }
  .msg-body { max-width: 88%; }
  .welcome-chips { grid-template-columns: 1fr; }
}
</style>
