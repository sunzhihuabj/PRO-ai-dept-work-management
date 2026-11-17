// chat/stream 逐帧到达时间探针：判断 SSE 是渐进到达还是整体缓冲
const B = 'http://127.0.0.1:8650'
const login = await fetch(`${B}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: 'admin', password: 'dept-studio-2026' }) }).then((r) => r.json())
const auth = { Authorization: `Bearer ${login.token}`, 'Content-Type': 'application/json' }

const title = `probe-${Date.now()}`
const s = await fetch(`${B}/api/hermes/api/sessions`, { method: 'POST', headers: auth, body: JSON.stringify({ title, source: 'studio' }) }).then((r) => r.json())
const sid = s?.session?.id || s?.id
console.log(`session=${sid}`)
const t0 = Date.now()
const resp = await fetch(`${B}/api/hermes/api/sessions/${sid}/chat/stream`, {
  method: 'POST', headers: auth, body: JSON.stringify({ message: '请只回复两个字：在线' })
})
console.log(`HTTP ${resp.status} @ +${((Date.now() - t0) / 1000).toFixed(1)}s`)
const reader = resp.body.getReader()
const dec = new TextDecoder()
let buf = '', n = 0
for (;;) {
  const { done, value } = await reader.read()
  if (done) break
  buf += dec.decode(value, { stream: true })
  let idx
  while ((idx = buf.indexOf('\n\n')) >= 0) {
    const frame = buf.slice(0, idx)
    buf = buf.slice(idx + 2)
    const evLine = frame.split('\n').find((l) => l.startsWith('event:'))
    if (evLine) {
      const at = ((Date.now() - t0) / 1000).toFixed(1)
      n++
      if (n <= 12 || evLine.includes('run.')) console.log(`  +${at}s  ${evLine.slice(6)}`)
    }
  }
}
console.log(`流结束，共 ${n} 帧，总耗时 ${((Date.now() - t0) / 1000).toFixed(1)}s`)
await fetch(`${B}/api/hermes/api/sessions/${sid}`, { method: 'DELETE', headers: auth })
console.log('探针会话已清理')
