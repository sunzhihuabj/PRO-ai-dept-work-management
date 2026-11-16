-- ============================================================================
-- P0 智能化改造（AI 工作台记忆层升级）
-- 1. ai_conversation 新增滚动摘要列（summary / summary_msg_id）—— 长会话渐进压缩，
--    decide/通用对话 prompt 只携带「摘要 + 最近消息」，token 成本不随会话长度线性增长
-- 2. ai_user_memory —— 用户长期记忆表（跨会话沉淀稳定事实，如常跟项目/汇报偏好）
-- ============================================================================

USE `dept_mgmt`;
SET NAMES utf8mb4;

-- ---------- 1. ai_conversation 滚动摘要列 ----------
ALTER TABLE `ai_conversation`
    ADD COLUMN `summary`         TEXT   DEFAULT NULL COMMENT '会话滚动摘要（P0-3，最近 KEEP_RECENT 条之前的消息压缩结果）' AFTER `pinned`,
    ADD COLUMN `summary_msg_id`  BIGINT DEFAULT NULL COMMENT '摘要已覆盖到的最后一条消息ID（此后消息仍原文携带）' AFTER `summary`;

-- ---------- 2. 用户长期记忆表 ----------
DROP TABLE IF EXISTS `ai_user_memory`;
CREATE TABLE `ai_user_memory` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记忆ID',
    `user_id`         BIGINT       NOT NULL                COMMENT '用户ID',
    `conversation_id` BIGINT       DEFAULT NULL            COMMENT '来源会话ID（溯源用，可空）',
    `kind`            VARCHAR(20)  DEFAULT 'fact'          COMMENT '记忆类型（fact稳定事实/preference偏好，当前只用 fact）',
    `content`         VARCHAR(500) NOT NULL                COMMENT '记忆内容（一句话事实）',
    `confidence`       DOUBLE       DEFAULT 0.8             COMMENT '置信度（0-1，摘要抽取默认 0.8）',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_conv` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 用户长期记忆表（P0-3）';
