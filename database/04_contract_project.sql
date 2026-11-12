-- ============================================================================
-- 合同-项目关联关系表（T-406 合同域 × 项目域双向关联）
-- 关联规则：
--   1. 仅 EFFECTIVE（已生效）合同允许建立关联；绑定/解除均记录操作人与时间
--   2. 一份合同可关联多个项目（1:N）；一个项目也可关联多份合同（如销售合同+采购合同）
--   3. 合同状态变更（生效/关闭/终止）经事件总线通知关联项目负责人（状态联动）
--   4. 项目阶段完成/里程碑完成经事件总线反馈至合同侧（项目进度联动）
-- ============================================================================

USE `dept_mgmt`;
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `contract_project`;
CREATE TABLE `contract_project` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `contract_id` BIGINT       NOT NULL                COMMENT '合同ID',
    `project_id`  BIGINT       NOT NULL                COMMENT '项目ID',
    `bind_by`     VARCHAR(50)  DEFAULT NULL            COMMENT '绑定操作人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_contract_project` (`contract_id`, `project_id`),
    KEY `idx_cp_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '合同-项目关联表';
