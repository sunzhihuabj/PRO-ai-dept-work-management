/**
 * 模型 Provider 抽象层
 *
 * <p>职责：屏蔽底层模型 SDK/URL 差异，统一 Provider 接口（Ollama / 商用 API / mock），
 * 支持网关 mock→真实双通道路由测试。现有 AiService（RestTemplate 直连 Ollama）
 * 后续改造为网关下游 Provider 实现。
 *
 * <p>对应规划任务：T-105。当前状态：骨架占位（Phase 0 / T-004）。
 */
package com.dept.mgmt.ai.provider;
