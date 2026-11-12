-- ============================================================================
-- T-408：统一 AI 助手工作台（对话 + 业务流程编排 + 生命周期推动）
-- 1. ai_conversation / ai_message   —— 多轮对话会话与消息（含 Agent 任务卡片关联）
-- 2. wf_workflow / wf_instance / wf_instance_node —— 流程定义 / 实例 / 节点运行记录
-- ============================================================================

USE `dept_mgmt`;
SET NAMES utf8mb4;

-- ---------- 0. T-409 消息评分列（对齐豆包回复点赞/点踩） ----------
ALTER TABLE `ai_message`
    ADD COLUMN `rating` TINYINT DEFAULT NULL COMMENT '用户评分（1赞/-1踩/NULL未评，仅 assistant 消息，T-409）' AFTER `ref_workflow_id`;

-- ---------- 1. AI 对话会话表 ----------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `conv_no`       VARCHAR(30)  NOT NULL                COMMENT '会话编号（唯一）',
    `title`         VARCHAR(100) DEFAULT '新对话'        COMMENT '会话标题（默认取首条消息截断）',
    `user_id`       BIGINT       NOT NULL                COMMENT '归属用户ID',
    `pinned`        TINYINT      DEFAULT 0               COMMENT '是否置顶（0否 1是）',
    `last_msg_time` DATETIME     DEFAULT NULL            COMMENT '最后消息时间（列表排序）',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conv_no` (`conv_no`),
    KEY             `idx_user` (`user_id`),
    KEY             `idx_last_msg` (`last_msg_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话会话表';

-- ---------- 2. AI 对话消息表 ----------
DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id` BIGINT      NOT NULL                COMMENT '会话ID',
    `role`            VARCHAR(12) NOT NULL                COMMENT '角色（user/assistant）',
    `content`         MEDIUMTEXT  DEFAULT NULL            COMMENT '消息内容（Markdown）',
    `msg_type`        VARCHAR(12) DEFAULT 'text'          COMMENT '消息类型（text/task/workflow）',
    `ref_task_id`     BIGINT      DEFAULT NULL            COMMENT '关联 Agent 任务ID（msg_type=task）',
    `ref_workflow_id` BIGINT      DEFAULT NULL            COMMENT '关联流程草稿ID（msg_type=workflow）',
    `create_time`     DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_conv` (`conversation_id`),
    KEY `idx_ref_task` (`ref_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话消息表';

-- ---------- 3. 业务流程定义表 ----------
DROP TABLE IF EXISTS `wf_workflow`;
CREATE TABLE `wf_workflow` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流程ID',
    `wf_code`     VARCHAR(30)  NOT NULL                COMMENT '流程编码（唯一，WF 规则）',
    `name`        VARCHAR(100) NOT NULL                COMMENT '流程名称',
    `description` VARCHAR(500) DEFAULT NULL            COMMENT '流程说明',
    `graph`       JSON         DEFAULT NULL            COMMENT '流程图（{nodes:[{key,name,type,x,y,config}],edges:[{from,to,label}]}）',
    `status`      VARCHAR(12)  NOT NULL DEFAULT 'draft' COMMENT '状态（draft草稿/published已发布/offline已下线）',
    `version`     INT          DEFAULT 1               COMMENT '版本号',
    `create_by`   VARCHAR(50)  DEFAULT NULL            COMMENT '创建人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wf_code` (`wf_code`),
    KEY `idx_status` (`status`),
    KEY `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务流程定义表';

-- ---------- 4. 流程实例表 ----------
DROP TABLE IF EXISTS `wf_instance`;
CREATE TABLE `wf_instance` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '实例ID',
    `instance_no`    VARCHAR(30)  NOT NULL                COMMENT '实例编号（唯一，WFI 规则）',
    `workflow_id`    BIGINT       NOT NULL                COMMENT '流程ID',
    `workflow_name`  VARCHAR(100) DEFAULT NULL            COMMENT '流程名称快照',
    `graph_snapshot` JSON         DEFAULT NULL            COMMENT '启动时流程图快照',
    `status`         VARCHAR(12)  NOT NULL DEFAULT 'running' COMMENT '状态（running/completed/failed/cancelled）',
    `started_by`     VARCHAR(50)  DEFAULT NULL            COMMENT '发起人',
    `start_time`     DATETIME     DEFAULT NULL            COMMENT '开始时间',
    `end_time`       DATETIME     DEFAULT NULL            COMMENT '结束时间',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_instance_no` (`instance_no`),
    KEY `idx_workflow` (`workflow_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例表';

-- ---------- 5. 流程实例节点运行记录表 ----------
DROP TABLE IF EXISTS `wf_instance_node`;
CREATE TABLE `wf_instance_node` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '节点运行ID',
    `instance_id`      BIGINT       NOT NULL                COMMENT '实例ID',
    `node_key`         VARCHAR(50)  NOT NULL                COMMENT '节点键（流程图内唯一）',
    `node_name`        VARCHAR(100) DEFAULT NULL            COMMENT '节点名称快照',
    `node_type`        VARCHAR(20)  NOT NULL                COMMENT '节点类型（start/agent/approval/notification）',
    `config`           JSON         DEFAULT NULL            COMMENT '节点配置快照（能力点/指令模板/审批人/通知对象）',
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT '状态（pending/running/waiting_approval/done/failed/skipped）',
    `agent_task_id`    BIGINT       DEFAULT NULL            COMMENT '关联 Agent 任务ID（agent 节点）',
    `assignee_user_id` BIGINT       DEFAULT NULL            COMMENT '审批人ID（approval 节点）',
    `approval_comment` VARCHAR(500) DEFAULT NULL            COMMENT '审批意见',
    `error_msg`        VARCHAR(500) DEFAULT NULL            COMMENT '失败原因',
    `start_time`       DATETIME     DEFAULT NULL            COMMENT '开始时间',
    `finish_time`      DATETIME     DEFAULT NULL            COMMENT '完成时间',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_instance_node` (`instance_id`, `node_key`),
    KEY `idx_instance` (`instance_id`),
    KEY `idx_status` (`status`),
    KEY `idx_agent_task` (`agent_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例节点运行记录表';

-- ---------- 6. 编码规则 ----------
INSERT INTO `sys_code_rule` (`rule_code`, `rule_name`, `pattern`, `date_pattern`, `status`, `remark`) VALUES
('CONV', 'AI会话编号', 'CONV-{yyyyMMdd}-{seq:4}', 'yyyyMMdd', 1, 'T-408 AI 对话会话'),
('WF',   '流程编码',   'WF-{yyyyMMdd}-{seq:3}',   'yyyyMMdd', 1, 'T-408 业务流程定义'),
('WFI',  '流程实例号', 'WFI-{yyyyMMdd}-{seq:4}',  'yyyyMMdd', 1, 'T-408 流程实例');

-- ---------- 7. 流程域权限点（T-404 体系，灰度原则：未配置不拦截；对话能力不做接口级限制） ----------
INSERT INTO `sys_perm_point` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`) VALUES
('btn:wf:manage',    '流程编排·设计管理',   'BUTTON', '/ai', 21),
('btn:wf:start',     '流程编排·启动实例',   'BUTTON', '/ai', 22),
('btn:wf:approve',   '流程编排·节点审批',   'BUTTON', '/ai', 23),
('api:wf:manage',    '流程编排·定义管理接口', 'API',  '/ai', 121),
('api:wf:start',     '流程编排·实例启动接口', 'API',  '/ai', 122),
('api:wf:approve',   '流程编排·节点审批接口', 'API',  '/ai', 123);
