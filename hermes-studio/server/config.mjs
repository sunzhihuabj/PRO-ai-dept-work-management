// 极简 .env 加载器（不引入 dotenv 依赖）：只解析 KEY=VALUE 行，已存在的环境变量优先
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const envFile = path.join(__dirname, '..', '.env')

if (fs.existsSync(envFile)) {
  for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const m = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/.exec(line)
    if (!m) continue
    if (process.env[m[1]] === undefined) process.env[m[1]] = m[2]
  }
}

export const config = {
  hermesBaseUrl: process.env.HERMES_BASE_URL || 'http://127.0.0.1:8642',
  hermesApiKey: process.env.HERMES_API_KEY || '',
  port: Number(process.env.STUDIO_PORT || 8650),
  user: process.env.STUDIO_USER || 'admin',
  pass: process.env.STUDIO_PASS || 'dept-studio-2026',
  secret: process.env.STUDIO_SECRET || 'dev-secret',
  dataDir: path.join(__dirname, 'data'),
  version: '1.0.0'
}

if (!config.hermesApiKey) {
  console.warn('[hermes-studio] HERMES_API_KEY 未配置，代理到 Hermes 将返回 401')
}
