// 基于 fetch 的 SSE 解析器：支持 POST（EventSource 不支持），逐帧回调
import { getToken } from './client'

export interface SseFrame {
  id?: string
  event?: string
  data: string
}

export interface SseOptions {
  method?: string
  body?: any
  headers?: Record<string, string>
  signal?: AbortSignal
  onFrame: (frame: SseFrame) => void
  onClose?: () => void
}

export async function sseFetch(url: string, opts: SseOptions): Promise<void> {
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    Authorization: `Bearer ${getToken()}`,
    ...(opts.headers || {})
  }
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  const resp = await fetch(url, {
    method: opts.method || 'GET',
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    signal: opts.signal
  })
  if (!resp.ok || !resp.body) {
    let detail = ''
    try {
      const j = await resp.json()
      detail = j?.error?.message || ''
    } catch {
      /* ignore */
    }
    throw new Error(`SSE 连接失败(${resp.status})${detail ? '：' + detail : ''}`)
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      let idx: number
      // SSE 帧以空行分隔；行首 ':' 的注释帧（keepalive/open/closed）在解析时天然跳过
      while ((idx = buf.indexOf('\n\n')) >= 0) {
        const raw = buf.slice(0, idx)
        buf = buf.slice(idx + 2)
        const frame: SseFrame = { data: '' }
        const dataLines: string[] = []
        for (const line of raw.split('\n')) {
          if (line.startsWith(':')) continue
          if (line.startsWith('id:')) frame.id = line.slice(3).trim()
          else if (line.startsWith('event:')) frame.event = line.slice(6).trim()
          else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
        }
        frame.data = dataLines.join('\n')
        if (frame.data || frame.event) opts.onFrame(frame)
      }
    }
  } finally {
    opts.onClose?.()
  }
}
