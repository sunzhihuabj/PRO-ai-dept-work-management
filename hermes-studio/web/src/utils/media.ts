// 三感工具库：眼睛（图片降采样+多模态部件）、耳朵（语音识别）、嘴巴（语音合成）

// ---------- 眼睛：图片 → 降采样 dataURL ----------
// Hermes 多模态契约（api_server.py:_normalize_image_part）：data:image/* 或 http(s) URL；
// body 上限 10MB，因此大图先经 canvas 降采样（截图类保 PNG，照片类保 JPEG）
export interface PreparedImage {
  dataUrl: string
  name: string
  width: number
  height: number
}

const MAX_EDGE = 1568 // 对齐主流视觉模型输入分辨率习惯

export async function prepareImage(file: File | Blob, fallbackName = 'image'): Promise<PreparedImage> {
  if (file instanceof File && !file.type.startsWith('image/')) {
    throw new Error(`不支持的文件类型：${file.type || file.name || '未知'}（仅支持图片）`)
  }
  const bitmap = await createImageBitmap(file)
  try {
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height))
    const w = Math.max(1, Math.round(bitmap.width * scale))
    const h = Math.max(1, Math.round(bitmap.height * scale))
    const isPng = file instanceof File ? file.type === 'image/png' : true
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, w, h)
    // 小图或 PNG（截图需要保真）走 PNG；大照片走 JPEG 85
    const mime = isPng && scale === 1 ? 'image/png' : 'image/jpeg'
    const quality = mime === 'image/jpeg' ? 0.85 : undefined
    const dataUrl = canvas.toDataURL(mime, quality)
    return { dataUrl, name: file instanceof File ? file.name : fallbackName, width: w, height: h }
  } finally {
    bitmap.close()
  }
}

/** 组装 Hermes 多模态 message：有图时为部件数组，纯文本退化为字符串 */
export function buildChatMessage(text: string, images: PreparedImage[]): string | any[] {
  const trimmed = text.trim()
  if (!images.length) return trimmed
  const parts: any[] = []
  if (trimmed) parts.push({ type: 'text', text: trimmed })
  for (const img of images) {
    parts.push({ type: 'image_url', image_url: { url: img.dataUrl } })
  }
  return parts
}

// ---------- 耳朵：Web Speech API 语音识别 ----------
export interface RecognitionHandle {
  stop: () => void
}

export function speechRecognitionSupported(): boolean {
  const w = window as any
  return !!(w.SpeechRecognition || w.webkitSpeechRecognition)
}

export function startRecognition(
  onText: (finalText: string, interimText: string) => void,
  onEnd: (error?: string) => void
): RecognitionHandle | null {
  const w = window as any
  const SR = w.SpeechRecognition || w.webkitSpeechRecognition
  if (!SR) return null
  const rec = new SR()
  rec.lang = 'zh-CN'
  rec.continuous = true
  rec.interimResults = true
  let finalText = ''
  let stopped = false
  rec.onresult = (event: any) => {
    let interim = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const r = event.results[i]
      if (r.isFinal) finalText += r[0].transcript
      else interim += r[0].transcript
    }
    onText(finalText, interim)
  }
  rec.onerror = (e: any) => {
    if (!stopped) onEnd(e?.error || 'recognition_error')
  }
  rec.onend = () => {
    if (!stopped) onEnd()
  }
  try {
    rec.start()
  } catch (e: any) {
    onEnd(e?.message || 'start_failed')
    return null
  }
  return {
    stop: () => {
      stopped = true
      try {
        rec.stop()
      } catch {
        /* ignore */
      }
    }
  }
}

// ---------- 嘴巴：语音合成 ----------
export function ttsSupported(): boolean {
  return 'speechSynthesis' in window
}

/** 取中文优先的语音（Chromium 自带本地 zh 声音或系统装的声音） */
function pickVoice(): SpeechSynthesisVoice | null {
  const voices = speechSynthesis.getVoices()
  return (
    voices.find((v) => v.lang?.toLowerCase().startsWith('zh-cn')) ||
    voices.find((v) => v.lang?.toLowerCase().startsWith('zh')) ||
    voices[0] ||
    null
  )
}

/** 剥掉 Markdown 语法，只留适合朗读的文本 */
export function speakableText(md: string): string {
  return (md || '')
    .replace(/```[\s\S]*?```/g, '（代码块略）')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/[*_~|#>]+/g, '')
    .replace(/\n{2,}/g, '。')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 1200)
}

export function speak(text: string, onEnd?: () => void): boolean {
  if (!ttsSupported()) return false
  const clean = speakableText(text)
  if (!clean) return false
  // 声音包可能异步加载（首次 getVoices() 为空，尤其内嵌/无头 Chromium），最多等 800ms
  const start = () => {
    const voice = pickVoice()
    if (!voice && !speechSynthesis.getVoices().length) return false
    speechSynthesis.cancel()
    const utter = new SpeechSynthesisUtterance(clean)
    if (voice) utter.voice = voice
    utter.lang = voice?.lang || 'zh-CN'
    utter.rate = 1.05
    if (onEnd) utter.onend = () => onEnd()
    speechSynthesis.speak(utter)
    return true
  }
  if (speechSynthesis.getVoices().length) return start()
  return new Promise<boolean>((resolve) => {
    const timer = setTimeout(() => resolve(start()), 800)
    speechSynthesis.onvoiceschanged = () => {
      clearTimeout(timer)
      resolve(start())
    }
  }) as unknown as boolean
}

export function stopSpeaking() {
  if (ttsSupported()) speechSynthesis.cancel()
}

export function isSpeaking(): boolean {
  return ttsSupported() && speechSynthesis.speaking
}
