/**
 * E3 耳朵（语音输入 STT）+ E2 嘴巴（语音播报 TTS）—— V2.0 外设能力
 *
 * 基于浏览器 Web Speech API（Studio 已验证方案复用）：
 * - STT：webkitSpeechRecognition，zh-CN 连续识别 + 中间结果，能力探测自动降级
 * - TTS：speechSynthesis，中文声音优先，Markdown 剥离朗读
 * 能力不支持的浏览器返回 disabled 状态，UI 隐藏对应入口。
 */

// ==================== E3 耳朵：语音识别 ====================

/* eslint-disable @typescript-eslint/no-explicit-any */
type SpeechRecognitionLike = any

export function getSpeechRecognitionCtor(): (new () => SpeechRecognitionLike) | null {
  const w = window as any
  return w.SpeechRecognition || w.webkitSpeechRecognition || null
}

export interface SpeechRecState {
  listening: boolean
  interim: string
}

/**
 * 创建连续语音识别器（zh-CN）
 * @param onFinal  一句识别完成的回调（追加到输入框）
 * @param onInterim 中间结果回调（实时预览）
 */
export function createRecognizer(
  onFinal: (text: string) => void,
  onInterim?: (text: string) => void
): { start: () => void; stop: () => void; available: boolean } {
  const Ctor = getSpeechRecognitionCtor()
  if (!Ctor) return { start: () => {}, stop: () => {}, available: false }

  let rec: SpeechRecognitionLike | null = null
  let wantListening = false

  function build(): SpeechRecognitionLike {
    const Ctor2 = Ctor
    if (!Ctor2) throw new Error('SpeechRecognition unavailable')
    const r = new Ctor2()
    r.lang = 'zh-CN'
    r.continuous = true
    r.interimResults = true
    r.maxAlternatives = 1
    r.onresult = (ev: any) => {
      let interim = ''
      for (let i = ev.resultIndex; i < ev.results.length; i++) {
        const res = ev.results[i]
        const txt = res[0]?.transcript || ''
        if (res.isFinal) {
          const t = txt.trim()
          if (t) onFinal(t)
        } else {
          interim += txt
        }
      }
      onInterim?.(interim)
    }
    r.onend = () => {
      // 连续模式：浏览器自动断流时若用户仍想听，重启
      if (wantListening) {
        try { r.start() } catch { /* 重启竞态忽略 */ }
      }
    }
    r.onerror = (ev: any) => {
      if (ev?.error === 'not-allowed' || ev?.error === 'service-not-allowed') {
        wantListening = false
      }
    }
    return r
  }

  return {
    available: true,
    start() {
      if (!rec) rec = build()
      wantListening = true
      try { rec.start() } catch { /* already started */ }
    },
    stop() {
      wantListening = false
      try { rec?.stop() } catch { /* ignore */ }
      onInterim?.('')
    }
  }
}

// ==================== E2 嘴巴：语音播报 ====================

/** 剥离 Markdown 标记为可朗读文本（代码块/表格/图片/链接/强调标记） */
export function stripMarkdown(md: string): string {
  return md
    .replace(/```[\s\S]*?```/g, '（代码块略）')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^\s*#{1,6}\s+/gm, '')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/^\s*[-*+]\s+/gm, '')
    .replace(/^\s*\|.*\|\s*$/gm, (m) => m.replace(/\|/g, ' ').replace(/\s+/g, ' ').trim())
    .replace(/<!--[\s\S]*?-->/g, '')
    .trim()
}

let zhVoice: SpeechSynthesisVoice | null = null

/** 择优中文声音（本地包优先），声音包异步加载时二次选取 */
function pickVoice(): SpeechSynthesisVoice | null {
  if (zhVoice) return zhVoice
  const voices = window.speechSynthesis?.getVoices() || []
  if (!voices.length) return null
  zhVoice =
    voices.find(v => /zh[-_]CN/i.test(v.lang) && v.localService) ||
    voices.find(v => /zh/i.test(v.lang)) ||
    null
  return zhVoice
}

if (typeof window !== 'undefined' && window.speechSynthesis) {
  // 声音包异步加载：加载完成后预热选择
  window.speechSynthesis.onvoiceschanged = () => { zhVoice = null; pickVoice() }
}

export const ttsAvailable = typeof window !== 'undefined' && 'speechSynthesis' in window

/** 朗读一段文本（中文声音优先；已在本朗读时返回 false 供按钮切换停止） */
export function speak(text: string, onEnd?: () => void): boolean {
  if (!ttsAvailable) return false
  const synth = window.speechSynthesis
  if (synth.speaking) { synth.cancel(); return false }
  const clean = stripMarkdown(text).slice(0, 2000)
  if (!clean) return false
  synth.cancel()
  const u = new SpeechSynthesisUtterance(clean)
  u.lang = 'zh-CN'
  const v = pickVoice()
  if (v) u.voice = v
  u.rate = 1.05
  u.onend = () => onEnd?.()
  u.onerror = () => onEnd?.()
  synth.speak(u)
  return true
}

/** 停止朗读 */
export function stopSpeaking() {
  window.speechSynthesis?.cancel()
}

// ==================== 自动播报偏好（持久化） ====================

const AUTO_SPEAK_KEY = 'ai.autoSpeak'

export function loadAutoSpeak(): boolean {
  try { return localStorage.getItem(AUTO_SPEAK_KEY) === '1' } catch { return false }
}

export function saveAutoSpeak(on: boolean) {
  try { localStorage.setItem(AUTO_SPEAK_KEY, on ? '1' : '0') } catch { /* ignore */ }
}
