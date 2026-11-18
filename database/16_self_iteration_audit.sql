-- =====================================================================
-- D7：M8 自迭代闭环 + 铁律五审计链联查视图（V2.0）
-- 依赖：hermes_run_event / hermes_tool_audit / sys_ai_call_log / outbound_log（D1/D4 已建）
-- =====================================================================

-- M8-1 反馈采集：run/消息/任务三源评分（±1），维度归因数据源
CREATE TABLE IF NOT EXISTS agent_feedback (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_type VARCHAR(20) NOT NULL COMMENT 'RUN/MESSAGE/TASK',
  source_id BIGINT NOT NULL COMMENT '来源主键（agent_task.id / ai_message.id）',
  run_id VARCHAR(64) NULL COMMENT 'Hermes runId（source_type=RUN 时）',
  user_id BIGINT NOT NULL COMMENT '评分人',
  rating TINYINT NOT NULL COMMENT '+1 赞 / -1 踩',
  dimension VARCHAR(20) NULL COMMENT 'ACCURACY准确/SPEED速度/SAFETY安全/TOOL工具',
  comment VARCHAR(500) NULL COMMENT '文字反馈',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_af_source (source_type, source_id),
  KEY idx_af_run (run_id),
  KEY idx_af_user (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 反馈采集（M8 自迭代）';

-- M8-2 迭代管线：每周低分归因 → 补丁建议（人工采纳后写入 SOUL.md / 工具需求 / 模板沉淀）
CREATE TABLE IF NOT EXISTS soul_patch_proposal (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  period VARCHAR(10) NOT NULL COMMENT '统计周期（ISO 周如 2026-W47）',
  category VARCHAR(20) NOT NULL COMMENT 'SOUL_PATCH口径补丁/TOOL_REQUEST工具需求/SKILL_TEMPLATE模板沉淀',
  root_cause VARCHAR(20) NOT NULL COMMENT 'TASK_UNCLEAR任务书不清/TOOL_MISSING工具缺失/KNOWLEDGE_GAP知识不足/OTHER其他',
  summary VARCHAR(300) NOT NULL COMMENT '问题摘要',
  suggestion VARCHAR(500) NOT NULL COMMENT '改进建议（采纳后追加进 SOUL.md 的补丁段文本）',
  evidence_json TEXT COMMENT '证据（低分 run/失败任务清单）',
  status VARCHAR(16) NOT NULL DEFAULT 'PROPOSED' COMMENT 'PROPOSED待审/ADOPTED已采纳/REJECTED已驳回',
  adopt_note VARCHAR(300) NULL COMMENT '采纳备注',
  soul_version VARCHAR(20) NULL COMMENT '采纳后 SOUL 版本号',
  adopted_at DATETIME NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_spp_status (status, create_time),
  KEY idx_spp_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOUL 补丁建议（M8 迭代管线）';

-- M8-3 SOUL.md 版本链：每次采纳追加补丁段前快照留痕（可回滚）
CREATE TABLE IF NOT EXISTS soul_change_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version VARCHAR(20) NOT NULL COMMENT '版本号 v1/v2/…',
  change_type VARCHAR(20) NOT NULL COMMENT 'INIT基线/PATCH补丁/ROLLBACK回滚',
  diff_summary VARCHAR(500) COMMENT '变更摘要',
  content_snapshot MEDIUMTEXT NOT NULL COMMENT '变更后全文快照',
  proposal_id BIGINT NULL COMMENT '关联补丁建议',
  operator VARCHAR(50) NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_scl_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SOUL.md 变更版本链（M8）';

-- M8-4 Skill 沉淀：高频成功任务书模板化（模板带使用统计与成功率）
CREATE TABLE IF NOT EXISTS skill_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_name VARCHAR(100) NOT NULL COMMENT '模板名',
  template_content VARCHAR(2000) NOT NULL COMMENT '任务书模板（{占位符}）',
  use_count INT NOT NULL DEFAULT 0 COMMENT '使用次数',
  success_count INT NOT NULL DEFAULT 0 COMMENT '成功次数',
  success_rate DECIMAL(4,3) NOT NULL DEFAULT 0 COMMENT '成功率（success/use）',
  last_used_at DATETIME NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_st_name (template_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务书模板库（M8 Skill 沉淀）';

-- M8-5 能力评估回归集：30 条黄金指令（25 路由 + 5 越权），SOUL/工具变更后回归门槛
CREATE TABLE IF NOT EXISTS hermes_regression_suite (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  case_no VARCHAR(10) NOT NULL COMMENT '用例号 G01…P05',
  case_kind VARCHAR(10) NOT NULL COMMENT 'ROUTE路由断言/PERM越权断言',
  scene VARCHAR(20) NOT NULL COMMENT 'WEBSEARCH/ADVISOR/WORKFLOW/OPS/DELEGATE/PERM',
  instruction VARCHAR(500) NOT NULL COMMENT '黄金指令原文（PERM 类为目标端点）',
  expected_route VARCHAR(40) NULL COMMENT '期望路由（ROUTE 类）',
  perm_method VARCHAR(10) NULL COMMENT '越权请求方法（PERM 类）',
  expect_denied TINYINT NULL COMMENT '越权期望 403',
  enabled TINYINT NOT NULL DEFAULT 1,
  last_result VARCHAR(20) NULL COMMENT 'PASS/FAIL',
  last_run_at DATETIME NULL,
  UNIQUE KEY uk_hrs_case (case_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='黄金指令回归集（M8/V2.0 验收）';

-- 路由断言 25 条（期望值与 AiChatService.dryRoute 规则层一致：WEBSEARCH→ADVISOR→WORKFLOW→DELEGATE→OPS→GENERAL）
INSERT INTO hermes_regression_suite (case_no, case_kind, scene, instruction, expected_route) VALUES
  ('G01','ROUTE','WEBSEARCH','联网检索一下风电行业最新招标动态','WEBSEARCH'),
  ('G02','ROUTE','WEBSEARCH','网上搜索一下智能消防系统的主流厂商','WEBSEARCH'),
  ('G03','ROUTE','WEBSEARCH','帮我联网查一下今天的钢材价格行情','WEBSEARCH'),
  ('G04','ROUTE','WEBSEARCH','websearch wind turbine fire safety standards','WEBSEARCH'),
  ('G05','ROUTE','ADVISOR','专业意见：风电场消防验收流程有哪些关键点','ADVISOR'),
  ('G06','ROUTE','ADVISOR','给个合规意见：这类改造是否需要消防备案','ADVISOR'),
  ('G07','ROUTE','ADVISOR','机舱自动灭火装置安装的行业标准要求是什么','ADVISOR'),
  ('G08','ROUTE','ADVISOR','风电塔筒防火涂层应该符合哪些标准','ADVISOR'),
  ('G09','ROUTE','WORKFLOW','创建一个设备验收流程','WORKFLOW'),
  ('G10','ROUTE','WORKFLOW','新建一份合同会签流程','WORKFLOW'),
  ('G11','ROUTE','WORKFLOW','设备采购流程设计','WORKFLOW'),
  ('G12','ROUTE','DELEGATE','委托AI员工把三个生产基地的整改情况汇总成一份材料','DELEGATE'),
  ('G13','ROUTE','DELEGATE','交给智能体处理供应商对账问题并跟踪闭环','DELEGATE'),
  ('G14','ROUTE','DELEGATE','帮我起草一份设备巡检报告然后同步给全部班组长','DELEGATE'),
  ('G15','ROUTE','DELEGATE','生成一份本月项目周报并对比上月进度差异','DELEGATE'),
  ('G16','ROUTE','OPS','查一下我的待办任务','OPS'),
  ('G17','ROUTE','OPS','当前有哪些进行中的项目','OPS'),
  ('G18','ROUTE','OPS','查一下生效中的合同','OPS'),
  ('G19','ROUTE','OPS','最近中标了哪些标讯','OPS'),
  ('G20','ROUTE','OPS','查一下生产工单的齐套率','OPS'),
  ('G21','ROUTE','OPS','有多少逾期未还的档案','OPS'),
  ('G22','ROUTE','OPS','查一下上个月的售后报修单','OPS'),
  ('G23','ROUTE','OPS','目前各项目的成本核算情况','OPS'),
  ('G24','ROUTE','OPS','设计方案列表里查BOM','OPS'),
  ('G25','ROUTE','OPS','事件审批还有哪些待处理','OPS')
ON DUPLICATE KEY UPDATE instruction = VALUES(instruction), expected_route = VALUES(expected_route);

-- 越权断言 5 条（V2.0 铁律二：低权限身份直调管理端点必须 403）
INSERT INTO hermes_regression_suite (case_no, case_kind, scene, instruction, perm_method, expect_denied) VALUES
  ('P01','PERM','PERM','/v1/system/perm-config/assignments','POST',1),
  ('P02','PERM','PERM','/v1/system/perm-config/preview','GET',1),
  ('P03','PERM','PERM','/v1/agent-grants','POST',1),
  ('P04','PERM','PERM','/v1/ai-configs','POST',1),
  ('P05','PERM','PERM','/v1/code-rules','POST',1)
ON DUPLICATE KEY UPDATE instruction = VALUES(instruction), perm_method = VALUES(perm_method), expect_denied = VALUES(expect_denied);

-- 铁律五审计链联查视图：run_id 主线四段（过程事件/工具审计/模型调用/出网留痕），
-- SysOperLog 无 run_id 关联列，由前端按操作人+时间窗辅助定位，不入视图。
-- 四表建表排序规则不一致（0900_ai_ci/general_ci/unicode_ci），UNION 列统一 CONVERT 收敛
CREATE OR REPLACE VIEW v_audit_chain AS
SELECT 'RUN_EVENT' AS chain_type, CONVERT(run_id USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS run_id,
       CAST(id AS CHAR) AS ref_id,
       CONVERT(event_type USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS action,
       CONVERT(IFNULL(tool, '') USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS detail_key,
       CONVERT(IFNULL(preview, '') USING utf8mb4) COLLATE utf8mb4_0900_ai_ci AS detail,
       is_error AS is_error, duration_ms, NULL AS user_id, create_time
FROM hermes_run_event
UNION ALL
SELECT 'TOOL_AUDIT', CONVERT(run_id USING utf8mb4) COLLATE utf8mb4_0900_ai_ci, CAST(id AS CHAR),
       CONVERT(outcome USING utf8mb4) COLLATE utf8mb4_0900_ai_ci,
       CONVERT(IFNULL(tool_name, '') USING utf8mb4) COLLATE utf8mb4_0900_ai_ci,
       CONVERT(CONCAT_WS(' | ', tool_kind, identity_src, IFNULL(params_digest, ''), IFNULL(error_brief, '')) USING utf8mb4) COLLATE utf8mb4_0900_ai_ci,
       CASE WHEN outcome = 'SUCCESS' THEN 0 ELSE 1 END, cost_ms, user_id, create_time
FROM hermes_tool_audit
UNION ALL
SELECT 'AI_CALL', CONVERT(request_id USING utf8mb4) COLLATE utf8mb4_0900_ai_ci, CAST(id AS CHAR),
       CONVERT(status USING utf8mb4) COLLATE utf8mb4_0900_ai_ci,
       CONVERT(IFNULL(capability_code, '') USING utf8mb4) COLLATE utf8mb4_0900_ai_ci,
       CONVERT(CONCAT_WS(' | ', model, CONCAT('prompt_chars:', IFNULL(prompt_chars, 0)),
                 CONCAT('response_chars:', IFNULL(response_chars, 0)), CONCAT('tokens:', IFNULL(total_tokens, 0)),
                 IFNULL(error_msg, '')) USING utf8mb4) COLLATE utf8mb4_0900_ai_ci,
       CASE WHEN status = 'SUCCESS' THEN 0 ELSE 1 END, duration_ms, CAST(caller_id AS UNSIGNED), create_time
FROM sys_ai_call_log
UNION ALL
SELECT 'OUTBOUND', CONCAT('OB-', id), CAST(id AS CHAR), CONVERT(status USING utf8mb4) COLLATE utf8mb4_0900_ai_ci,
       CONVERT(IFNULL(purpose, '') USING utf8mb4) COLLATE utf8mb4_0900_ai_ci,
       CONVERT(CONCAT_WS(' | ', target_host, IFNULL(sanitized_query, ''), IFNULL(hit_types, ''), IFNULL(error_brief, '')) USING utf8mb4) COLLATE utf8mb4_0900_ai_ci,
       CASE WHEN status = 'SUCCESS' THEN 0 ELSE 1 END, cost_ms, user_id, create_time
FROM outbound_log;
