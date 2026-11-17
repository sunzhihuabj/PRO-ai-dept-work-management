// Hermes Studio BFF 入口
// 链路：浏览器 → (Express 8650：登录鉴权 + 工作流存储 + 静态托管) → 透传代理 → Hermes API Server 8642
import path from 'node:path'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import express from 'express'
import { config } from './config.mjs'
import { checkPassword, issueToken, requireAuth } from './auth.mjs'
import { hermesProxy } from './hermes-proxy.mjs'
import { listWorkflows, getWorkflow, saveWorkflow, deleteWorkflow } from './workflows.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const webDist = path.join(__dirname, '..', 'web', 'dist')

const app = express()
app.disable('x-powered-by')

// ---------- 登录（唯一明文 body 解析点） ----------
app.post('/api/auth/login', express.json({ limit: '64kb' }), (req, res) => {
  const { username, password } = req.body || {}
  const identity = checkPassword(username, password)
  if (!identity) {
    return res.status(401).json({ error: { message: '用户名或密码错误', code: 'studio_bad_credentials' } })
  }
  res.json({ token: issueToken(identity.username), username: identity.username })
})

// ---------- Studio 自有数据：工作流 CRUD ----------
app.use('/api/studio', requireAuth, express.json({ limit: '1mb' }))
app.get('/api/studio/config', (req, res) => {
  res.json({
    version: config.version,
    hermesBaseUrl: config.hermesBaseUrl,
    hermesKeyMasked: config.hermesApiKey ? `${config.hermesApiKey.slice(0, 8)}****` : '(未配置)',
    user: req.studioUser
  })
})
app.get('/api/studio/workflows', (req, res) => res.json(listWorkflows()))
app.get('/api/studio/workflows/:id', (req, res) => {
  const doc = getWorkflow(req.params.id)
  doc ? res.json(doc) : res.status(404).json({ error: { message: '工作流不存在' } })
})
app.post('/api/studio/workflows', (req, res) => res.json(saveWorkflow(req.body || {})))
app.delete('/api/studio/workflows/:id', (req, res) => {
  deleteWorkflow(req.params.id) ? res.json({ ok: true }) : res.status(404).json({ error: { message: '工作流不存在' } })
})

// ---------- Hermes 透传（含 SSE；必须挂在 body 解析之外） ----------
app.use('/api/hermes', requireAuth, hermesProxy)

// ---------- 静态托管（生产模式） ----------
if (fs.existsSync(webDist)) {
  app.use(express.static(webDist))
  app.get(/^\/(?!api\/).*/, (req, res) => res.sendFile(path.join(webDist, 'index.html')))
} else {
  app.get('/', (req, res) =>
    res.type('text/plain').send('Hermes Studio BFF 运行中。\n前端未构建：npm run build 后重启；或开发模式 npm run dev（Vite 8651）。')
  )
}

// ---------- 兜底错误处理 ----------
app.use((err, req, res, next) => {
  console.error('[hermes-studio]', err)
  if (!res.headersSent) res.status(500).json({ error: { message: String(err?.message || err) } })
})

app.listen(config.port, '127.0.0.1', () => {
  console.log(`[hermes-studio] BFF http://127.0.0.1:${config.port} → Hermes ${config.hermesBaseUrl}`)
})
