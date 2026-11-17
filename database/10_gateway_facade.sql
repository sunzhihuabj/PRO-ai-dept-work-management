-- ============================================================================
-- 10_gateway_facade.sql  阶段一：OpenAI 兼容门面能力点（Hermes/Dify 统一模型入口）
-- 网关 /v1/chat/completions 以 OPENAI_COMPAT 能力点走治理流水线
-- 执行顺序：在 01_schema.sql 之后执行
-- ============================================================================

USE dept_mgmt;

-- 门面能力点（幂等：存在则跳过）
INSERT INTO `sys_capability` (`capability_code`, `capability_name`, `category`, `allowed_identities`,
                              `is_never`, `status`, `remark`, `create_time`, `update_time`, `deleted`)
SELECT 'OPENAI_COMPAT', 'OpenAI 兼容门面（外部 Agent 统一模型入口）', 'base', 'AGENT', 0, 1,
       '阶段一多 Agent 协同：Hermes/Dify 经 /v1/chat/completions 调用，走 35 次/分限流与审计',
       NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_capability` WHERE `capability_code` = 'OPENAI_COMPAT');
