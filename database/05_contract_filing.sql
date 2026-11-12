-- ============================================================================
-- 合同建档扩展（T-407：建档更名 + 独立建档页 + AI 提取 + 权限脱敏）
-- 1. contract 表新增建档核心字段（AI 预填 + 人工校验编辑后入库）
-- 2. 合同域权限点：建档 / 关联管理 / 敏感字段明文查看
-- ============================================================================

USE `dept_mgmt`;
SET NAMES utf8mb4;

-- ---------- 1. contract 表结构扩展 ----------
ALTER TABLE `contract`
    ADD COLUMN `party_b`              VARCHAR(200)  DEFAULT NULL COMMENT '乙方主体（我方签约主体）' AFTER `customer_name`,
    ADD COLUMN `payment_terms`        VARCHAR(500)  DEFAULT NULL COMMENT '付款条件' AFTER `remark`,
    ADD COLUMN `payment_method`       VARCHAR(100)  DEFAULT NULL COMMENT '付款方式' AFTER `payment_terms`,
    ADD COLUMN `delivery_time`        VARCHAR(100)  DEFAULT NULL COMMENT '交货时间' AFTER `payment_method`,
    ADD COLUMN `delivery_place`       VARCHAR(200)  DEFAULT NULL COMMENT '交货地点' AFTER `delivery_time`,
    ADD COLUMN `contact_info`         VARCHAR(300)  DEFAULT NULL COMMENT '联系人信息（敏感：按权限脱敏）' AFTER `delivery_place`,
    ADD COLUMN `acceptance_condition` VARCHAR(500)  DEFAULT NULL COMMENT '验收条件' AFTER `contact_info`,
    ADD COLUMN `summary`              VARCHAR(1000) DEFAULT NULL COMMENT '合同摘要' AFTER `acceptance_condition`,
    ADD COLUMN `filing_main_file_id`  BIGINT        DEFAULT NULL COMMENT '建档主文件归档ID（文件域 file.id）' AFTER `summary`;

-- ---------- 2. 合同域权限点（T-404 体系，灰度原则：未配置不拦截；口径与 live 库 14 点一致） ----------
INSERT INTO `sys_perm_point` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`) VALUES
('btn:contract:create',            '合同管理·新建合同',       'BUTTON', '/contract', 1),
('btn:contract:list',              '合同管理·查询',           'BUTTON', '/contract', 2),
('btn:contract:update',            '合同管理·编辑',           'BUTTON', '/contract', 3),
('btn:contract:delete',            '合同管理·删除',           'BUTTON', '/contract', 4),
('btn:contract:filing',            '合同管理·建档',           'BUTTON', '/contract', 5),
('btn:contract:link',              '合同管理·关联项目管理',   'BUTTON', '/contract', 6),
('btn:contract:sensitive:view',    '合同管理·敏感字段明文',   'BUTTON', '/contract', 7),
('api:contract:create',            '合同管理·创建接口',       'API',    '/contract', 101),
('api:contract:list',              '合同管理·查询接口',       'API',    '/contract', 102),
('api:contract:update',            '合同管理·更新接口',       'API',    '/contract', 103),
('api:contract:delete',            '合同管理·删除接口',       'API',    '/contract', 104),
('api:contract:filing',            '合同管理·建档接口',       'API',    '/contract', 105),
('api:contract:link',              '合同管理·关联管理接口',   'API',    '/contract', 106),
('api:contract:sensitive:view',    '合同管理·敏感字段明文接口', 'API',  '/contract', 107);
