-- =============================================================
-- D6: M6 文档工作台 + M7 专业意见（AGT-28）
-- =============================================================

-- 文档草稿表（M6 文档工作台：起草/版本/合规审核/送审）
CREATE TABLE IF NOT EXISTS ai_doc_draft (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_no VARCHAR(50) NOT NULL COMMENT '文档编号 DOC-YYYYMMDD-SEQ',
  title VARCHAR(200) NOT NULL COMMENT '文档标题',
  content_md MEDIUMTEXT COMMENT '正文（Markdown）',
  version INT NOT NULL DEFAULT 1 COMMENT '版本号（每次编辑 +1）',
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT草稿/REVIEW待审/APPROVED已审',
  source VARCHAR(20) NOT NULL DEFAULT 'HERMES' COMMENT '来源：HERMES/AGENT/MANUAL',
  conversation_id BIGINT COMMENT '关联 AI 会话',
  create_by VARCHAR(50) COMMENT '创建人',
  update_by VARCHAR(50) COMMENT '最后编辑人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_doc_no (doc_no),
  KEY idx_adr_status (status),
  KEY idx_adr_create (create_by, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 文档草稿（M6 文档工作台）';

-- M7 专业意见能力点（AGT-28）：kb 分域检索 + LLM 专业推理 + 依据引用 + 置信度分级审核
INSERT INTO sys_capability (capability_code, capability_name, category, allowed_identities, is_never, status, remark)
VALUES ('AGT-28', '专业意见（行业标准合规/优化建议）', 'ADVISOR', 'EXPERT,WORKER,ASSISTANT', 0, 1,
        'kb Top-K 依据检索 → LLM 专业推理 → 意见+依据引用(source:ref)+置信度；置信度<0.85 强制人工审核')
ON DUPLICATE KEY UPDATE capability_name = VALUES(capability_name), allowed_identities = VALUES(allowed_identities);

INSERT INTO agent_capability_policy (capability_code, autonomy_level)
VALUES ('AGT-28', 2)
ON DUPLICATE KEY UPDATE autonomy_level = VALUES(autonomy_level);

-- M6 文档编号规则（CodeEngineService.nextCode("DOC", null)）
INSERT INTO sys_code_rule (rule_code, rule_name, pattern, date_pattern, status, deleted)
VALUES ('DOC', 'AI文档草稿编号', 'DOC-{yyyyMMdd}-{seq:4}', 'yyyyMMdd', 1, 0)
ON DUPLICATE KEY UPDATE pattern = VALUES(pattern);
