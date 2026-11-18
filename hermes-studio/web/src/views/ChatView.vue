<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  chatStream, createSession, deleteSession, getSessionMessages, listSessions, HermesSession
} from '../api/hermes'
import MarkdownView from '../components/MarkdownView.vue'
import ApprovalCard from '../components/ApprovalCard.vue'
import { useRuntimeStore } from '../stores/runtime'
import {
  PreparedImage, prepareImage, buildChatMessage,
  speechRecognitionSupported, startRecognition, RecognitionHandle,
  ttsSupported, speak, stopSpeaking, isSpeaking
} from '../utils/media'

const runtime = useRuntimeStore()
const route = useRoute()

// ---------- 会话侧栏 ----------
const sessions = ref<HermesSession[]>([])
const sessionsLoading = ref(false)
const activeId = ref('')
const creating = ref(false)

async function refreshSessions() {
  sessionsLoading.value = true
  try {
    const data = await listSessions({ limit: 100 })
    sessions.value = data?.data || []
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    sessionsLoading.value = false
  }
}

async function newSession() {
  creating.value = true
  try {
    const { value } = await ElMessageBox.prompt('会话标题（留空自动生成）', '新建会话', { inputValue: '' })
    const created = await createSession({ title: value || undefined, source: 'studio' })
    await refreshSessions()
    activeId.value = created?.id || created?.session_id || created?.session?.id || ''
    await loadHistory()
    ElMessage.success('会话已创建')
  } catch {
    /* 取消 */
  } finally {
    creating.value = false
  }
}

async function removeSession(s: HermesSession) {
  await ElMessageBox.confirm(`删除会话 ${s.id}？（Hermes 侧硬删除，含全部消息）`, '危险操作', { type: 'warning' })
  await deleteSession(s.id)
  if (activeId.value === s.id) activeId.value = ''
  await refreshSessions()
  ElMessage.success('已删除')
}

function fmtLast(ts?: number) {
  if (!ts) return ''
  const ms = ts < 1e12 ? ts * 1000 : ts
  return new Date(ms).toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// ---------- 聊天主体 ----------
interface ChatMsg {
  role: 'user' | 'assistant'
  content: string
  images?: string[] // dataURL 缩略展示（用户消息附带）
  tools: { name: string; status: 'running' | 'ok' | 'failed'; preview?: string }[]
  approval?: any
  usage?: any
  error?: string
}

const history = ref<ChatMsg[]>([])
const live = ref<ChatMsg | null>(null) // 正在流式生成中的一轮
const input = ref('')
const streaming = ref(false)
const loadingHistory = ref(false)
const runId = ref('') // run.started 事件记录，供审批卡定位 run
let aborter: AbortController | null = null
const scrollBox = ref<HTMLElement>()

// ---------- 眼睛：图片附件 ----------
const attached = ref<PreparedImage[]>([])
const fileInput = ref<HTMLInputElement>()
const dragOver = ref(false)

function pickImage() {
  fileInput.value?.click()
}

async function onFilesChosen(ev: Event) {
  const input_ = ev.target as HTMLInputElement
  await absorbFiles(input_.files)
  input_.value = ''
}

async function absorbFiles(files: FileList | File[] | null) {
  if (!files?.length) return
  for (const f of Array.from(files).slice(0, 4)) {
    try {
      attached.value.push(await prepareImage(f))
    } catch (e: any) {
      ElMessage.error(e?.message || '图片处理失败')
    }
  }
}

function removeImage(i: number) {
  attached.value.splice(i, 1)
}

function onPaste(ev: ClipboardEvent) {
  const items = ev.clipboardData?.items
  if (!items) return
  const imgs: File[] = []
  for (const it of items) {
    if (it.type.startsWith('image/')) {
      const f = it.getAsFile()
      if (f) imgs.push(f)
    }
  }
  if (imgs.length) {
    ev.preventDefault()
    absorbFiles(imgs)
  }
}

function onDrop(ev: DragEvent) {
  dragOver.value = false
  const files = ev.dataTransfer?.files
  if (files?.length) absorbFiles(files)
}

// ---------- 耳朵：语音输入 ----------
const listening = ref(false)
const listeningInterim = ref('')
let recHandle: RecognitionHandle | null = null

const micSupported = speechRecognitionSupported()

function toggleMic() {
  if (listening.value) {
    recHandle?.stop()
    return
  }
  const base = input.value ? input.value + ' ' : ''
  recHandle = startRecognition(
    (finalText, interim) => {
      input.value = base + finalText + interim
      listeningInterim.value = interim
    },
    (err) => {
      listening.value = false
      listeningInterim.value = ''
      if (err === 'not-allowed') ElMessage.error('麦克风权限被拒绝，请在浏览器地址栏允许麦克风')
      else if (err && err !== 'aborted') ElMessage.warning(`语音识别中断：${err}`)
    }
  )
  if (recHandle) {
    listening.value = true
    ElMessage.info('开始聆听…再点一次麦克风结束')
  } else {
    ElMessage.warning('当前浏览器不支持语音识别')
  }
}

// ---------- 嘴巴：语音播报 ----------
const autoSpeak = ref(localStorage.getItem('hs-auto-speak') === '1')
const speakingKey = ref('')

function toggleAutoSpeak() {
  autoSpeak.value = !autoSpeak.value
  localStorage.setItem('hs-auto-speak', autoSpeak.value ? '1' : '0')
  if (!autoSpeak.value) {
    stopSpeaking()
    speakingKey.value = ''
  }
}

function toggleSpeak(msg: ChatMsg, key: string) {
  if (speakingKey.value === key && isSpeaking()) {
    stopSpeaking()
    speakingKey.value = ''
    return
  }
  const ok = speak(msg.content, () => {
    speakingKey.value = ''
  })
  if (ok) speakingKey.value = key
  else ElMessage.warning('当前浏览器不支持语音合成')
}

// ---------- 会话加载/发送 ----------
async function loadHistory() {
  if (!activeId.value) {
    history.value = []
    return
  }
  loadingHistory.value = true
  try {
    const data = await getSessionMessages(activeId.value, { limit: 200, order: 'oldest' })
    history.value = (data?.data || [])
      .filter((m: any) => (m.role === 'user' || m.role === 'assistant') && m.content)
      .map((m: any) => ({ role: m.role, content: typeof m.content === 'string' ? m.content : JSON.stringify(m.content), tools: [] }))
  } catch (e: any) {
    ElMessage.error(`加载历史失败：${e.message}`)
  } finally {
    loadingHistory.value = false
  }
}

async function pickSession(s: HermesSession) {
  if (streaming.value) return ElMessage.warning('正在流式回复中，请等待或中断后再切换')
  activeId.value = s.id
  await loadHistory()
}

function scrollBottom() {
  nextTick(() => scrollBox.value?.scrollTo({ top: scrollBox.value.scrollHeight }))
}

async function send() {
  const text = input.value.trim()
  const images = [...attached.value]
  if (!text && !images.length) return
  if (!activeId.value) return ElMessage.warning('请先选择或新建会话')
  if (streaming.value) return ElMessage.warning('正在回复中')

  history.value.push({
    role: 'user',
    content: text,
    images: images.map((i) => i.dataUrl),
    tools: []
  })
  input.value = ''
  attached.value = []
  if (listening.value) recHandle?.stop()
  live.value = { role: 'assistant', content: '', tools: [] }
  streaming.value = true
  runId.value = ''
  aborter = new AbortController()
  scrollBottom()

  try {
    await chatStream(
      activeId.value,
      { message: buildChatMessage(text, images) },
      (frame) => {
        let payload: any = null
        try {
          payload = frame.data ? JSON.parse(frame.data) : null
        } catch {
          payload = null
        }
        // chat/stream 事件名在 SSE event: 行（data JSON 无 event 字段，与 /v1/runs 不同）
        const ev = payload?.event || frame.event || ''
        const liveMsg = live.value!
        switch (ev) {
          case 'run.started':
            runId.value = payload.run_id || ''
            break
          case 'assistant.delta':
          case 'message.delta':
            liveMsg.content += payload?.delta || payload?.text || ''
            scrollBottom()
            break
          case 'assistant.commentary':
          case 'message.interim':
            if (payload?.text && !liveMsg.content.includes(payload.text)) liveMsg.content += (liveMsg.content ? '\n\n' : '') + payload.text
            break
          case 'tool.started':
            liveMsg.tools.push({ name: payload?.tool || '?', status: 'running', preview: payload?.preview })
            scrollBottom()
            break
          case 'tool.completed':
          case 'tool.progress': {
            const t = liveMsg.tools.find((x) => x.name === payload?.tool && x.status === 'running') || liveMsg.tools[liveMsg.tools.length - 1]
            if (t) {
              t.status = payload?.error ? 'failed' : 'ok'
              if (payload?.preview) t.preview = payload.preview
            }
            break
          }
          case 'tool.failed': {
            const t = liveMsg.tools.find((x) => x.status === 'running') || liveMsg.tools[liveMsg.tools.length - 1]
            if (t) t.status = 'failed'
            break
          }
          case 'approval.request':
            liveMsg.approval = payload
            runtime.upsertApproval({
              runId: runId.value || payload?.run_id || '',
              requestId: payload?.request_id || '',
              command: payload?.command || '',
              description: payload?.description || '',
              choices: payload?.choices || ['once', 'deny'],
              source: 'chat',
              createdAt: Date.now()
            })
            scrollBottom()
            break
          case 'assistant.completed':
            if (payload?.content) liveMsg.content = payload.content
            break
          case 'run.completed':
            if (payload?.output) liveMsg.content = payload.output
            liveMsg.usage = payload?.usage
            break
          case 'run.failed':
          case 'run.cancelled':
          case 'run.interrupted':
            liveMsg.error = payload?.error || `run 终态：${ev}`
            break
          case 'done':
          case 'error':
            if (ev === 'error') liveMsg.error = payload?.message || '流错误'
            break
          default:
            break
        }
      },
      aborter.signal
    )
  } catch (e: any) {
    if (live.value && !e.message?.includes('abort')) live.value.error = e.message || '流异常'
  } finally {
    if (live.value && (live.value.content || live.value.error || live.value.tools.length)) {
      history.value.push(live.value)
      // 嘴巴：自动播报本轮回复（静默失败）
      if (autoSpeak.value && live.value.content && !live.value.error) {
        speak(live.value.content)
      }
    }
    live.value = null
    streaming.value = false
    aborter = null
    scrollBottom()
    refreshSessions()
  }
}

function stopStream() {
  aborter?.abort('用户中断')
}

function toolIcon(status: string) {
  return status === 'running' ? 'Loading' : status === 'failed' ? 'CircleClose' : 'CircleCheck'
}

onMounted(async () => {
  await refreshSessions()
  // 支持 /chat?session=<id> 直接续聊（来自会话管理页）
  const wanted = route.query.session as string
  if (wanted && sessions.value.some((s) => s.id === wanted)) {
    activeId.value = wanted
    await loadHistory()
  }
})
</script>

<template>
  <div class="chat-wrap">
    <aside class="chat-side">
      <div style="padding: 10px 12px; border-bottom: 1px solid var(--hs-border); display: flex; gap: 8px">
        <el-button type="primary" size="small" style="flex: 1" :loading="creating" data-testid="new-session" @click="newSession">
          <el-icon><Plus /></el-icon>新建会话
        </el-button>
        <el-button size="small" circle :loading="sessionsLoading" @click="refreshSessions">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
      <div class="chat-scroll" style="padding: 8px">
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === activeId }"
          @click="pickSession(s)"
        >
          <div class="session-title">{{ s.title || s.preview || s.id }}</div>
          <div class="muted">
            {{ s.message_count }} 条 · {{ fmtLast(s.last_active) }}
            <el-icon class="session-del" @click.stop="removeSession(s)"><Delete /></el-icon>
          </div>
        </div>
        <div v-if="!sessions.length" class="muted" style="padding: 16px; text-align: center">暂无会话</div>
      </div>
    </aside>

    <section class="chat-main" :class="{ 'drag-over': dragOver }" @dragover.prevent="dragOver = true" @dragleave="dragOver = false" @drop.prevent="onDrop">
      <template v-if="activeId">
        <div class="chat-toolbar">
          <el-button size="small" :type="autoSpeak ? 'primary' : 'default'" round @click="toggleAutoSpeak">
            <el-icon><Bell /></el-icon>自动播报{{ autoSpeak ? '开' : '关' }}
          </el-button>
          <span class="muted">可拖拽/粘贴/上传图片（眼睛）· 麦克风语音输入（耳朵）· 朗读回复（嘴巴）</span>
        </div>

        <div ref="scrollBox" class="chat-scroll" data-testid="chat-scroll">
          <div v-loading="loadingHistory">
            <div v-for="(m, i) in history" :key="i" class="chat-msg" :class="m.role">
              <div class="bubble">
                <div v-if="m.images?.length" class="msg-images">
                  <el-image
                    v-for="(src, j) in m.images"
                    :key="j"
                    :src="src"
                    :preview-src-list="m.images"
                    fit="cover"
                    class="msg-image-thumb"
                  />
                </div>
                <MarkdownView v-if="m.role === 'assistant'" :content="m.content" />
                <template v-else>{{ m.content }}</template>
                <div v-if="m.tools?.length" style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 6px">
                  <el-tag v-for="(t, j) in m.tools" :key="j" size="small" :type="t.status === 'failed' ? 'danger' : t.status === 'running' ? 'warning' : 'info'" effect="plain">
                    <el-icon class="rotating" v-if="t.status === 'running'"><component :is="toolIcon(t.status)" /></el-icon>
                    <el-icon v-else><component :is="toolIcon(t.status)" /></el-icon>
                    {{ t.name }}
                  </el-tag>
                </div>
                <div v-if="m.usage" class="muted" style="margin-top: 6px">
                  tokens：入 {{ m.usage.input_tokens ?? m.usage.prompt_tokens ?? '-' }} / 出 {{ m.usage.output_tokens ?? m.usage.completion_tokens ?? '-' }}
                </div>
                <el-alert v-if="m.error" :title="m.error" type="error" :closable="false" style="margin-top: 8px" />
                <el-icon
                  v-if="m.role === 'assistant' && m.content && ttsSupported()"
                  class="speak-btn"
                  :class="{ speaking: speakingKey === 'h' + i }"
                  :title="speakingKey === 'h' + i ? '停止朗读' : '朗读这条回复'"
                  @click="toggleSpeak(m, 'h' + i)"
                ><component :is="speakingKey === 'h' + i ? 'VideoPause' : 'Service'" /></el-icon>
              </div>
            </div>

            <div v-if="live" class="chat-msg assistant">
              <div class="bubble">
                <MarkdownView v-if="live.content" :content="live.content" />
                <span v-else class="muted">思考中…</span>
                <div v-if="live.tools?.length" style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 6px">
                  <el-tag v-for="(t, j) in live.tools" :key="j" size="small" :type="t.status === 'failed' ? 'danger' : t.status === 'running' ? 'warning' : 'info'" effect="plain">
                    {{ t.name }}{{ t.status === 'running' ? ' …' : '' }}
                  </el-tag>
                </div>
                <ApprovalCard v-if="live.approval && runId" :run-id="runId" :payload="live.approval" />
                <div v-if="live.usage" class="muted" style="margin-top: 6px">
                  tokens：入 {{ live.usage.input_tokens ?? '-' }} / 出 {{ live.usage.output_tokens ?? '-' }}
                </div>
                <el-alert v-if="live.error" :title="live.error" type="error" :closable="false" style="margin-top: 8px" />
              </div>
            </div>
          </div>
        </div>

        <div v-if="attached.length" class="attach-row">
          <div v-for="(img, i) in attached" :key="i" class="attach-thumb">
            <el-image :src="img.dataUrl" fit="cover" style="width: 44px; height: 44px; border-radius: 6px" />
            <el-icon class="attach-del" @click="removeImage(i)"><CircleCloseFilled /></el-icon>
            <span class="muted">{{ img.width }}×{{ img.height }}</span>
          </div>
        </div>

        <div class="chat-input-bar">
          <div class="sense-btns">
            <el-button size="small" circle title="上传图片（眼睛）" @click="pickImage"><el-icon><Picture /></el-icon></el-button>
            <el-button
              size="small"
              circle
              :type="listening ? 'danger' : 'default'"
              :title="micSupported ? (listening ? '停止聆听' : '语音输入（耳朵）') : '当前浏览器不支持语音识别'"
              :disabled="!micSupported"
              @click="toggleMic"
            >
              <el-icon><Microphone /></el-icon>
            </el-button>
          </div>
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            :placeholder="listening ? '聆听中…（说话即输入）' : '输入消息，Enter 发送 / Shift+Enter 换行；可粘贴或拖入图片'"
            data-testid="chat-input"
            :disabled="streaming"
            @keydown.enter.exact.prevent="send"
            @paste="onPaste"
          />
          <div style="display: flex; flex-direction: column; gap: 8px">
            <el-button type="primary" :loading="streaming" data-testid="chat-send" @click="send">
              {{ streaming ? '回复中' : '发送' }}
            </el-button>
            <el-button v-if="streaming" size="small" @click="stopStream">中断</el-button>
          </div>
        </div>
        <input ref="fileInput" type="file" accept="image/*" multiple style="display: none" @change="onFilesChosen" />
      </template>
      <el-empty v-else description="从左侧选择会话，或新建一个开始与 Hermes Agent 对话" style="margin: auto" />
    </section>
  </div>
</template>

<style scoped>
.session-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; margin-bottom: 4px; }
.session-item:hover { background: #f5f7fa; }
.session-item.active { background: #ecf5ff; }
.session-title { font-size: 13px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-del { float: right; color: #c0c4cc; }
.session-del:hover { color: #f56c6c; }
.rotating { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }

.chat-toolbar { padding: 8px 16px 0; display: flex; align-items: center; gap: 12px; }
.chat-main.drag-over { outline: 2px dashed var(--hs-primary); outline-offset: -4px; }
.msg-images { display: flex; gap: 6px; margin-bottom: 6px; flex-wrap: wrap; }
.msg-image-thumb { width: 72px; height: 72px; border-radius: 8px; }
.attach-row { border-top: 1px solid var(--hs-border); padding: 8px 16px 0; display: flex; gap: 10px; }
.attach-thumb { display: flex; align-items: center; gap: 4px; position: relative; padding-right: 40px; }
.attach-del { position: absolute; right: 24px; top: -2px; color: #f56c6c; cursor: pointer; }
.sense-btns { display: flex; flex-direction: column; gap: 6px; }
.speak-btn { float: right; margin-left: 10px; color: #c0c4cc; cursor: pointer; }
.speak-btn:hover { color: var(--hs-primary); }
.speak-btn.speaking { color: var(--hs-primary); animation: pulse 1s ease-in-out infinite; }
@keyframes pulse { 50% { opacity: 0.4; } }
</style>
