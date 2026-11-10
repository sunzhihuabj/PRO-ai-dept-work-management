/**
 * AI 调用审计
 *
 * <p>职责：全量记录模型调用的能力编号、输入输出摘要、Token 用量、耗时、
 * 调用人与 Agent 标识（agent:{userId}:{能力编号}），日志保留 2 年；
 * AI 生成内容统一打 ai_generated 标注。
 *
 * <p>对应规划任务：T-105。当前状态：骨架占位（Phase 0 / T-004）。
 */
package com.dept.mgmt.ai.audit;
