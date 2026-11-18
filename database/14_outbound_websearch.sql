-- =============================================================
-- D4: M5 联网检索（铁律四脱敏 proxy 全链路）+ M2 LLM 意图判别
-- =============================================================

-- 出网留痕表（铁律五：唯一出网口审计；原始检索词不落库，只存 SHA-256 摘要）
CREATE TABLE IF NOT EXISTS outbound_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL COMMENT '发起用户',
  target_host VARCHAR(100) NOT NULL COMMENT '出网目标主机',
  purpose VARCHAR(30) NOT NULL COMMENT '用途（WEB_SEARCH）',
  query_digest VARCHAR(16) NOT NULL COMMENT '原始检索词 SHA-256 前 16 位（不存原文）',
  sanitized_query VARCHAR(300) COMMENT '脱敏后实际出网检索词',
  hit_types VARCHAR(200) COMMENT '命中的敏感类型列表（逗号分隔）',
  result_count INT DEFAULT 0 COMMENT '结果条数',
  status VARCHAR(20) NOT NULL COMMENT 'SUCCESS/FAILED',
  error_brief VARCHAR(300) COMMENT '失败简述',
  cost_ms BIGINT DEFAULT 0 COMMENT '耗时',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ob_user_time (user_id, create_time),
  KEY idx_ob_host (target_host)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出网留痕（铁律四：脱敏 proxy 唯一出网口审计）';

-- 能力点登记：联网检索（只读）+ LLM 意图判别
INSERT INTO sys_capability (capability_code, capability_name, category, allowed_identities, is_never, status, remark)
VALUES ('CAP_WEB_SEARCH', 'AI 联网检索（M5 脱敏 proxy）', 'RETRIEVAL', 'USER,AGENT', 0, 1,
        '经脱敏网关的联网搜索，客户名/金额/证件号不出网，全程 outbound_log 留痕')
ON DUPLICATE KEY UPDATE capability_name = VALUES(capability_name);

INSERT INTO sys_capability (capability_code, capability_name, category, allowed_identities, is_never, status, remark)
VALUES ('CAP_INTENT_CLASSIFY', 'AI 意图判别（M2 自然语言驱动）', 'ROUTE', 'USER,AGENT', 0, 1,
        '对话消息的轻量意图分类（JSON 契约），路由漏判捞回')
ON DUPLICATE KEY UPDATE capability_name = VALUES(capability_name);
