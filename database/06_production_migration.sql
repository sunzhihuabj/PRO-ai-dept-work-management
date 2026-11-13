-- ============================================================================
-- 06_production_migration.sql — T-412 样机管理 → 生产管理 数据迁移脚本
-- 执行前提：backend 服务停止或短暂窗口期（本迁移仅 DDL/术语更新，耗时秒级）
-- 幂等性：RENAME 前做存在性判断；UPDATE 天然幂等
-- ============================================================================

-- 1. 验收表重命名：prototype_acceptance → production_acceptance（历史数据原样保留）
SET @tbl_exists = (SELECT COUNT(*) FROM information_schema.tables
                   WHERE table_schema = 'dept_mgmt' AND table_name = 'prototype_acceptance');
SET @ddl = IF(@tbl_exists > 0,
              'RENAME TABLE `prototype_acceptance` TO `production_acceptance`',
              'SELECT ''prototype_acceptance 不存在，跳过重命名'' AS info');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 验收表注释更新（表注释 + 工单列注释）
ALTER TABLE `production_acceptance` COMMENT = '生产完工验收表（BR-01 数据源）';
ALTER TABLE `production_acceptance`
    MODIFY COLUMN `workorder_id` BIGINT NOT NULL COMMENT '生产工单ID';

-- 3. 生产工单类型枚举值迁移：PROTOTYPE(样机) → TRIAL(试制)；默认值与列注释同步
UPDATE `work_order` SET `order_type` = 'TRIAL' WHERE `order_type` = 'PROTOTYPE';
ALTER TABLE `work_order`
    MODIFY COLUMN `order_type` VARCHAR(20) NOT NULL DEFAULT 'TRIAL' COMMENT '类型（TRIAL试制/BATCH批量）';

-- 4. AI 能力点描述：AGT-13~18 W4 样机域 → W4 生产域
UPDATE `sys_capability`
SET `remark` = REPLACE(`remark`, 'W4 样机域', 'W4 生产域')
WHERE `capability_code` IN ('AGT-13', 'AGT-14', 'AGT-15', 'AGT-16', 'AGT-17', 'AGT-18');

-- 5. 精细权限点名称：样机管理 → 生产管理（perm_code 前缀 workshop 保留，向后兼容既有分配数据）
UPDATE `sys_perm_point`
SET `perm_name` = REPLACE(`perm_name`, '样机管理', '生产管理')
WHERE `perm_code` IN ('/workshop', 'btn:workshop:list', 'btn:workshop:create', 'btn:workshop:update',
                      'btn:workshop:delete', 'api:workshop:list', 'api:workshop:create',
                      'api:workshop:update', 'api:workshop:delete');

-- 6. 编号规则备注
UPDATE `sys_code_rule`
SET `remark` = REPLACE(`remark`, 'W4 样机域', 'W4 生产域')
WHERE `rule_code` IN ('PO', 'WO');

-- ============================================================================
-- 迁移后校验（期望：全部为 0 或 1，见注释）
-- ============================================================================
-- 7.1 新表存在（期望 1）
SELECT COUNT(*) AS chk_new_table_exists
FROM information_schema.tables
WHERE table_schema = 'dept_mgmt' AND table_name = 'production_acceptance';

-- 7.2 旧表不存在（期望 0）
SELECT COUNT(*) AS chk_old_table_gone
FROM information_schema.tables
WHERE table_schema = 'dept_mgmt' AND table_name = 'prototype_acceptance';

-- 7.3 工单类型值域收敛（期望 0：不存在 TRIAL/BATCH 之外的值）
SELECT COUNT(*) AS chk_order_type_domain
FROM `work_order` WHERE `order_type` NOT IN ('TRIAL', 'BATCH');

-- 7.4 能力点残留（期望 0）
SELECT COUNT(*) AS chk_capability_leftover
FROM `sys_capability` WHERE `remark` LIKE '%样机%';

-- 7.5 权限点残留（期望 0）
SELECT COUNT(*) AS chk_perm_leftover
FROM `sys_perm_point`
WHERE `perm_name` LIKE '%样机%' AND `perm_code` LIKE '%workshop%';

-- 7.6 迁移后数据快照（历史数据行数核对）
SELECT COUNT(*) AS production_acceptance_rows FROM `production_acceptance` WHERE `deleted` = 0;
SELECT COUNT(*) AS work_order_rows FROM `work_order` WHERE `deleted` = 0;
