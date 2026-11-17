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
// BFF 自身健康（公开，供计划任务/监控探活）
app.get('/api/studio/health', async (req, res) => {
  let hermesOk = false
  try {
    const ctrl = new AbortController()
    const t = setTimeout(() => ctrl.abort(), 1500)
    const r = await fetch(`${config.hermesBaseUrl}/health`, { signal: ctrl.signal })
    hermesOk = (await r.json())?.status === 'ok'
    clearTimeout(t)
  } catch {
    hermesOk = false
  }
  res.json({ ok: true, version: config.version, hermesOk, ts: Date.now() })
})
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
  // assets 带 hash → 长缓存；index.html 不缓存保证发版即生效
  app.use(
    express.static(webDist, {
      setHeaders: (res, filePath) => {
        if (filePath.includes(`${path.sep}assets${path.sep}`)) {
          res.setHeader('Cache-Control', 'public, max-age=604800, immutable')
        } else {
          res.setHeader('Cache-Control', 'no-cache')
        }
      }
    })
  )
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

const server = app.listen(config.port, '127.0.0.1', () => {
  console.log(`[hermes-studio] BFF http://127.0.0.1:${config.port} → Hermes ${config.hermesBaseUrl}`)
})

// 优雅停机（计划任务停止/部署重启时不杀进行中的 SSE，先关监听再退出）
for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    console.log(`[hermes-studio] 收到 ${sig}，关闭监听…`)
    server.close(() => process.exit(0))
    setTimeout(() => process.exit(0), 3000).unref()
  })
}
