/**
 * AgentTask 状态机与任务队列
 *
 * <p>职责：管理 Agent 任务的完整生命周期，状态机：
 * queued → running → pending_confirm → done / failed / escalated / cancelled / rolled_back；
 * Redis 任务队列 + 并发控制（≤5）。
 *
 * <p>对应规划任务：T-107。当前状态：骨架占位（Phase 0 / T-004）。
 */
package com.dept.mgmt.agent.task;
