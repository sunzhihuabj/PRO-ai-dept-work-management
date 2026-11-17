// Hermes API Server 原始透传代理（含 SSE 直通）
// 设计要点：
//  - 不经过任何 body 解析中间件，req/upstream 双向 pipe，保证 SSE 不被缓冲
//  - 浏览器侧令牌在中间件层校验；上游 Authorization 统一替换为服务端保管的 API_SERVER_KEY
//  - 路径规则：/api/hermes/<path> → <HERMES_BASE_URL>/<path>
import http from 'node:http'
import https from 'node:https'
import { config } from './config.mjs'

export function hermesProxy(req, res) {
  const target = new URL(config.hermesBaseUrl)
  const path = req.originalUrl.replace(/^\/api\/hermes/, '') || '/'
  const headers = { ...req.headers }
  headers.host = target.host
  headers.authorization = `Bearer ${config.hermesApiKey}`
  delete headers.connection
  // content-length 原样保留：body 未被解析，pipe 的是原始字节流

  const lib = target.protocol === 'https:' ? https : http
  const upstream = lib.request(
    {
      protocol: target.protocol,
      hostname: target.hostname,
      port: target.port || (target.protocol === 'https:' ? 443 : 80),
      method: req.method,
      path,
      headers,
      timeout: 0 // SSE 可能长时间空闲（心跳间隔 10s），不设整体超时
    },
    (ur) => {
      res.writeHead(ur.statusCode || 502, ur.headers)
      ur.pipe(res) // 流式回写：SSE / 大 JSON 均不缓冲
    }
  )
  upstream.on('timeout', () => upstream.destroy(new Error('upstream timeout')))
  upstream.on('error', (err) => {
    if (res.headersSent) {
      res.end()
    } else {
      res.status(502).json({ error: { message: `Hermes 上游不可达: ${err.message}`, code: 'upstream_unreachable' } })
    }
  })
  req.pipe(upstream)
}
