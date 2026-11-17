// Hermes Studio 全链路冒烟：登录 → 建会话 → SSE 流式聊天 → 清理
const B = 'http://127.0.0.1:8650'

const login = await fetch(`${B}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'dept-studio-2026' })
}).then((r) => r.json())
if (!login.token) throw new Error('登录失败: ' + JSON.stringify(login))
const auth = { Authorization: `Bearer ${login.token}`, 'Content-Type': 'application/json' }
console.log('[1] 登录 OK')

// 清理历史冒烟遗留
for (const wf of await fetch(`${B}/api/studio/workflows`, { headers: auth }).then((r) => r.json())) {
  if (String(wf.name).includes('冒烟测试流')) {
    await fetch(`${B}/api/studio/workflows/${wf.id}`, { method: 'DELETE', headers: auth })
    console.log(`[0] 清理遗留工作流 ${wf.id}`)
  }
}

// 清理历史冒烟会话（Hermes 会话标题唯一，重跑前先删）
for (const old of ['studio-smoke', 'shape-probe']) {
  const found = await fetch(`${B}/api/hermes/api/sessions?title=${old}&include_hidden=true`, { headers: auth }).then((r) => r.json())
  for (const s of found?.data || []) {
    await fetch(`${B}/api/hermes/api/sessions/${s.id}`, { method: 'DELETE', headers: auth })
    console.log(`[0] 清理遗留会话 ${s.id}(${old})`)
  }
}

const title = `studio-smoke-${Date.now()}`
const sess = await fetch(`${B}/api/hermes/api/sessions`, {
  method: 'POST', headers: auth,
  body: JSON.stringify({ title, source: 'studio' })
}).then((r) => r.json())
const sid = sess.id || sess.session_id || sess.session?.id
console.log(`[2] 会话创建 OK: ${sid}`)
if (!sid) throw new Error('会话创建响应异常: ' + JSON.stringify(sess).slice(0, 200))

// SSE 流式聊天
const resp = await fetch(`${B}/api/hermes/api/sessions/${sid}/chat/stream`, {
  method: 'POST', headers: auth, body: JSON.stringify({ message: '请只回复两个字：在线' })
})
console.log(`[3] SSE HTTP ${resp.status}`)
const reader = resp.body.getReader()
const dec = new TextDecoder()
let buf = '', deltas = 0, finalText = ''
const seen = new Set()
const t0 = Date.now()
for (;;) {
  const { done, value } = await reader.read()
  if (done || Date.now() - t0 > 90000) break
  buf += dec.decode(value, { stream: true })
  let idx
  while ((idx = buf.indexOf('\n\n')) >= 0) {
    const frame = buf.slice(0, idx)
    buf = buf.slice(idx + 2)
    let ev = '', data = ''
    for (const line of frame.split('\n')) {
      if (line.startsWith('event:')) ev = line.slice(6).trim()
      else if (line.startsWith('data:')) data += line.slice(5)
    }
    if (ev && !seen.has(ev)) { seen.add(ev); console.log(`    event: ${ev}`) }
    try {
      const p = JSON.parse(data)
      if (p?.event) {
        if (!seen.has(p.event)) { seen.add(p.event); console.log(`    event: ${p.event}`) }
        if (p.event === 'assistant.delta' && deltas < 2) { deltas++; console.log(`    delta: ${p.delta}`) }
        if (p.event === 'run.started') console.log(`    run_id: ${p.run_id}`)
        if (p.event === 'run.completed') finalText = p.output || ''
        if (p.event === 'run.failed') finalText = 'FAILED: ' + (p.error || '')
      }
    } catch { /* 注释帧 */ }
  }
}
console.log(`[4] 事件类型 ${seen.size} 种: ${[...seen].join(', ')}`)
console.log(`[5] 最终输出: ${finalText.slice(0, 100) || '(空)'}`)

// 清理会话
await fetch(`${B}/api/hermes/api/sessions/${sid}`, { method: 'DELETE', headers: auth })
console.log('[6] 冒烟会话已清理')
