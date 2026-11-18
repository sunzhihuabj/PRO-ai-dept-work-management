<template>
  <div class="ai-workspace">
    <!-- ==================== 左侧栏（T-409：可折叠，对齐豆包/WorkBuddy） ==================== -->
    <aside class="ws-sidebar" :class="{ collapsed }">
      <div class="sidebar-head">
        <el-button v-if="!collapsed" type="primary" class="new-conv-btn" @click="newConversation">
          <el-icon><Plus /></el-icon>
          <span>新建对话</span>
        </el-button>
        <el-tooltip v-else content="新建对话" placement="right">
          <el-button type="primary" class="icon-btn" @click="newConversation"><el-icon><Plus /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip :content="collapsed ? '展开侧栏' : '收起侧栏'" placement="right">
          <el-button class="icon-btn" @click="collapsed = !collapsed">
            <el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon>
          </el-button>
        </el-tooltip>
      </div>

      <template v-if="!collapsed">
        <!-- 会话搜索（T-409 对齐豆包/WorkBuddy） -->
        <div class="side-search">
          <el-input v-model="searchKey" placeholder="搜索会话…" size="small" clearable :prefix-icon="Search" />
        </div>

        <el-tabs v-model="sideTab" class="sidebar-tabs">
          <!-- 对话历史：置顶 + 时间分组 -->
          <el-tab-pane label="对话" name="chat">
            <el-scrollbar class="side-list">
              <template v-for="g in groupedConversations" :key="g.label">
                <div class="group-label">{{ g.label }}</div>
                <div v-for="c in g.items" :key="c.id"
                     class="conv-item" :class="{ active: c.id === activeConvId }"
                     @click="openConversation(c.id)">
                  <el-icon class="conv-icon"><ChatDotRound /></el-icon>
                  <div class="conv-main">
                    <div class="conv-title">{{ c.title }}</div>
                    <div class="conv-time">{{ formatTime(c.lastMsgTime) }}</div>
                  </div>
                  <div class="conv-ops" @click.stop>
                    <el-tooltip :content="c.pinned ? '取消置顶' : '置顶'" placement="top">
                      <el-icon size="13" :class="{ pinned: !!c.pinned }" @click="togglePin(c)"><Top /></el-icon>
                    </el-tooltip>
                    <el-icon size="13" @click="renameConversation(c)"><Edit /></el-icon>
                    <el-icon size="13" @click="removeConversation(c)"><Delete /></el-icon>
                  </div>
                </div>
              </template>
              <el-empty v-if="!groupedConversations.length" :description="searchKey ? '无匹配会话' : '暂无对话'" :image-size="60" />
            </el-scrollbar>
          </el-tab-pane>

          <!-- 能力广场（T-409 对齐豆包智能体市场/WorkBuddy 技能） -->
          <el-tab-pane label="能力" name="capability">
            <el-scrollbar class="side-list">
              <div v-for="cap in capabilities" :key="cap.capabilityCode"
                   class="cap-item" @click="useCapability(cap)">
                <div class="cap-code">{{ cap.capabilityCode }}</div>
                <div class="cap-name">{{ cap.capabilityName || '能力点' }}</div>
                <el-tag size="small" :type="cap.level != null ? LEVEL_TYPE[cap.level] : 'info'" class="cap-level">{{ cap.level != null ? (LEVEL_LABEL[cap.level] || '未知') : '未知' }}</el-tag>
              </div>
              <el-empty v-if="!capabilities.length" description="能力加载中…" :image-size="60" />
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
      </template>

      <!-- 折叠态图标导航 -->
      <div v-else class="collapsed-rail">
        <el-tooltip content="对话" placement="right"><el-icon class="rail-icon" :class="{ on: sideTab === 'chat' }" @click="collapsed = false"><ChatDotRound /></el-icon></el-tooltip>
        <el-tooltip content="能力广场" placement="right"><el-icon class="rail-icon" :class="{ on: sideTab === 'capability' }" @click="collapsed = false; sideTab = 'capability'"><MagicStick /></el-icon></el-tooltip>
        <el-tooltip content="流程" placement="right"><el-icon class="rail-icon" :class="{ on: sideTab === 'workflow' }" @click="collapsed = false; sideTab = 'workflow'"><SetUp /></el-icon></el-tooltip>
        <el-tooltip content="任务" placement="right"><el-icon class="rail-icon" :class="{ on: sideTab === 'task' }" @click="collapsed = false; sideTab = 'task'"><Cpu /></el-icon></el-tooltip>
      </div>
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
        <!-- 欢迎态（T-409 遵循 WorkBuddy 5.11 减法原则：≤6 场景胶囊，突出输入） -->
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
            <div class="header-actions">
              <el-tooltip content="复制全文" placement="bottom">
                <el-button size="small" text @click="copyConversation"><el-icon><DocumentCopy /></el-icon></el-button>
              </el-tooltip>
              <el-tooltip content="导出 Markdown" placement="bottom">
                <el-button size="small" text @click="exportMd"><el-icon><Download /></el-icon></el-button>
              </el-tooltip>
            </div>
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
                    <div v-else-if="m.msgType === 'hermes'" class="bubble ai-bubble no-pad">
                      <HermesCard :content="m.content" />
                    </div>
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

                    <!-- 消息操作栏（T-409 对齐豆包：复制/朗读/重新生成/赞/踩） -->
                    <div v-if="m.msgType === 'text'" class="msg-ops">
                      <el-tooltip content="复制" placement="top">
                        <el-icon size="14" @click="copyText(m.content)"><DocumentCopy /></el-icon>
                      </el-tooltip>
                      <el-tooltip v-if="ttsOk" :content="speakingId === m.id ? '停止朗读' : '朗读'" placement="top">
                        <el-icon size="14" :class="{ speaking: speakingId === m.id }" @click="toggleSpeak(m)">
                          <VideoPause v-if="speakingId === m.id" /><VideoPlay v-else />
                        </el-icon>
                      </el-tooltip>
                      <el-tooltip v-if="isLastAssistant(m)" content="重新生成" placement="top">
                        <el-icon size="14" @click="regenerate" :class="{ disabled: pending }"><RefreshRight /></el-icon>
                      </el-tooltip>
                      <el-tooltip content="赞" placement="top">
                        <el-icon size="14" :class="{ rated: m.rating === 1 }" @click="rate(m, 1)"><svg viewBox="0 0 1024 1024" width="1em" height="1em" fill="currentColor"><path d="M885.9 533.7c-78.3-40.7-161.5-51.8-161.5-51.8 22.3-56.5 24.9-121.1 6.5-179.6-21.6-69.4-72.3-127.9-138.5-160.1-13.2-6.5-28.9-3.4-38.7 7.3L448.4 283.2 341.1 149.5c-9.8-10.7-25.5-13.8-38.7-7.3-66.2 32.2-116.9 90.7-138.5 160.1-18.4 58.5-15.8 123.1 6.5 179.6 0 0-83.2 11.1-161.5 51.8-29 15.1-40.2 51-25 80 40.7 78.3 123.9 129.5 212.4 129.5h415.4c88.5 0 171.7-51.2 212.4-129.5 15.2-29 4-64.9-25-80z" transform="rotate(180 512 512)"/></svg></el-icon>
                      </el-tooltip>
                      <el-tooltip content="踩" placement="top">
                        <el-icon size="14" :class="{ rated: m.rating === -1 }" @click="rate(m, -1)"><svg viewBox="0 0 1024 1024" width="1em" height="1em" fill="currentColor"><path d="M885.9 533.7c-78.3-40.7-161.5-51.8-161.5-51.8 22.3-56.5 24.9-121.1 6.5-179.6-21.6-69.4-72.3-127.9-138.5-160.1-13.2-6.5-28.9-3.4-38.7 7.3L448.4 283.2 341.1 149.5c-9.8-10.7-25.5-13.8-38.7-7.3-66.2 32.2-116.9 90.7-138.5 160.1-18.4 58.5-15.8 123.1 6.5 179.6 0 0-83.2 11.1-161.5 51.8-29 15.1-40.2 51-25 80 40.7 78.3 123.9 129.5 212.4 129.5h415.4c88.5 0 171.7-51.2 212.4-129.5 15.2-29 4-64.9-25-80z"/></svg></el-icon>
                      </el-tooltip>
                      <span v-if="m.rating" class="rated-tag">{{ m.rating === 1 ? '已赞' : '已踩' }}</span>
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

          <!-- 输入区（T-409：工具条 + 快捷指令 + 字数统计） -->
          <div class="chat-input-area">
            <div class="input-box">
              <div class="input-toolbar">
                <el-tooltip content="上传文档问答（PDF/Word/TXT）" placement="top">
                  <el-button size="small" text :loading="uploadingDoc" @click="docInputRef?.click()">
                    <el-icon><Paperclip /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="图片识别（AI 看图，≤4 张）" placement="top">
                  <el-button size="small" text :loading="visionBusy" @click="imageInputRef?.click()">
                    <el-icon><Picture /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="读屏：让 AI 看见当前页面内容" placement="top">
                  <el-button size="small" text :loading="screenBusy" @click="runScreenRead">
                    <el-icon><View /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip v-if="listenAvailable" :content="listening ? '停止语音输入' : '语音输入（说完停顿自动断句）'" placement="top">
                  <el-button size="small" text :class="{ rec: listening }" @click="toggleListen">
                    <el-icon><Microphone /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-dropdown trigger="click" @command="useQuickCommand">
                  <el-button size="small" text><el-icon><Operation /></el-icon><span class="quick-label">快捷指令</span></el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="q in QUICK_COMMANDS" :key="q" :command="q">{{ q }}</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <input ref="docInputRef" type="file" accept=".pdf,.doc,.docx,.txt" hidden
                       @change="onDocSelected" />
                <input ref="imageInputRef" type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple hidden
                       @change="onImagesSelected" />
                <!-- 待识别图片预览（E1 眼睛） -->
                <div v-if="visionFiles.length" class="vision-chips">
                  <div v-for="(vf, i) in visionFiles" :key="i" class="vision-chip">
                    <img :src="vf.dataUrl" alt="" />
                    <span class="vc-name">{{ vf.name }}</span>
                    <el-icon size="12" class="vc-del" @click="visionFiles.splice(i, 1)"><Close /></el-icon>
                  </div>
                  <el-button size="small" type="primary" plain class="vc-run" :loading="visionBusy" @click="runVision">
                    开始识别
                  </el-button>
                </div>
              </div>
              <el-input v-model="input" type="textarea" :rows="1" :autosize="{ minRows: 1, maxRows: 6 }"
                        :placeholder="listening ? '正在聆听…' : '输入消息，Enter 发送，Shift+Enter 换行'"
                        @keydown="onInputKeydown" :disabled="pending" maxlength="4000" show-word-limit />
              <div v-if="listening && interim" class="rec-hint">{{ interim }}</div>
              <div class="input-actions">
                <span class="char-count" :class="{ warn: input.length > 3600 }">{{ input.length }}/4000</span>
                <el-tooltip v-if="ttsOk" content="自动播报 AI 回复（E2 嘴巴）" placement="top">
                  <el-button size="small" text :class="{ 'auto-on': autoSpeak }" @click="toggleAutoSpeak">
                    <el-icon><Service /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-button type="primary" class="send-btn" :disabled="!input.trim() || pending" :loading="pending"
                           @click="send">
                  <el-icon v-if="!pending"><Promotion /></el-icon>
                </el-button>
              </div>
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
import { computed, nextTick, onActivated, onDeactivated, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Back, ChatDotRound, Close, Cpu, Delete, DocumentCopy, Download, Edit, Fold, Expand, MagicStick,
  Microphone, Operation, Paperclip, Picture, Plus, Promotion, RefreshRight, Search, Service,
  SetUp, Top, VideoPause, VideoPlay, View
} from '@element-plus/icons-vue'
import { renderMarkdown } from '@/utils/sanitize'
import TaskCard from './components/TaskCard.vue'
import HermesCard from './components/HermesCard.vue'
import WorkflowPanel from './components/WorkflowPanel.vue'
import {
  chatApi, createConversationApi, deleteConversationApi, describeImageApi, listConversationsApi,
  listMessagesApi, pinConversationApi, rateMessageApi, regenerateApi, renameConversationApi,
  type AiConversation, type AiMessage
} from '@/api/aiws'
import { listWorkflowsApi, type WfWorkflow } from '@/api/aiws'
import { getMyTasksApi, getAutonomyPoliciesApi, type AgentTask } from '@/api/agent'
import { uploadContractDocApi } from '@/api/contract'
import { collectScreenDigest } from '@/utils/screenReader'
import {
  createRecognizer, loadAutoSpeak, saveAutoSpeak, speak, stopSpeaking, ttsAvailable
} from '@/utils/speech'

const SUGGESTIONS = [
  '检索知识库：项目验收流程规定',
  '帮我生成今日日报摘要',
  '创建一个「设备采购审批」流程',
  '查一下本月项目交付情况'
]

/** 快捷指令（T-409 对齐豆包 "/" 指令） */
const QUICK_COMMANDS = [
  '检索知识库：',
  '帮我生成今日日报摘要',
  '总结 documentId= 的文档内容',
  '创建一个「XX」流程'
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
/** 能力自主级别（T-110 渐进自主：1建议/2自动/3批量自动，is_never 红线） */
const LEVEL_LABEL: Record<string, string> = { '1': '建议级', '2': '自动级', '3': '批量自动', '0': '红线封顶' }
const LEVEL_TYPE: Record<string, TagType> = { '1': 'warning', '2': 'primary', '3': 'success', '0': 'danger' }

// ==================== 状态 ====================
const collapsed = ref(false)
const searchKey = ref('')
const sideTab = ref<'chat' | 'capability' | 'workflow' | 'task'>('chat')
const mainView = ref<'chat' | 'workflow'>('chat')
const conversations = ref<AiConversation[]>([])
const workflows = ref<WfWorkflow[]>([])
const agentTasks = ref<AgentTask[]>([])
const capabilities = ref<{ capabilityCode: string; capabilityName?: string; level?: number | string }[]>([])
const activeConvId = ref<number | null>(null)
const messages = ref<AiMessage[]>([])
const input = ref('')
const pending = ref(false)
const pendingText = ref('思考中')
const chatScrollRef = ref()
const taskDrawer = ref(false)
const drawerTaskId = ref<number | null>(null)
const workflowPanelRef = ref()
const docInputRef = ref<HTMLInputElement>()
const uploadingDoc = ref(false)
// E1 眼睛：图片识别 + 读屏
const imageInputRef = ref<HTMLInputElement>()
const visionBusy = ref(false)
const screenBusy = ref(false)
const visionFiles = ref<{ name: string; dataUrl: string }[]>([])

// E3 耳朵：语音输入（能力探测，不支持则隐藏麦克风）
const listening = ref(false)
const interim = ref('')
const recognizer = createRecognizer(
  (text) => {
    input.value = (input.value ? input.value.replace(/\s+$/, '') + (input.value.endsWith('。') || input.value.endsWith('？') ? '\n' : '') : '') + text
    interim.value = ''
    focusInput()
  },
  (t) => { interim.value = t }
)
const listenAvailable = recognizer.available

function toggleListen() {
  if (listening.value) {
    recognizer.stop()
    listening.value = false
  } else {
    recognizer.start()
    listening.value = true
    ElMessage.info('语音输入已开启，再次点击麦克风结束')
  }
}

// E2 嘴巴：朗读 + 自动播报（偏好持久化）
const ttsOk = ttsAvailable
const speakingId = ref<number | null>(null)
const autoSpeak = ref(loadAutoSpeak())

function toggleSpeak(m: AiMessage) {
  if (speakingId.value === m.id) {
    stopSpeaking()
    speakingId.value = null
    return
  }
  const ok = speak(m.content, () => { speakingId.value = null })
  if (ok) speakingId.value = m.id
}

function toggleAutoSpeak() {
  autoSpeak.value = !autoSpeak.value
  saveAutoSpeak(autoSpeak.value)
  if (!autoSpeak.value) { stopSpeaking(); speakingId.value = null }
  ElMessage.success(autoSpeak.value ? '已开启自动播报' : '已关闭自动播报')
}

const activeTitle = computed(() => conversations.value.find(c => c.id === activeConvId.value)?.title || '新对话')

/** 会话按 置顶/今天/昨天/近7天/更早 分组（T-409 对齐豆包） */
const groupedConversations = computed(() => {
  const kw = searchKey.value.trim().toLowerCase()
  const list = conversations.value.filter(c => !kw || c.title.toLowerCase().includes(kw))
  const groups: { label: string; items: AiConversation[] }[] = []
  const bucket = (label: string, item: AiConversation) => {
    let g = groups.find(x => x.label === label)
    if (!g) { g = { label, items: [] }; groups.push(g) }
    g.items.push(item)
  }
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfYesterday = startOfToday - 86400000
  const startOf7d = startOfToday - 6 * 86400000
  for (const c of list) {
    if (c.pinned) { bucket('置顶', c); continue }
    const t = new Date((c.lastMsgTime || c.createTime || '').replace(' ', 'T')).getTime()
    if (t >= startOfToday) bucket('今天', c)
    else if (t >= startOfYesterday) bucket('昨天', c)
    else if (t >= startOf7d) bucket('近 7 天', c)
    else bucket('更早', c)
  }
  return groups.filter(g => g.items.length)
})

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

async function loadCapabilities() {
  try {
    const res = await getAutonomyPoliciesApi()
    capabilities.value = (res.data || []).map((p: any) => ({
      capabilityCode: p.capabilityCode, capabilityName: p.capabilityName, level: p.level
    }))
  } catch { /* 能力列表失败不阻塞 */ }
}

function refreshSide() {
  loadConversations()
  loadWorkflows()
  loadAgentTasks()
  loadCapabilities()
}

onActivated(refreshSide)
// 离开工作台：停止语音输入与朗读（E2/E3 外设收尾）
onDeactivated(() => {
  if (listening.value) { recognizer.stop(); listening.value = false }
  stopSpeaking()
  speakingId.value = null
})
onUnmounted(() => {
  if (listening.value) { recognizer.stop(); listening.value = false }
  stopSpeaking()
})

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

async function togglePin(c: AiConversation) {
  await pinConversationApi(c.id, !c.pinned)
  c.pinned = c.pinned ? 0 : 1
  loadConversations()
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

// ==================== 能力广场 ====================

/** 从能力广场发起：新建会话并预填指令（用户可补充参数后发送） */
async function useCapability(cap: { capabilityCode: string; capabilityName?: string }) {
  if (!activeConvId.value) await newConversation()
  mainView.value = 'chat'
  input.value = `请执行能力 ${cap.capabilityCode}（${cap.capabilityName || ''}）：`
  focusInput()
}

// ==================== 对话 ====================

function startWith(s: string) {
  input.value = s
  send()
}

async function send() {
  const content = input.value.trim()
  if (!content || pending.value) return
  if (!activeConvId.value) {
    const res = await createConversationApi()
    if (!res.data) return
    activeConvId.value = res.data.id
    messages.value = []
    await loadConversations()
  }
  input.value = ''
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
      messages.value.splice(messages.value.indexOf(tempUser), 1, res.data.userMessage, res.data.assistantMessage)
      // E2 嘴巴：自动播报开启时朗读新回复（仅文本消息）
      if (autoSpeak.value && res.data.assistantMessage.msgType === 'text') {
        const ok = speak(res.data.assistantMessage.content, () => { speakingId.value = null })
        if (ok) speakingId.value = res.data.assistantMessage.id
      }
    }
    refreshSide()
  } catch (e) {
    pending.value = false
    messages.value.splice(messages.value.indexOf(tempUser), 1)
  }
  scrollBottom()
}

/** 重新生成（T-409 对齐豆包重试）：仅最后一条 AI 回复可重试 */
async function regenerate() {
  if (!activeConvId.value || pending.value) return
  pending.value = true
  pendingText.value = '正在重新生成…'
  scrollBottom()
  try {
    const res = await regenerateApi(activeConvId.value)
    pending.value = false
    if (res.data?.assistantMessage) {
      // 服务端已删除原回复：本地替换最后一条 assistant 消息
      for (let i = messages.value.length - 1; i >= 0; i--) {
        if (messages.value[i].role === 'assistant') { messages.value.splice(i, 1, res.data.assistantMessage); break }
      }
      loadAgentTasks()
      loadWorkflows()
    }
  } catch {
    pending.value = false
  }
  scrollBottom()
}

function isLastAssistant(m: AiMessage): boolean {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'assistant') return messages.value[i].id === m.id
  }
  return false
}

async function rate(m: AiMessage, v: 1 | -1) {
  const next = m.rating === v ? 0 : v
  await rateMessageApi(m.id, next as 1 | -1 | 0)
  m.rating = next === 0 ? null : next
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

/** 导出当前对话为 Markdown（T-409 对齐豆包分享/导出） */
function exportMd() {
  const lines: string[] = [`# ${activeTitle.value}`, '', `> 导出时间：${new Date().toLocaleString()}`, '']
  for (const m of messages.value) {
    lines.push(m.role === 'user' ? `**用户**：` : `**AI 助手**：`, '', m.content, '', '---', '')
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${activeTitle.value}.md`
  a.click()
  URL.revokeObjectURL(a.href)
  ElMessage.success('已导出 Markdown')
}

async function copyConversation() {
  const text = messages.value
    .map(m => (m.role === 'user' ? `用户：${m.content}` : `AI：${m.content}`))
    .join('\n\n')
  await copyText(text)
}

// ==================== 附件文档问答（T-409 对齐豆包文件上传） ====================

async function onDocSelected(ev: Event) {
  const files = (ev.target as HTMLInputElement).files
  const file = files?.[0]
  ;(ev.target as HTMLInputElement).value = ''
  if (!file) return
  uploadingDoc.value = true
  try {
    const res = await uploadContractDocApi(file, file.name)
    const docId = res.data?.id
    if (!docId) throw new Error('上传失败')
    ElMessage.success('文档已上传知识库，正在发起摘要…')
    input.value = `总结 documentId=${docId} 的文档内容`
    await send()
  } catch {
    ElMessage.error('文档上传失败，请重试')
  } finally {
    uploadingDoc.value = false
  }
}

function useQuickCommand(q: string) {
  input.value = q.endsWith('：') || q.endsWith('=') ? q : q + ' '
  focusInput()
}

// ==================== E1 眼睛：图片识别 + 读屏（V2.0） ====================

/** 文件 → data URL（超大图 canvas 降采样到 ≤1568px，PNG 截图保真/JPEG 照片压缩） */
function fileToDataUrl(file: File, maxDim = 1568): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(new Error('读取失败'))
    reader.onload = () => {
      const raw = String(reader.result)
      const img = new Image()
      img.onerror = () => resolve(raw)
      img.onload = () => {
        if (img.width <= maxDim && img.height <= maxDim) { resolve(raw); return }
        const scale = maxDim / Math.max(img.width, img.height)
        const canvas = document.createElement('canvas')
        canvas.width = Math.round(img.width * scale)
        canvas.height = Math.round(img.height * scale)
        canvas.getContext('2d')?.drawImage(img, 0, 0, canvas.width, canvas.height)
        const isPng = file.type === 'image/png'
        resolve(canvas.toDataURL(isPng ? 'image/png' : 'image/jpeg', 0.85))
      }
      img.src = raw
    }
    reader.readAsDataURL(file)
  })
}

async function onImagesSelected(ev: Event) {
  const files = Array.from((ev.target as HTMLInputElement).files || [])
  ;(ev.target as HTMLInputElement).value = ''
  if (!files.length) return
  if (visionFiles.value.length + files.length > 4) {
    ElMessage.warning('单次最多 4 张图片')
    return
  }
  for (const f of files) {
    try {
      visionFiles.value.push({ name: f.name, dataUrl: await fileToDataUrl(f) })
    } catch {
      ElMessage.error(`图片 ${f.name} 读取失败`)
    }
  }
}

/** 视觉识别：把待识别图片发给网关视觉通道，描述文本注入输入框 */
async function runVision() {
  if (!visionFiles.value.length || visionBusy.value) return
  visionBusy.value = true
  try {
    const res = await describeImageApi({ images: visionFiles.value.map(v => v.dataUrl) })
    const desc = res.data
    visionFiles.value = []
    if (desc) {
      input.value = `【图片识别】${desc}\n\n请基于以上图片内容继续回答：`
      focusInput()
      ElMessage.success('图片识别完成，已注入输入框')
    } else {
      ElMessage.warning('未获得识别结果')
    }
  } catch {
    ElMessage.error('图片识别失败（视觉通道未启用或服务异常）')
  } finally {
    visionBusy.value = false
  }
}

/** 读屏：收集页面 [data-ai-readable] 区域摘要注入输入框 */
async function runScreenRead() {
  if (screenBusy.value) return
  screenBusy.value = true
  try {
    const digest = collectScreenDigest()
    if (!digest) {
      ElMessage.warning('当前页面无可读区域（页面未标注 data-ai-readable）')
      return
    }
    input.value = `【当前屏幕】${digest}\n\n请基于以上屏幕内容回答：`
    focusInput()
    ElMessage.success('已读取屏幕内容，请补充问题后发送')
  } finally {
    screenBusy.value = false
  }
}

// ==================== 输入 ====================

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

function focusInput() {
  nextTick(() => {
    const ta = document.querySelector('.input-box textarea') as HTMLTextAreaElement | null
    ta?.focus()
  })
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

/** Markdown 渲染（H3 整改：统一经 DOMPurify 白名单净化，杜绝存储型 XSS） */
function renderMd(src: string): string {
  return renderMarkdown(src)
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
  transition: width 0.2s;
  &.collapsed { width: 64px; }
}
.sidebar-head { padding: 12px; display: flex; gap: 8px; }
.new-conv-btn { flex: 1; border-radius: 8px; }
.icon-btn { border-radius: 8px; }
.side-search { padding: 0 12px 8px; }
.sidebar-tabs {
  flex: 1; min-height: 0; display: flex; flex-direction: column; padding: 0 8px;
  :deep(.el-tabs__header) { margin: 0 0 6px; }
  :deep(.el-tabs__nav-wrap::after) { height: 1px; }
  :deep(.el-tabs__content) { flex: 1; min-height: 0; }
  :deep(.el-tab-pane) { height: 100%; }
}
.side-list { height: 100%; }
.group-label {
  font-size: 11px; color: var(--el-text-color-secondary); padding: 8px 10px 4px;
  text-transform: uppercase; letter-spacing: 0.4px;
}
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
  .el-icon.pinned { color: var(--el-color-warning); opacity: 1; }
}

// 能力广场
.cap-item {
  display: flex; align-items: center; gap: 8px; padding: 9px 10px; border-radius: 8px;
  cursor: pointer; margin-bottom: 2px; border: 1px solid transparent;
  &:hover { background: var(--el-fill-color); border-color: var(--el-border-color-lighter); }
}
.cap-code {
  font-size: 11px; font-weight: 600; color: var(--el-color-primary);
  background: var(--el-color-primary-light-9); border-radius: 6px; padding: 2px 6px; flex-shrink: 0;
}
.cap-name {
  flex: 1; min-width: 0; font-size: 12px; color: var(--el-text-color-primary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.cap-level { flex-shrink: 0; }

// 折叠态图标栏
.collapsed-rail { display: flex; flex-direction: column; align-items: center; gap: 14px; padding-top: 8px; }
.rail-icon {
  font-size: 18px; color: var(--el-text-color-secondary); cursor: pointer; padding: 8px; border-radius: 8px;
  &:hover { background: var(--el-fill-color); color: var(--el-color-primary); }
  &.on { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
}

// ---------- 主区域 ----------
.ws-main { flex: 1; min-width: 0; display: flex; flex-direction: column; min-height: 0; }
.main-header {
  display: flex; align-items: center; gap: 10px; padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter); flex-shrink: 0;
}
.main-title { font-size: 14px; font-weight: 600; color: var(--el-text-color-primary); }
.header-actions { margin-left: auto; display: flex; gap: 4px; }
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

// 消息操作栏（hover 显示，T-409）
.msg-ops {
  display: flex; align-items: center; gap: 12px; margin-top: 6px; padding: 0 4px;
  opacity: 0; transition: opacity 0.15s;
  color: var(--el-text-color-secondary);
  .el-icon { cursor: pointer; &:hover { color: var(--el-color-primary); } }
  .el-icon.rated { color: var(--el-color-primary); }
  .el-icon.disabled { opacity: 0.4; cursor: not-allowed; }
}
.msg-row:hover .msg-ops { opacity: 1; }
.rated-tag { font-size: 11px; color: var(--el-text-color-secondary); }

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
  border: 1px solid var(--el-border-color); border-radius: 14px; padding: 6px 10px 8px;
  transition: border-color 0.15s;
  &:focus-within { border-color: var(--el-color-primary); }
  :deep(.el-textarea__inner) { box-shadow: none; padding: 4px 4px; font-size: 14px; }
}
.input-toolbar {
  display: flex; align-items: center; gap: 6px; padding: 2px 2px 4px;
  border-bottom: 1px dashed var(--el-border-color-lighter); margin-bottom: 4px;
  .quick-label { font-size: 12px; margin-left: 2px; }
}
.input-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 4px; }
.char-count { font-size: 11px; color: var(--el-text-color-secondary); &.warn { color: var(--el-color-danger); } }
.send-btn { height: 34px; width: 44px; border-radius: 10px; }
.input-tip { font-size: 11px; color: var(--el-text-color-secondary); margin-top: 6px; text-align: center; }

// E1 眼睛：待识别图片 chips
.vision-chips { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 4px 2px; }
.vision-chip {
  display: flex; align-items: center; gap: 6px; padding: 3px 8px 3px 3px;
  background: var(--el-fill-color); border: 1px solid var(--el-border-color-lighter); border-radius: 8px;
  img { width: 30px; height: 30px; object-fit: cover; border-radius: 5px; }
  .vc-name { font-size: 11px; color: var(--el-text-color-regular); max-width: 120px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .vc-del { cursor: pointer; color: var(--el-text-color-secondary); &:hover { color: var(--el-color-danger); } }
}
.vc-run { border-radius: 8px; }

// E3 耳朵：录音态脉冲 + 中间结果提示
.el-button.rec { color: var(--el-color-danger); position: relative;
  &::after {
    content: ''; position: absolute; inset: 4px; border-radius: 50%;
    border: 2px solid var(--el-color-danger); opacity: 0; animation: rec-pulse 1.4s infinite;
  }
}
@keyframes rec-pulse { 0% { opacity: 0.9; transform: scale(0.6); } 100% { opacity: 0; transform: scale(1.5); } }
.rec-hint {
  font-size: 12px; color: var(--el-color-danger); padding: 2px 4px 0;
  &::before { content: '●'; margin-right: 6px; animation: blink 1.2s infinite; }
}

// E2 嘴巴：自动播报开启态 + 朗读中高亮
.el-button.auto-on { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.el-icon.speaking { color: var(--el-color-primary); animation: blink 1.2s infinite; }

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
  .ws-sidebar { width: 200px; &.collapsed { width: 56px; } }
  .chat-list { padding: 12px; }
  .msg-body { max-width: 88%; }
  .welcome-chips { grid-template-columns: 1fr; }
}
</style>
