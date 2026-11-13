-- ============================================================================
-- 基于AI的部门工作管理软件 - 精细化权限配置（T-404）
-- 说明: 多维度权限（部门/固定职位/临时职位）+ 权限点目录 + 权限模板 + 变更日志
-- 合并规则: 合并叠加；冲突时 临时职位 > 固定职位 > 部门维度（高维度 effect 覆盖低维度）
-- 灰度原则: 用户未命中任何维度分配记录时，不启用精细控制（行为与纯角色一致）
-- ============================================================================

USE `dept_mgmt`;
SET NAMES utf8mb4;

-- ============================================================================
-- 1. 职位定义表（固定职位与临时职位共用定义，区分方式见 sys_user_position.kind）
-- ============================================================================
DROP TABLE IF EXISTS `sys_position`;
CREATE TABLE `sys_position` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '职位ID',
    `position_code` VARCHAR(50)  NOT NULL                COMMENT '职位编码',
    `position_name` VARCHAR(100) NOT NULL                COMMENT '职位名称',
    `description`   VARCHAR(500) DEFAULT NULL            COMMENT '描述',
    `status`        TINYINT      DEFAULT 1               COMMENT '状态（0停用 1正常）',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_position_code` (`position_code`),
    KEY             `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='职位定义表（T-404）';

-- ============================================================================
-- 2. 用户职位关联表（FIXED 固定职位；TEMP 临时职位必须带有效期）
-- ============================================================================
DROP TABLE IF EXISTS `sys_user_position`;
CREATE TABLE `sys_user_position` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `user_id`       BIGINT   NOT NULL                COMMENT '用户ID',
    `position_id`   BIGINT   NOT NULL                COMMENT '职位ID',
    `position_kind` VARCHAR(10) NOT NULL             COMMENT '职位性质（FIXED固定 TEMP临时）',
    `start_time`    DATETIME DEFAULT NULL            COMMENT '生效开始时间（TEMP 必填）',
    `end_time`      DATETIME DEFAULT NULL            COMMENT '生效结束时间（TEMP 必填，到期自动失效）',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT  DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_position_id` (`position_id`),
    KEY `idx_kind` (`position_kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户职位关联表（T-404）';

-- ============================================================================
-- 3. 权限点目录表（MENU 菜单级 / BUTTON 按钮级 / API 接口级）
--    MENU code = 前端路由 path（如 /system/user）
--    BUTTON code = btn:<path去斜杠>:<action>；API code = api:<path去斜杠>:<action>
-- ============================================================================
DROP TABLE IF EXISTS `sys_perm_point`;
CREATE TABLE `sys_perm_point` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '权限点ID',
    `perm_code`   VARCHAR(120) NOT NULL                COMMENT '权限点编码（menu:路由path / btn:xxx / api:xxx）',
    `perm_name`   VARCHAR(100) NOT NULL                COMMENT '权限点名称',
    `perm_type`   VARCHAR(10)  NOT NULL                COMMENT '权限类型（MENU菜单 BUTTON按钮 API接口）',
    `parent_code` VARCHAR(120) DEFAULT NULL            COMMENT '父权限点编码（BUTTON/API 挂 MENU）',
    `sort_order`  INT          DEFAULT 0               COMMENT '排序号',
    `status`      TINYINT      DEFAULT 1               COMMENT '状态（0停用 1正常）',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`),
    KEY `idx_parent_code` (`parent_code`),
    KEY `idx_perm_type` (`perm_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点目录表（T-404）';

-- ============================================================================
-- 4. 权限分配表（部门维度 subject_type=DEPT；职位维度 subject_type=POSITION）
--    effect: GRANT 授予 / DENY 显式拒绝（冲突合并时高维度 effect 覆盖低维度）
-- ============================================================================
DROP TABLE IF EXISTS `sys_perm_assignment`;
CREATE TABLE `sys_perm_assignment` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分配ID',
    `subject_type` VARCHAR(10)  NOT NULL                COMMENT '主体类型（DEPT部门维度 POSITION职位维度）',
    `subject_id`   BIGINT       NOT NULL                COMMENT '主体ID（部门ID/职位ID）',
    `perm_code`    VARCHAR(120) NOT NULL                COMMENT '权限点编码',
    `perm_type`    VARCHAR(10)  NOT NULL                COMMENT '权限类型（MENU/BUTTON/API）',
    `effect`       VARCHAR(10)  NOT NULL DEFAULT 'GRANT' COMMENT '效果（GRANT授予 DENY拒绝）',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_subject_perm` (`subject_type`, `subject_id`, `perm_code`),
    KEY `idx_subject` (`subject_type`, `subject_id`),
    KEY `idx_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限分配表（T-404）';

-- ============================================================================
-- 5. 权限模板表 + 模板条目表（模板复用：一键将模板内容应用到某维度）
-- ============================================================================
DROP TABLE IF EXISTS `sys_perm_template`;
CREATE TABLE `sys_perm_template` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `template_code` VARCHAR(50)  NOT NULL                COMMENT '模板编码',
    `template_name` VARCHAR(100) NOT NULL                COMMENT '模板名称',
    `description`   VARCHAR(500) DEFAULT NULL            COMMENT '描述',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限模板表（T-404）';

DROP TABLE IF EXISTS `sys_perm_template_item`;
CREATE TABLE `sys_perm_template_item` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '条目ID',
    `template_id` BIGINT       NOT NULL                COMMENT '模板ID',
    `perm_code`   VARCHAR(120) NOT NULL                COMMENT '权限点编码',
    `perm_type`   VARCHAR(10)  NOT NULL                COMMENT '权限类型（MENU/BUTTON/API）',
    `effect`      VARCHAR(10)  NOT NULL DEFAULT 'GRANT' COMMENT '效果（GRANT/DENY）',
    `sort_order`  INT          DEFAULT 0               COMMENT '排序号',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_perm` (`template_id`, `perm_code`),
    KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限模板条目表（T-404）';

-- ============================================================================
-- 6. 权限变更日志表（审计：操作人/时间/主体/维度/变更前后内容）
-- ============================================================================
DROP TABLE IF EXISTS `sys_perm_change_log`;
CREATE TABLE `sys_perm_change_log` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `operator_id`  BIGINT       DEFAULT NULL            COMMENT '操作人ID',
    `operator_name` VARCHAR(50) DEFAULT NULL            COMMENT '操作人姓名（冗余）',
    `subject_type` VARCHAR(10)  NOT NULL                COMMENT '主体类型（USER用户视角 DEPT部门 POSITION职位）',
    `subject_id`   BIGINT       DEFAULT NULL            COMMENT '主体ID（用户ID/部门ID/职位ID）',
    `subject_name` VARCHAR(100) DEFAULT NULL            COMMENT '主体名称（冗余便于审计阅读）',
    `dimension`    VARCHAR(10)  DEFAULT NULL            COMMENT '变更维度（DEPT部门 FIXED固定职位 TEMP临时职位）',
    `operation`    VARCHAR(30)  NOT NULL                COMMENT '操作类型（GRANT/DENY/CLEAR/ASSIGN_POSITION/REMOVE_POSITION/APPLY_TEMPLATE/BATCH_GRANT/BATCH_CLEAR）',
    `perm_codes`   TEXT         DEFAULT NULL            COMMENT '本次变更涉及的权限点编码（JSON数组）',
    `before_json`  TEXT         DEFAULT NULL            COMMENT '变更前权限内容快照（JSON）',
    `after_json`   TEXT         DEFAULT NULL            COMMENT '变更后权限内容快照（JSON）',
    `remark`       VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_subject` (`subject_type`, `subject_id`),
    KEY `idx_operator` (`operator_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限变更日志表（T-404）';

-- ============================================================================
-- 种子数据：权限点目录
-- ============================================================================

-- ---------- MENU 权限点（code = 前端路由 path，与 AppLayout 菜单一致） ----------
INSERT INTO `sys_perm_point` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`) VALUES
('/dashboard',        '工作台',       'MENU', NULL, 1),
('/task',             '任务管理',     'MENU', NULL, 2),
('/project',          '项目管理',     'MENU', NULL, 3),
('/message',          '消息中心',     'MENU', NULL, 4),
('/tender',           '投标管理',     'MENU', NULL, 5),
('/contract',         '合同管理',     'MENU', NULL, 6),
('/design',           '设计管理',     'MENU', NULL, 7),
('/workshop',         '生产管理',     'MENU', NULL, 8),
('/delivery',         '交付管理',     'MENU', NULL, 9),
('/ecn',              '变更管理',     'MENU', NULL, 10),
('/cost',             '成本管理',     'MENU', NULL, 11),
('/aftermarket',      '售后管理',     'MENU', NULL, 12),
('/archive',          '档案管理',     'MENU', NULL, 13),
('/file/list',        '文件归档',     'MENU', NULL, 14),
('/event',            '事件审批',     'MENU', NULL, 15),
('/agent',            'Agent 工作台', 'MENU', NULL, 16),
('/ai',               'AI助手',       'MENU', NULL, 17),
('/kpi',              '绩效管理',     'MENU', NULL, 18),
('/lean',             '精益改进',     'MENU', NULL, 19),
('/lean/stream',      '价值流分析',   'MENU', NULL, 20),
('/system/user',      '用户管理',     'MENU', NULL, 21),
('/system/dept',      '组织架构',     'MENU', NULL, 22),
('/system/notice',    '通知公告',     'MENU', NULL, 23),
('/system/log',       '操作日志',     'MENU', NULL, 24),
('/system/ai-config', 'AI 服务配置',  'MENU', NULL, 25),
('/system/perm-config','权限配置',    'MENU', NULL, 26),
('/bonus',            '奖金管理',     'MENU', NULL, 27);

-- ---------- BUTTON/API 权限点（按 MENU 批量生成标准动作；/dashboard 与 /message 为只读页不生成 create/update/delete） ----------
-- BUTTON: 查看列表（除 /dashboard 外全部）
INSERT INTO `sys_perm_point` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT CONCAT('btn:', SUBSTRING(p.perm_code, 2), ':list'), CONCAT(p.perm_name, '·查看'), 'BUTTON', p.perm_code, 1
FROM `sys_perm_point` p WHERE p.perm_type = 'MENU' AND p.perm_code <> '/dashboard';

-- BUTTON/API: 创建/更新/删除（除 /dashboard /message 只读页外）
INSERT INTO `sys_perm_point` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT CONCAT('btn:', SUBSTRING(p.perm_code, 2), ':create'), CONCAT(p.perm_name, '·新建'), 'BUTTON', p.perm_code, 2
FROM `sys_perm_point` p
WHERE p.perm_type = 'MENU' AND p.perm_code NOT IN ('/dashboard', '/message');

INSERT INTO `sys_perm_point` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT CONCAT('btn:', SUBSTRING(p.perm_code, 2), ':update'), CONCAT(p.perm_name, '·编辑'), 'BUTTON', p.perm_code, 3
FROM `sys_perm_point` p
WHERE p.perm_type = 'MENU' AND p.perm_code NOT IN ('/dashboard', '/message');

INSERT INTO `sys_perm_point` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT CONCAT('btn:', SUBSTRING(p.perm_code, 2), ':delete'), CONCAT(p.perm_name, '·删除'), 'BUTTON', p.perm_code, 4
FROM `sys_perm_point` p
WHERE p.perm_type = 'MENU' AND p.perm_code NOT IN ('/dashboard', '/message');

-- API 权限点：与 BUTTON 同步生成（去掉 btn: 4字符前缀，code 前缀 api:）
INSERT INTO `sys_perm_point` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`)
SELECT CONCAT('api:', SUBSTRING(p.perm_code, 5)), CONCAT(p.perm_name, '·接口'), 'API', p.parent_code, p.sort_order + 100
FROM `sys_perm_point` p
WHERE p.perm_type = 'BUTTON' AND p.deleted = 0;

-- ---------- 特例权限点：用户管理重置密码/启停状态；权限配置管理动作 ----------
INSERT INTO `sys_perm_point` (`perm_code`, `perm_name`, `perm_type`, `parent_code`, `sort_order`) VALUES
('btn:system/user:reset-password', '用户管理·重置密码',    'BUTTON', '/system/user', 5),
('btn:system/user:status',         '用户管理·启停状态',    'BUTTON', '/system/user', 6),
('api:system/user:reset-password', '用户管理·重置密码接口', 'API',    '/system/user', 105),
('api:system/user:status',         '用户管理·启停状态接口', 'API',    '/system/user', 106),
('btn:system/perm-config:assign',   '权限配置·分配变更',   'BUTTON', '/system/perm-config', 5),
('btn:system/perm-config:preview',  '权限配置·权限预览',   'BUTTON', '/system/perm-config', 6),
('btn:system/perm-config:template', '权限配置·模板管理',   'BUTTON', '/system/perm-config', 7),
('btn:system/perm-config:position', '权限配置·职位管理',   'BUTTON', '/system/perm-config', 8),
('btn:system/perm-config:log',      '权限配置·日志审计',   'BUTTON', '/system/perm-config', 9),
('api:system/perm-config:assign',   '权限配置·分配变更接口', 'API',  '/system/perm-config', 105),
('api:system/perm-config:preview',  '权限配置·权限预览接口', 'API',  '/system/perm-config', 106),
('api:system/perm-config:template', '权限配置·模板管理接口', 'API',  '/system/perm-config', 107),
('api:system/perm-config:position', '权限配置·职位管理接口', 'API',  '/system/perm-config', 108),
('api:system/perm-config:log',      '权限配置·日志审计接口', 'API',  '/system/perm-config', 109);

-- ============================================================================
-- 种子数据：示例职位
-- ============================================================================
INSERT INTO `sys_position` (`position_code`, `position_name`, `description`) VALUES
('PRJ_SPECIALIST', '项目专员',   '项目管理组专员（固定职位示例）'),
('GA_SPECIALIST',  '技改专员',   '技改组专员（固定职位示例）'),
('TMP_REVIEWER',   '临时评审专家', '阶段性评审任务临时岗位（临时职位示例，需配置有效期）');

-- ============================================================================
-- 种子数据：权限模板
-- ============================================================================
INSERT INTO `sys_perm_template` (`template_code`, `template_name`, `description`) VALUES
('TPL_SYS_READONLY', '系统管理·只读模板', '仅授予系统管理域菜单与查看权限，适合审计/观察类岗位'),
('TPL_SYS_FULL',     '系统管理·完全模板', '授予系统管理域全部菜单/按钮/接口权限，适合系统管理员');

-- 只读模板条目：用户管理菜单+查看按钮+接口
INSERT INTO `sys_perm_template_item` (`template_id`, `perm_code`, `perm_type`, `effect`, `sort_order`)
SELECT t.id, '/system/user', 'MENU', 'GRANT', 1 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_READONLY'
UNION ALL
SELECT t.id, 'btn:system/user:list', 'BUTTON', 'GRANT', 2 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_READONLY'
UNION ALL
SELECT t.id, 'api:system/user:list', 'API', 'GRANT', 3 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_READONLY';

-- 完全模板条目：系统管理域全部标准动作（list/create/update/delete + 特例）
INSERT INTO `sys_perm_template_item` (`template_id`, `perm_code`, `perm_type`, `effect`, `sort_order`)
SELECT t.id, '/system/user', 'MENU', 'GRANT', 1 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, '/system/dept', 'MENU', 'GRANT', 2 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, '/system/notice', 'MENU', 'GRANT', 3 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, '/system/log', 'MENU', 'GRANT', 4 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, '/system/ai-config', 'MENU', 'GRANT', 5 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, '/system/perm-config', 'MENU', 'GRANT', 6 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/user:list', 'BUTTON', 'GRANT', 10 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/user:create', 'BUTTON', 'GRANT', 11 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/user:update', 'BUTTON', 'GRANT', 12 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/user:delete', 'BUTTON', 'GRANT', 13 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/user:reset-password', 'BUTTON', 'GRANT', 14 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/user:status', 'BUTTON', 'GRANT', 15 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/perm-config:assign', 'BUTTON', 'GRANT', 16 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/perm-config:preview', 'BUTTON', 'GRANT', 17 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/perm-config:template', 'BUTTON', 'GRANT', 18 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/perm-config:position', 'BUTTON', 'GRANT', 19 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL'
UNION ALL SELECT t.id, 'btn:system/perm-config:log', 'BUTTON', 'GRANT', 20 FROM `sys_perm_template` t WHERE t.template_code = 'TPL_SYS_FULL';

-- ============================================================================
-- 说明: 不预置任何 sys_perm_assignment 记录（灰度原则：未配置=不限制）
--       测试数据由集成测试/冒烟脚本通过接口动态创建
-- ============================================================================
