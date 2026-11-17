-- ============================================================================
-- 09_agent_collaboration.sql  阶段一：多 Agent 协同驱动全业务流程（AI-Agent 专项）
-- 决策建议表 + Agent 任务预算护栏字段
-- 执行顺序：在 01_schema.sql / 08_tender_collect.sql 之后执行
-- ============================================================================

USE dept_mgmt;

-- ----------------------------------------------------------------------------
-- 1. Agent 决策建议表（阶段一 WP1-6：15 个介入节点统一建议输出通道）
--    生成来源：业务事件（EventBus）→ DecisionAdviceService 确定性模板（LLM 生成接入为阶段二）
--    审批语义：requiresApproval=1 的建议须经人工审批（HMAC 锁定 payload 防篡改）后执行
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `agent_decision_advice`;
CREATE TABLE `agent_decision_advice` (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '建议ID',
    `scene`                 VARCHAR(64)   NOT NULL                COMMENT '场景标识（PROJECT_CREATED/TASK_CREATED/TASK_STATUS_CHANGED/PROJECT_PROGRESS）',
    `source_type`           VARCHAR(32)   NOT NULL                COMMENT '来源实体类型（PROJECT/TASK/PHASE/MILESTONE）',
    `source_id`             BIGINT        NOT NULL                COMMENT '来源实体ID',
    `source_biz_no`         VARCHAR(64)   DEFAULT NULL            COMMENT '来源业务编号（项目编号/任务名等，便于展示）',
    `agent_id`              VARCHAR(64)   NOT NULL DEFAULT 'system-rule' COMMENT '建议 Agent 标识（阶段一为规则引擎 system-rule）',
    `agent_role`            VARCHAR(32)   NOT NULL DEFAULT '业务执行' COMMENT '建议 Agent 角色（业务执行/编排/审批兜底/系统集成）',
    `title`                 VARCHAR(200)  NOT NULL                COMMENT '建议标题',
    `content_json`          TEXT          NOT NULL                COMMENT '建议内容 JSON（type=INFO/WARNING/REQUEST + body + structured）',
    `confidence`            DECIMAL(3,2)  NOT NULL DEFAULT 0.85   COMMENT '置信度（0-1，低于阈值强制人工）',
    `risk_level`            VARCHAR(16)   NOT NULL DEFAULT 'LOW'  COMMENT '风险等级（LOW/MEDIUM/HIGH）',
    `requires_approval`     TINYINT       NOT NULL DEFAULT 0      COMMENT '是否需人工审批（0否 1是）',
    `approval_action`       VARCHAR(64)   DEFAULT NULL            COMMENT '审批通过后执行的动作（AUTO_ASSIGN_TASK 等，空=纯建议）',
    `approval_params_json`  TEXT          DEFAULT NULL            COMMENT '审批动作参数 JSON（HMAC 锁定防篡改）',
    `approval_hmac`         VARCHAR(128)  DEFAULT NULL            COMMENT '审批参数 HMAC-SHA256 摘要（防篡改校验）',
    `status`                VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING待处理/APPROVED已采纳/REJECTED已驳回/EXPIRED已过期）',
    `evidence_json`         TEXT          DEFAULT NULL            COMMENT '建议依据引用 JSON 数组（source+ref）',
    `target_user_id`        BIGINT        DEFAULT NULL            COMMENT '建议推送目标用户（站内信接收人）',
    `response_deadline`     DATETIME      DEFAULT NULL            COMMENT '建议响应截止时间（SLA）',
    `approver_id`           BIGINT        DEFAULT NULL            COMMENT '审批人',
    `approver_comment`      VARCHAR(500)  DEFAULT NULL            COMMENT '审批备注（驳回原因等）',
    `approved_at`           DATETIME      DEFAULT NULL            COMMENT '审批时间',
    `create_time`           DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_scene_source` (`scene`, `source_type`, `source_id`),
    KEY `idx_status` (`status`),
    KEY `idx_target_user` (`target_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 决策建议表（阶段一多 Agent 协同）';

-- ----------------------------------------------------------------------------
-- 2. Agent 任务预算护栏字段（阶段一：预算模型预留；精确 LLM 调用记账为阶段二）
-- ----------------------------------------------------------------------------
ALTER TABLE `agent_task`
    ADD COLUMN `a2a_task_id`   VARCHAR(64) DEFAULT NULL COMMENT 'A2A v1.0 Task ID（阶段二跨 Agent 委派时回填）' AFTER `executed_by`,
    ADD COLUMN `step_count`    INT         NOT NULL DEFAULT 0 COMMENT '子任务步骤数（阶段二 A2A fan-out）' AFTER `a2a_task_id`,
    ADD COLUMN `budget_used`   INT         NOT NULL DEFAULT 0 COMMENT '已用 LLM 调用次数（阶段一预留，精确记账为阶段二）' AFTER `step_count`,
    ADD COLUMN `budget_limit`  INT         NOT NULL DEFAULT 35 COMMENT 'LLM 调用预算上限（全局限流 35 次/分对齐）' AFTER `budget_used`,
    ADD COLUMN `confidence`    DECIMAL(3,2) DEFAULT NULL COMMENT '结果置信度（0-1）' AFTER `budget_limit`,
    ADD COLUMN `risk_level`    VARCHAR(16)  DEFAULT 'MEDIUM' COMMENT '结果风险等级（LOW/MEDIUM/HIGH）' AFTER `confidence`;
