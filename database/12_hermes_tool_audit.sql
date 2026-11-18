-- ============================================================
-- 12_hermes_tool_audit.sql（V2.0 铁律五：操作留痕——MCP 工具调用级审计）
-- append-only：应用层仅 INSERT/SELECT，禁 UPDATE/DELETE（MyBatis 侧无更新方法）
-- ============================================================
CREATE TABLE IF NOT EXISTS hermes_tool_audit (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    run_id        VARCHAR(64)  NULL COMMENT 'Hermes run ID（非委托调用为空）',
    tool_name     VARCHAR(100) NOT NULL COMMENT 'MCP 工具名',
    tool_kind     VARCHAR(20)  NOT NULL DEFAULT 'readonly' COMMENT '工具类型：readonly/write（写=审批化）',
    user_id       BIGINT       NULL COMMENT '生效调用身份（委托人/透传用户）',
    identity_src  VARCHAR(20)  NULL COMMENT '身份来源：ARGS/HEADER',
    outcome       VARCHAR(20)  NOT NULL COMMENT '结果：SUCCESS/DENIED/FAILED/INVALID',
    error_brief   VARCHAR(300) NULL COMMENT '失败/拒绝简要（截断）',
    params_digest CHAR(16)     NULL COMMENT '参数 SHA-256 前 16 位（不存原文）',
    cost_ms       BIGINT       NULL COMMENT '执行耗时毫秒',
    chain_prev    CHAR(16)     NULL COMMENT '链式哈希：上一条记录 digest（防篡改链）',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (id),
    KEY idx_hta_run (run_id),
    KEY idx_hta_user_time (user_id, create_time),
    KEY idx_hta_tool (tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='Hermes/MCP 工具调用审计（V2.0 铁律五：全链路可追溯，append-only）';
