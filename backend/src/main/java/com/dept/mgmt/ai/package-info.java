/**
 * AI 网关域 —— 全系统模型调用唯一出口
 *
 * <p>红线：任何业务代码不得直连模型 SDK/URL，必须经本网关（静态扫描 0 命中）；
 * 网关输出强制 JSON 契约校验，失败重生成一次后转人工。
 *
 * <p>对应规划任务：T-105 AI 网关（Provider 抽象 / 能力注册表 / 配额限流 /
 * 出网脱敏 / 全量调用审计 / 降级链 / 双通道路由）。
 *
 * <p>子包说明：gateway 网关入口与路由；provider 模型 Provider 抽象
 * （现有 {@link com.dept.mgmt.service.AiService} 后续改造为网关下游，本次不动其代码）；
 * registry 能力注册表（AGT-01~25 能力点登记）；audit AI 调用审计（日志保留 2 年）。
 *
 * <p>当前状态：骨架占位（Phase 0 / T-004），Phase 1 实施。
 */
package com.dept.mgmt.ai;
