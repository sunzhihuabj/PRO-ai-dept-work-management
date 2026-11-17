-- ============================================================
-- 11_hermes_run_event.sql
-- B3 执行过程回写：Hermes run 执行事件落库（时间线可视化 / B5 审批联动数据源）
-- 事件来源：GET /v1/runs/{id}/events SSE 流（Hermes v0.22.5 api_server）
-- ============================================================

CREATE TABLE IF NOT EXISTS hermes_run_event (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  run_id      VARCHAR(64)  NOT NULL COMMENT 'Hermes runId（ai_message.content 内嵌标记关联）',
  seq         BIGINT       NOT NULL DEFAULT -1 COMMENT 'Hermes 事件序号（SSE seq，幂等去重）',
  event_type  VARCHAR(64)  NOT NULL COMMENT '事件类型（tool.started/tool.completed/approval.request/run.*等）',
  tool        VARCHAR(128) NULL COMMENT '工具名（tool.* 事件）',
  preview     VARCHAR(768) NULL COMMENT '内容预览（工具参数预览/中间文本/错误信息，截断 500 字符）',
  duration_ms BIGINT       NULL COMMENT '工具执行耗时毫秒（tool.completed 事件）',
  is_error    TINYINT      NOT NULL DEFAULT 0 COMMENT '工具是否执行失败（tool.completed.error）',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '落库时间',
  UNIQUE KEY uk_run_seq (run_id, seq),
  KEY idx_run (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Hermes run 执行过程事件（B3 过程回写）';
