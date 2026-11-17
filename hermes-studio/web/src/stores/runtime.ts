import { defineStore } from 'pinia'
import { getCapabilities, getHealth } from '../api/hermes'

// 跨页共享的运行时状态：能力面 / 健康度 / 待审批聚合
export interface PendingApproval {
  runId: string
  requestId: string
  command: string
  description: string
  choices: string[]
  source: string // 'chat' | 'run' | 'workflow' | 'manual'
  scene?: string
  createdAt: number
  resolved?: string
}

export const useRuntimeStore = defineStore('runtime', {
  state: () => ({
    capabilities: null as any,
    health: null as any,
    healthOk: false,
    lastHealthAt: 0,
    pendingApprovals: [] as PendingApproval[]
  }),
  getters: {
    feature: (s) => (name: string) => !!s.capabilities?.features?.[name],
    // 审批选择全集： Hermes 会按场景给出 choices（once/session/always/deny 子集）
    pendingCount: (s) => s.pendingApprovals.filter((a) => !a.resolved).length
  },
  actions: {
    async refreshHealth() {
      try {
        this.health = await getHealth()
        this.healthOk = this.health?.status === 'ok'
      } catch {
        this.healthOk = false
      }
      this.lastHealthAt = Date.now()
    },
    async refreshCapabilities() {
      try {
        this.capabilities = await getCapabilities()
      } catch {
        this.capabilities = null
      }
    },
    upsertApproval(a: PendingApproval) {
      const found = this.pendingApprovals.find((x) => x.runId === a.runId && x.requestId === a.requestId)
      if (found) Object.assign(found, a)
      else this.pendingApprovals.unshift(a)
    },
    markResolved(runId: string, requestId: string, choice: string) {
      const found = this.pendingApprovals.find((x) => x.runId === runId && x.requestId === requestId)
      if (found) found.resolved = choice
    },
    removeRunApprovals(runId: string) {
      this.pendingApprovals = this.pendingApprovals.filter((x) => x.runId !== runId)
    }
  }
})
