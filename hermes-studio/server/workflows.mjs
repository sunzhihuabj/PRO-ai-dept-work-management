// 工作流定义存储：server/data/workflows/<id>.json（Studio 自有数据，与 Hermes 状态隔离）
import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { config } from './config.mjs'

const dir = path.join(config.dataDir, 'workflows')

function ensureDir() {
  fs.mkdirSync(dir, { recursive: true })
}

function safeId(id) {
  return /^[a-zA-Z0-9_-]{1,64}$/.test(id) ? id : null
}

export function listWorkflows() {
  ensureDir()
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .map((f) => JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')))
    .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
}

export function getWorkflow(id) {
  const sid = safeId(id)
  if (!sid) return null
  const file = path.join(dir, `${sid}.json`)
  if (!fs.existsSync(file)) return null
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

export function saveWorkflow(body) {
  const id = body.id && safeId(body.id) ? body.id : `wf_${crypto.randomUUID().slice(0, 8)}`
  const now = Date.now()
  const doc = {
    id,
    name: String(body.name || '未命名工作流').slice(0, 100),
    description: String(body.description || '').slice(0, 500),
    steps: Array.isArray(body.steps) ? body.steps.slice(0, 50) : [],
    createdAt: body.createdAt || now,
    updatedAt: now
  }
  ensureDir()
  fs.writeFileSync(path.join(dir, `${id}.json`), JSON.stringify(doc, null, 2))
  return doc
}

export function deleteWorkflow(id) {
  const sid = safeId(id)
  if (!sid) return false
  const file = path.join(dir, `${sid}.json`)
  if (!fs.existsSync(file)) return false
  fs.rmSync(file)
  return true
}
