// Studio 自身鉴权：HMAC 签名令牌（无状态，24h 有效），Hermes API Key 永不下发浏览器
import crypto from 'node:crypto'
import { config } from './config.mjs'

const TTL_MS = 24 * 3600 * 1000

function sign(payload) {
  return crypto.createHmac('sha256', config.secret).update(payload).digest('base64url')
}

export function issueToken(username) {
  const exp = Date.now() + TTL_MS
  const payload = `${username}.${exp}`
  return `v1.${exp}.${Buffer.from(username).toString('base64url')}.${sign(payload)}`
}

export function verifyToken(token) {
  if (typeof token !== 'string') return null
  const parts = token.split('.')
  if (parts.length !== 4 || parts[0] !== 'v1') return null
  const exp = Number(parts[1])
  if (!Number.isFinite(exp) || exp < Date.now()) return null
  const username = Buffer.from(parts[2], 'base64url').toString('utf8')
  const expected = sign(`${username}.${exp}`)
  const a = Buffer.from(parts[3])
  const b = Buffer.from(expected)
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null
  return { username, exp }
}

export function checkPassword(username, password) {
  const u = Buffer.from(String(username || ''))
  const p = Buffer.from(String(password || ''))
  const eu = Buffer.from(config.user)
  const ep = Buffer.from(config.pass)
  const okUser = u.length === eu.length && crypto.timingSafeEqual(u, eu)
  const okPass = p.length === ep.length && crypto.timingSafeEqual(p, ep)
  return okUser && okPass ? { username: config.user } : null
}

// Express 中间件：校验 Authorization: Bearer <studio-token>
export function requireAuth(req, res, next) {
  const header = req.headers.authorization || ''
  const token = header.startsWith('Bearer ') ? header.slice(7) : (req.query.token || '')
  const identity = verifyToken(token)
  if (!identity) {
    return res.status(401).json({ error: { message: 'Studio 登录态无效或已过期', code: 'studio_auth_failed' } })
  }
  req.studioUser = identity.username
  next()
}
