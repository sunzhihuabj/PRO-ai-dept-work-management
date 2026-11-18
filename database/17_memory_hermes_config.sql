-- =====================================================================
-- D8 收官：M9 记忆存储完整闭环 + Hermes 原生配置模块（V2.0 全功能嵌入补齐）
-- =====================================================================

-- M9-1 记忆写入 100% 经用户确认：PROPOSED 待确认 / APPROVED 已生效（recall 只取 APPROVED）
-- 兼容存量：已沉淀记忆直接视为已生效
ALTER TABLE ai_user_memory
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'APPROVED' COMMENT 'PROPOSED待确认/APPROVED已生效',
  ADD COLUMN expire_at DATETIME NULL COMMENT 'TTL 过期时间（默认 90 天），过期不再召回',
  ADD COLUMN source VARCHAR(20) NULL DEFAULT 'SUMMARY' COMMENT 'SUMMARY会话摘要抽取/USER用户手工';
