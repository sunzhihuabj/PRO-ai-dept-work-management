-- ============================================================================
-- 基于AI的部门工作管理软件 - 数据库表结构DDL脚本
-- 数据库: MySQL 8.0
-- 字符集: utf8mb4
-- 生成日期: 2026-08-13
-- 说明: 包含37张业务表，对应需求文档第9节
-- ============================================================================

-- 创建数据库
DROP DATABASE IF EXISTS `dept_mgmt`;
CREATE DATABASE `dept_mgmt` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `dept_mgmt`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 9.1 系统管理模块
-- ============================================================================

DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`      VARCHAR(50)  NOT NULL                COMMENT '登录账号',
    `password`      VARCHAR(200) NOT NULL                COMMENT '密码（BCrypt加密）',
    `real_name`     VARCHAR(50)  DEFAULT NULL            COMMENT '真实姓名',
    `dept_id`       BIGINT       DEFAULT NULL            COMMENT '所属部门ID',
    `phone`         VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    `email`         VARCHAR(100) DEFAULT NULL            COMMENT '邮箱',
    `avatar`        VARCHAR(500) DEFAULT NULL            COMMENT '头像URL',
    `wx_openid`     VARCHAR(64)  DEFAULT NULL            COMMENT '微信openid',
    UNIQUE KEY `uk_wx_openid` (`wx_openid`),
    `role_codes`    JSON         DEFAULT NULL            COMMENT '角色编码列表（JSON数组）',
    `identity`      VARCHAR(20)  NOT NULL DEFAULT 'WORKER' COMMENT '三身份（T-102）：EXPERT专家/WORKER工作者；ASSISTANT为AI虚拟身份不落人类账号',
    `status`        TINYINT      DEFAULT 1               COMMENT '状态（0停用 1正常）',
    `login_ip`      VARCHAR(50)  DEFAULT NULL            COMMENT '最后登录IP',
    `login_date`    DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
    `pwd_update_time` DATETIME   DEFAULT NULL            COMMENT '密码最后修改时间',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY             `idx_status` (`status`),
    KEY             `idx_real_name` (`real_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_code`     VARCHAR(50)  NOT NULL                COMMENT '角色编码',
    `role_name`     VARCHAR(100) NOT NULL                COMMENT '角色名称',
    `role_sort`     INT          DEFAULT 0               COMMENT '排序号',
    `status`        TINYINT      DEFAULT 1               COMMENT '状态（0停用 1正常）',
    `remark`        VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    KEY             `idx_role_sort` (`role_sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `user_id`       BIGINT   NOT NULL                COMMENT '用户ID',
    `role_id`       BIGINT   NOT NULL                COMMENT '角色ID',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT  DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY             `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `menu_name`     VARCHAR(50)  NOT NULL                COMMENT '菜单名称',
    `parent_id`     BIGINT       DEFAULT 0               COMMENT '父菜单ID（0为顶级）',
    `order_num`     INT          DEFAULT 0               COMMENT '排序号',
    `path`          VARCHAR(200) DEFAULT NULL            COMMENT '路由地址',
    `component`     VARCHAR(255) DEFAULT NULL            COMMENT '组件路径',
    `query`         VARCHAR(255) DEFAULT NULL            COMMENT '路由参数',
    `route_name`    VARCHAR(50)  DEFAULT NULL            COMMENT '路由名称',
    `is_frame`      TINYINT      DEFAULT 1               COMMENT '是否外链（0是 1否）',
    `is_cache`      TINYINT      DEFAULT 0               COMMENT '是否缓存（0不缓存 1缓存）',
    `menu_type`     CHAR(1)      DEFAULT ''              COMMENT '菜单类型（M目录 C菜单 F按钮）',
    `visible`       TINYINT      DEFAULT 1               COMMENT '是否可见（0隐藏 1显示）',
    `status`        TINYINT      DEFAULT 1               COMMENT '状态（0停用 1正常）',
    `perms`         VARCHAR(100) DEFAULT NULL            COMMENT '权限标识',
    `icon`          VARCHAR(100) DEFAULT NULL            COMMENT '菜单图标',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY             `idx_parent_id` (`parent_id`),
    KEY             `idx_order_num` (`order_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单表';

DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `role_id`       BIGINT   NOT NULL                COMMENT '角色ID',
    `menu_id`       BIGINT   NOT NULL                COMMENT '菜单ID',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT  DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    KEY             `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    `parent_id`     BIGINT       DEFAULT 0               COMMENT '父部门ID（0为顶级）',
    `ancestors`     VARCHAR(500) DEFAULT ''              COMMENT '祖级列表（逗号分隔）',
    `dept_name`     VARCHAR(100) NOT NULL                COMMENT '部门名称',
    `order_num`     INT          DEFAULT 0               COMMENT '排序号',
    `leader`        VARCHAR(50)  DEFAULT NULL            COMMENT '负责人',
    `phone`         VARCHAR(20)  DEFAULT NULL            COMMENT '联系电话',
    `email`         VARCHAR(100) DEFAULT NULL            COMMENT '邮箱',
    `status`        TINYINT      DEFAULT 1               COMMENT '状态（0停用 1正常）',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY             `idx_parent_id` (`parent_id`),
    KEY             `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `title`           VARCHAR(100) DEFAULT NULL            COMMENT '操作模块',
    `business_type`   INT          DEFAULT 0               COMMENT '业务类型（0其它 1新增 2修改 3删除）',
    `method`          VARCHAR(200) DEFAULT NULL            COMMENT '请求方法',
    `request_method`  VARCHAR(10)  DEFAULT NULL            COMMENT '请求方式（GET/POST/PUT/DELETE）',
    `operator_type`   INT          DEFAULT 0               COMMENT '操作人类别（0其它 1后台 2移动端）',
    `oper_name`       VARCHAR(50)  DEFAULT NULL            COMMENT '操作人',
    `dept_name`       VARCHAR(100) DEFAULT NULL            COMMENT '部门名称',
    `oper_url`        VARCHAR(500) DEFAULT NULL            COMMENT '请求URL',
    `oper_ip`         VARCHAR(50)  DEFAULT NULL            COMMENT '操作IP',
    `oper_location`   VARCHAR(100) DEFAULT NULL            COMMENT '操作地点',
    `oper_param`      TEXT         DEFAULT NULL            COMMENT '请求参数',
    `json_result`     TEXT         DEFAULT NULL            COMMENT '返回结果',
    `status`          TINYINT      DEFAULT 1               COMMENT '状态（0失败 1成功）',
    `error_msg`       TEXT         DEFAULT NULL            COMMENT '错误信息',
    `oper_time`       DATETIME     DEFAULT NULL            COMMENT '操作时间',
    `cost_time`       BIGINT       DEFAULT 0               COMMENT '耗时（毫秒）',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_oper_name` (`oper_name`),
    KEY               `idx_oper_time` (`oper_time`),
    KEY               `idx_business_type` (`business_type`),
    KEY               `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';

-- ============================================================================
-- 9.2 项目管理模块
-- ============================================================================

DROP TABLE IF EXISTS `project`;
CREATE TABLE `project` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '项目ID',
    `project_code`      VARCHAR(50)   NOT NULL                COMMENT '项目编号',
    `project_name`      VARCHAR(200)  NOT NULL                COMMENT '项目名称',
    `project_type`      TINYINT       NOT NULL                COMMENT '项目类型（1整机厂 2技改 3研发）',
    `customer_name`     VARCHAR(200)  DEFAULT NULL            COMMENT '客户名称',
    `project_leader`    VARCHAR(50)   DEFAULT NULL            COMMENT '项目负责人',
    `status`            TINYINT       DEFAULT 0               COMMENT '状态（0待启动 1进行中 2已完成 3已关闭 4暂停）',
    `contract_amount`   DECIMAL(18,2) DEFAULT NULL            COMMENT '合同金额',
    `target_cost`       DECIMAL(18,2) DEFAULT NULL            COMMENT '目标成本',
    `plan_start_date`   DATE          DEFAULT NULL            COMMENT '计划开始日期',
    `plan_end_date`     DATE          DEFAULT NULL            COMMENT '计划结束日期',
    `actual_start_date` DATE          DEFAULT NULL            COMMENT '实际开始日期',
    `actual_end_date`   DATE          DEFAULT NULL            COMMENT '实际结束日期',
    `description`       TEXT          DEFAULT NULL            COMMENT '项目描述',
    `create_by`         VARCHAR(50)   DEFAULT NULL            COMMENT '创建人',
    `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_code` (`project_code`),
    KEY                 `idx_project_type` (`project_type`),
    KEY                 `idx_status` (`status`),
    KEY                 `idx_project_leader` (`project_leader`),
    KEY                 `idx_plan_start_date` (`plan_start_date`),
    KEY                 `idx_plan_end_date` (`plan_end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

DROP TABLE IF EXISTS `project_phase`;
CREATE TABLE `project_phase` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '阶段ID',
    `project_id`        BIGINT       NOT NULL                COMMENT '项目ID',
    `phase_name`        VARCHAR(100) NOT NULL                COMMENT '阶段名称',
    `phase_order`       INT          DEFAULT 0               COMMENT '阶段顺序',
    `phase_type`        TINYINT      DEFAULT NULL            COMMENT '阶段类型（1整机厂 2技改 3研发）',
    `plan_start_date`   DATE         DEFAULT NULL            COMMENT '计划开始日期',
    `plan_end_date`     DATE         DEFAULT NULL            COMMENT '计划结束日期',
    `actual_start_date` DATE         DEFAULT NULL            COMMENT '实际开始日期',
    `actual_end_date`   DATE         DEFAULT NULL            COMMENT '实际结束日期',
    `status`            TINYINT      DEFAULT 0               COMMENT '状态（0未开始 1进行中 2已完成）',
    `description`       VARCHAR(500) DEFAULT NULL            COMMENT '阶段描述',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY                 `idx_project_id` (`project_id`),
    KEY                 `idx_phase_order` (`project_id`, `phase_order`),
    KEY                 `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目阶段表';

DROP TABLE IF EXISTS `project_member`;
CREATE TABLE `project_member` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '成员ID',
    `project_id`    BIGINT      NOT NULL                COMMENT '项目ID',
    `user_id`       BIGINT      NOT NULL                COMMENT '用户ID',
    `role_type`     VARCHAR(50) DEFAULT NULL            COMMENT '项目角色（项目经理/技术负责人/成员等）',
    `join_time`     DATETIME    DEFAULT NULL            COMMENT '加入时间',
    `leave_time`    DATETIME    DEFAULT NULL            COMMENT '离开时间',
    `status`        TINYINT     DEFAULT 1               COMMENT '状态（0已离开 1在项目中）',
    `create_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT     DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_user` (`project_id`, `user_id`),
    KEY             `idx_user_id` (`user_id`),
    KEY             `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';

DROP TABLE IF EXISTS `project_station`;
CREATE TABLE `project_station` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工位ID',
    `project_id`    BIGINT       NOT NULL                COMMENT '项目ID',
    `station_name`  VARCHAR(100) NOT NULL                COMMENT '工位名称',
    `station_order` INT          DEFAULT 0               COMMENT '工位顺序',
    `description`   VARCHAR(500) DEFAULT NULL            COMMENT '工位描述',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY             `idx_project_id` (`project_id`),
    KEY             `idx_station_order` (`project_id`, `station_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目工位表';

DROP TABLE IF EXISTS `milestone`;
CREATE TABLE `milestone` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '里程碑ID',
    `project_id`      BIGINT       NOT NULL                COMMENT '项目ID',
    `phase_id`        BIGINT       DEFAULT NULL            COMMENT '阶段ID',
    `milestone_name`  VARCHAR(200) NOT NULL                COMMENT '里程碑名称',
    `plan_date`       DATE         DEFAULT NULL            COMMENT '计划日期',
    `actual_date`     DATE         DEFAULT NULL            COMMENT '实际完成日期',
    `status`          TINYINT      DEFAULT 0               COMMENT '状态（0未开始 1已完成 2延期）',
    `description`     VARCHAR(500) DEFAULT NULL            COMMENT '描述',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_project_id` (`project_id`),
    KEY               `idx_phase_id` (`phase_id`),
    KEY               `idx_plan_date` (`plan_date`),
    KEY               `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='里程碑表';

DROP TABLE IF EXISTS `phase_gate`;
CREATE TABLE `phase_gate` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '门禁ID',
    `project_id`      BIGINT       NOT NULL                COMMENT '项目ID',
    `phase_id`        BIGINT       NOT NULL                COMMENT '阶段ID',
    `gate_name`       VARCHAR(200) NOT NULL                COMMENT '门禁名称',
    `gate_order`      INT          DEFAULT 0               COMMENT '门禁顺序',
    `pass_condition`  TEXT         DEFAULT NULL            COMMENT '通过条件',
    `audit_result`    TINYINT      DEFAULT NULL            COMMENT '审核结果（0待审核 1通过 2驳回）',
    `audit_by`        VARCHAR(50)  DEFAULT NULL            COMMENT '审核人',
    `audit_time`      DATETIME     DEFAULT NULL            COMMENT '审核时间',
    `audit_comment`   VARCHAR(500) DEFAULT NULL            COMMENT '审核意见',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_project_id` (`project_id`),
    KEY               `idx_phase_id` (`phase_id`),
    KEY               `idx_audit_result` (`audit_result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阶段门禁表';

DROP TABLE IF EXISTS `deliverable_template`;
CREATE TABLE `deliverable_template` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `template_name`   VARCHAR(200) NOT NULL                COMMENT '模板名称',
    `project_type`    TINYINT      DEFAULT NULL            COMMENT '项目类型（1整机厂 2技改 3研发）',
    `phase_order`     INT          DEFAULT 0               COMMENT '所属阶段顺序号',
    `phase_name`      VARCHAR(100) DEFAULT NULL            COMMENT '所属阶段名称',
    `file_type`       VARCHAR(50)  DEFAULT NULL            COMMENT '文件类型（docx/xlsx/pdf/dwg等）',
    `required`        TINYINT      DEFAULT 1               COMMENT '是否必需（0可选 1必需）',
    `description`     VARCHAR(500) DEFAULT NULL            COMMENT '模板说明',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_project_type` (`project_type`),
    KEY               `idx_phase_order` (`project_type`, `phase_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付物模板表';

DROP TABLE IF EXISTS `deliverable_checklist`;
CREATE TABLE `deliverable_checklist` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '清单ID',
    `project_id`        BIGINT       NOT NULL                COMMENT '项目ID',
    `phase_id`          BIGINT       DEFAULT NULL            COMMENT '阶段ID',
    `template_id`       BIGINT       DEFAULT NULL            COMMENT '模板ID',
    `deliverable_name`  VARCHAR(200) NOT NULL                COMMENT '交付物名称',
    `required`          TINYINT      DEFAULT 1               COMMENT '是否必需（0可选 1必需）',
    `file_type`         VARCHAR(50)  DEFAULT NULL            COMMENT '文件类型',
    `uploaded`          TINYINT      DEFAULT 0               COMMENT '是否已上传（0未上传 1已上传）',
    `file_id`           BIGINT       DEFAULT NULL            COMMENT '关联文件ID',
    `checked`           TINYINT      DEFAULT 0               COMMENT '是否已检查（0未检查 1已检查）',
    `check_time`        DATETIME     DEFAULT NULL            COMMENT '检查时间',
    `check_by`          VARCHAR(50)  DEFAULT NULL            COMMENT '检查人',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY                 `idx_project_id` (`project_id`),
    KEY                 `idx_phase_id` (`phase_id`),
    KEY                 `idx_template_id` (`template_id`),
    KEY                 `idx_checked` (`project_id`, `checked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交付物检查清单表';

-- ============================================================================
-- 9.3 任务管理模块
-- ============================================================================

DROP TABLE IF EXISTS `task`;
CREATE TABLE `task` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `project_id`        BIGINT        NOT NULL                COMMENT '项目ID',
    `phase_id`          BIGINT        DEFAULT NULL            COMMENT '阶段ID',
    `task_name`         VARCHAR(200)  NOT NULL                COMMENT '任务名称',
    `task_type`         VARCHAR(50)   DEFAULT NULL            COMMENT '任务类型',
    `value_type`        TINYINT       DEFAULT NULL            COMMENT '价值类型（1VA 2NNVA 3NVA）',
    `waste_type`        VARCHAR(50)   DEFAULT NULL            COMMENT '浪费类型',
    `assignee_id`       BIGINT        DEFAULT NULL            COMMENT '负责人ID',
    `collaborator_ids`  JSON          DEFAULT NULL            COMMENT '协作者ID列表（JSON数组）',
    `priority`          TINYINT       DEFAULT 2               COMMENT '优先级（1高 2中 3低）',
    `status`            TINYINT       DEFAULT 0               COMMENT '状态（0待办 1进行中 2待审核 3已完成 4已关闭）',
    `progress`          INT           DEFAULT 0               COMMENT '进度百分比（0-100）',
    `plan_hours`        DECIMAL(8,2)  DEFAULT NULL            COMMENT '计划工时',
    `actual_hours`      DECIMAL(8,2)  DEFAULT NULL            COMMENT '实际工时',
    `start_date`        DATE          DEFAULT NULL            COMMENT '计划开始日期',
    `deadline`          DATE          DEFAULT NULL            COMMENT '截止日期',
    `actual_start_time` DATETIME      DEFAULT NULL            COMMENT '实际开始时间',
    `complete_time`     DATETIME      DEFAULT NULL            COMMENT '实际完成时间',
    `description`       TEXT          DEFAULT NULL            COMMENT '任务描述',
    `pull_mode`         TINYINT       DEFAULT 0               COMMENT '是否拉动模式（0否 1是）',
    `create_by`         VARCHAR(50)   DEFAULT NULL            COMMENT '创建人',
    `create_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY                 `idx_project_id` (`project_id`),
    KEY                 `idx_phase_id` (`phase_id`),
    KEY                 `idx_assignee_id` (`assignee_id`),
    KEY                 `idx_status` (`status`),
    KEY                 `idx_priority` (`priority`),
    KEY                 `idx_deadline` (`deadline`),
    KEY                 `idx_project_status` (`project_id`, `status`),
    KEY                 `idx_assignee_status` (`assignee_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';

DROP TABLE IF EXISTS `task_sub`;
CREATE TABLE `task_sub` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '子任务ID',
    `task_id`         BIGINT        NOT NULL                COMMENT '父任务ID',
    `sub_name`        VARCHAR(200)  NOT NULL                COMMENT '子任务名称',
    `assignee_id`     BIGINT        DEFAULT NULL            COMMENT '负责人ID',
    `status`          TINYINT       DEFAULT 0               COMMENT '状态（0待办 1进行中 2已完成）',
    `plan_hours`      DECIMAL(8,2)  DEFAULT NULL            COMMENT '计划工时',
    `actual_hours`    DECIMAL(8,2)  DEFAULT NULL            COMMENT '实际工时',
    `deadline`        DATE          DEFAULT NULL            COMMENT '截止日期',
    `complete_time`   DATETIME      DEFAULT NULL            COMMENT '完成时间',
    `order_num`       INT           DEFAULT 0               COMMENT '排序号',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_task_id` (`task_id`),
    KEY               `idx_assignee_id` (`assignee_id`),
    KEY               `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='子任务表';

DROP TABLE IF EXISTS `task_log`;
CREATE TABLE `task_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `task_id`       BIGINT       NOT NULL                COMMENT '任务ID',
    `oper_type`     VARCHAR(50)  DEFAULT NULL            COMMENT '操作类型（创建/分配/状态变更/编辑等）',
    `oper_desc`     VARCHAR(500) DEFAULT NULL            COMMENT '操作描述',
    `oper_by`       VARCHAR(50)  DEFAULT NULL            COMMENT '操作人',
    `oper_time`     DATETIME     DEFAULT NULL            COMMENT '操作时间',
    `old_value`     TEXT         DEFAULT NULL            COMMENT '变更前值（JSON）',
    `new_value`     TEXT         DEFAULT NULL            COMMENT '变更后值（JSON）',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY             `idx_task_id` (`task_id`),
    KEY             `idx_oper_time` (`oper_time`),
    KEY             `idx_oper_by` (`oper_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务日志表';

DROP TABLE IF EXISTS `task_comment`;
CREATE TABLE `task_comment` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `task_id`       BIGINT   NOT NULL                COMMENT '任务ID',
    `user_id`       BIGINT   NOT NULL                COMMENT '评论人ID',
    `content`       TEXT     NOT NULL                COMMENT '评论内容',
    `parent_id`     BIGINT   DEFAULT 0               COMMENT '父评论ID（0为顶级评论）',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT  DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY             `idx_task_id` (`task_id`),
    KEY             `idx_user_id` (`user_id`),
    KEY             `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务评论表';

DROP TABLE IF EXISTS `work_hour`;
CREATE TABLE `work_hour` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '工时ID',
    `task_id`       BIGINT        NOT NULL                COMMENT '任务ID',
    `user_id`       BIGINT        NOT NULL                COMMENT '用户ID',
    `work_date`     DATE          NOT NULL                COMMENT '工作日期',
    `hours`         DECIMAL(6,2)  NOT NULL                COMMENT '工时数',
    `description`   VARCHAR(500)  DEFAULT NULL            COMMENT '工作描述',
    `status`        TINYINT       DEFAULT 1               COMMENT '状态（0无效 1有效）',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY             `idx_task_id` (`task_id`),
    KEY             `idx_user_id` (`user_id`),
    KEY             `idx_work_date` (`work_date`),
    KEY             `idx_user_date` (`user_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工时表';

DROP TABLE IF EXISTS `task_pool`;
CREATE TABLE `task_pool` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '任务池ID',
    `task_name`     VARCHAR(200)  NOT NULL                COMMENT '任务名称',
    `task_type`     VARCHAR(50)   DEFAULT NULL            COMMENT '任务类型',
    `project_type`  TINYINT       DEFAULT NULL            COMMENT '适用项目类型（1整机厂 2技改 3研发）',
    `phase_order`   INT           DEFAULT NULL            COMMENT '适用阶段顺序',
    `value_type`    TINYINT       DEFAULT NULL            COMMENT '价值类型（1VA 2NNVA 3NVA）',
    `plan_hours`    DECIMAL(8,2)  DEFAULT NULL            COMMENT '标准工时',
    `description`   VARCHAR(500)  DEFAULT NULL            COMMENT '任务描述',
    `status`        TINYINT       DEFAULT 1               COMMENT '状态（0停用 1启用）',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY             `idx_project_type` (`project_type`),
    KEY             `idx_phase_order` (`project_type`, `phase_order`),
    KEY             `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务池表';

-- ============================================================================
-- 9.4 文件与归档模块
-- ============================================================================

DROP TABLE IF EXISTS `file_info`;
CREATE TABLE `file_info` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `project_id`      BIGINT       DEFAULT NULL            COMMENT '项目ID',
    `phase_id`        BIGINT       DEFAULT NULL            COMMENT '阶段ID',
    `task_id`         BIGINT       DEFAULT NULL            COMMENT '来源任务ID（任务上传的资料）',
    `file_name`       VARCHAR(200) NOT NULL                COMMENT '文件名称',
    `file_path`       VARCHAR(500) NOT NULL                COMMENT '文件存储路径',
    `file_size`       BIGINT       DEFAULT 0               COMMENT '文件大小（字节）',
    `file_type`       VARCHAR(50)  DEFAULT NULL            COMMENT '文件类型（docx/xlsx/pdf/dwg等）',
    `version`         VARCHAR(50)  DEFAULT '1.0'           COMMENT '版本号',
    `version_number`  INT          DEFAULT 1               COMMENT '版本序号',
    `is_archive`      TINYINT      DEFAULT 0               COMMENT '是否已归档（0否 1是）',
    `archive_time`    DATETIME     DEFAULT NULL            COMMENT '归档时间',
    `description`     VARCHAR(500) DEFAULT NULL            COMMENT '文件描述',
    `create_by`       VARCHAR(50)  DEFAULT NULL            COMMENT '创建人',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_project_id` (`project_id`),
    KEY               `idx_phase_id` (`phase_id`),
    KEY               `idx_task_id` (`task_id`),
    KEY               `idx_file_type` (`file_type`),
    KEY               `idx_is_archive` (`is_archive`),
    KEY               `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件信息表';

DROP TABLE IF EXISTS `file_version`;
CREATE TABLE `file_version` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '版本ID',
    `file_id`         BIGINT       NOT NULL                COMMENT '文件ID',
    `version_number`  INT          NOT NULL                COMMENT '版本序号',
    `version_label`   VARCHAR(50)  DEFAULT NULL            COMMENT '版本标签（如V1.0/V2.0）',
    `file_path`       VARCHAR(500) NOT NULL                COMMENT '版本文件存储路径',
    `file_size`       BIGINT       DEFAULT 0               COMMENT '文件大小（字节）',
    `change_desc`     VARCHAR(500) DEFAULT NULL            COMMENT '变更说明',
    `create_by`       VARCHAR(50)  DEFAULT NULL            COMMENT '创建人',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_file_version` (`file_id`, `version_number`),
    KEY               `idx_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件版本表';

DROP TABLE IF EXISTS `file_category`;
CREATE TABLE `file_category` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `parent_id`       BIGINT       DEFAULT 0               COMMENT '父分类ID（0为顶级）',
    `category_name`   VARCHAR(100) NOT NULL                COMMENT '分类名称',
    `category_code`   VARCHAR(50)  DEFAULT NULL            COMMENT '分类编码',
    `order_num`       INT          DEFAULT 0               COMMENT '排序号',
    `status`          TINYINT      DEFAULT 1               COMMENT '状态（0停用 1启用）',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_parent_id` (`parent_id`),
    KEY               `idx_category_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件分类表';

-- ============================================================================
-- 9.4 档案归档（文件审核）模块
-- ============================================================================
DROP TABLE IF EXISTS `file_audit`;
CREATE TABLE `file_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `file_id` BIGINT NOT NULL COMMENT '关联的文件ID',
    `auditor_id` BIGINT NOT NULL COMMENT '审核人ID（对应 sys_user.id）',
    `status` VARCHAR(20) NOT NULL COMMENT '审核状态（APPROVED / REJECTED）',
    `comment` VARCHAR(500) DEFAULT NULL COMMENT '审核意见',
    `audit_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_file_id` (`file_id`),
    KEY `idx_auditor_id` (`auditor_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件审核记录表';

DROP TABLE IF EXISTS `archive_record`;
CREATE TABLE `archive_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '归档ID',
    `project_id`      BIGINT       NOT NULL                COMMENT '项目ID',
    `file_id`         BIGINT       DEFAULT NULL            COMMENT '文件ID',
    `archive_type`    VARCHAR(50)  DEFAULT NULL            COMMENT '归档类型（电子/纸质/两者）',
    `archive_status`  TINYINT      DEFAULT 0               COMMENT '归档状态（0未归档 1已归档 2归档中）',
    `archive_by`      VARCHAR(50)  DEFAULT NULL            COMMENT '归档人',
    `archive_time`    DATETIME     DEFAULT NULL            COMMENT '归档时间',
    `description`     VARCHAR(500) DEFAULT NULL            COMMENT '归档说明',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_project_id` (`project_id`),
    KEY               `idx_file_id` (`file_id`),
    KEY               `idx_archive_status` (`archive_status`),
    KEY               `idx_archive_time` (`archive_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='归档记录表';

-- ============================================================================
-- 9.5 审核与事件模块
-- ============================================================================

DROP TABLE IF EXISTS `audit_flow`;
CREATE TABLE `audit_flow` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '流程ID',
    `flow_name`     VARCHAR(200) NOT NULL                COMMENT '流程名称',
    `flow_type`     VARCHAR(50)  DEFAULT NULL            COMMENT '流程类型（交付物审核/门禁审核/事件审核等）',
    `project_type`  TINYINT      DEFAULT NULL            COMMENT '适用项目类型（1整机厂 2技改 3研发）',
    `status`        TINYINT      DEFAULT 1               COMMENT '状态（0停用 1启用）',
    `description`   VARCHAR(500) DEFAULT NULL            COMMENT '流程描述',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY             `idx_flow_type` (`flow_type`),
    KEY             `idx_project_type` (`project_type`),
    KEY             `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核流程表';

DROP TABLE IF EXISTS `audit_record`;
CREATE TABLE `audit_record` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `flow_id`         BIGINT       NOT NULL                COMMENT '审核流程ID',
    `project_id`      BIGINT       NOT NULL                COMMENT '项目ID',
    `business_type`   VARCHAR(50)  DEFAULT NULL            COMMENT '业务类型（交付物/门禁/事件等）',
    `business_id`     BIGINT       DEFAULT NULL            COMMENT '业务ID',
    `audit_level`     INT          DEFAULT 1               COMMENT '审核层级',
    `auditor_id`      BIGINT       DEFAULT NULL            COMMENT '审核人ID',
    `audit_result`    TINYINT      DEFAULT NULL            COMMENT '审核结果（0待审核 1通过 2驳回 3转审）',
    `audit_comment`   VARCHAR(500) DEFAULT NULL            COMMENT '审核意见',
    `audit_time`      DATETIME     DEFAULT NULL            COMMENT '审核时间',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_flow_id` (`flow_id`),
    KEY               `idx_project_id` (`project_id`),
    KEY               `idx_business` (`business_type`, `business_id`),
    KEY               `idx_auditor_id` (`auditor_id`),
    KEY               `idx_audit_result` (`audit_result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审核记录表';

DROP TABLE IF EXISTS `event_info`;
CREATE TABLE `event_info` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    `project_id`      BIGINT       NOT NULL                COMMENT '项目ID',
    `event_type`      TINYINT      NOT NULL                COMMENT '事件类型（1售后 2变更 3异常 99其他）',
    `event_title`     VARCHAR(200) NOT NULL                COMMENT '事件标题',
    `urgency`         TINYINT      DEFAULT 2               COMMENT '紧急程度（1紧急 2一般 3低）',
    `status`          TINYINT      DEFAULT 0               COMMENT '状态（0待审批 1处理中 2已完成 3已关闭 4已驳回）',
    `applicant_id`    BIGINT       DEFAULT NULL            COMMENT '申请人ID',
    `handler_id`      BIGINT       DEFAULT NULL            COMMENT '处理人ID',
    `description`     TEXT         DEFAULT NULL            COMMENT '事件描述',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `close_time`      DATETIME     DEFAULT NULL            COMMENT '关闭时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_project_id` (`project_id`),
    KEY               `idx_event_type` (`event_type`),
    KEY               `idx_urgency` (`urgency`),
    KEY               `idx_status` (`status`),
    KEY               `idx_handler_id` (`handler_id`),
    KEY               `idx_applicant_id` (`applicant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件信息表';

DROP TABLE IF EXISTS `event_handle`;
CREATE TABLE `event_handle` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '处理记录ID',
    `event_id`        BIGINT       NOT NULL                COMMENT '事件ID',
    `handle_type`     VARCHAR(50)  DEFAULT NULL            COMMENT '处理类型（受理/转派/处理/关闭等）',
    `handle_desc`     TEXT         DEFAULT NULL            COMMENT '处理描述',
    `handler_id`      BIGINT       DEFAULT NULL            COMMENT '处理人ID',
    `handle_time`     DATETIME     DEFAULT NULL            COMMENT '处理时间',
    `handle_result`   VARCHAR(500) DEFAULT NULL            COMMENT '处理结果',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_event_id` (`event_id`),
    KEY               `idx_handler_id` (`handler_id`),
    KEY               `idx_handle_time` (`handle_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件处理记录表';

-- ============================================================================
-- 9.6 绩效模块
-- ============================================================================

DROP TABLE IF EXISTS `kpi_indicator`;
CREATE TABLE `kpi_indicator` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '指标ID',
    `indicator_name`  VARCHAR(200)  NOT NULL                COMMENT '指标名称',
    `indicator_code`  VARCHAR(50)   DEFAULT NULL            COMMENT '指标编码',
    `project_type`    TINYINT       DEFAULT NULL            COMMENT '适用项目类型（1整机厂 2技改 3研发）',
    `indicator_type`  VARCHAR(50)   DEFAULT NULL            COMMENT '指标类型（进度/成本/质量/满意度等）',
    `base_score`      DECIMAL(5,2)  DEFAULT 100.00          COMMENT '基准分值',
    `unit`            VARCHAR(20)   DEFAULT NULL            COMMENT '单位（%/分/万元等）',
    `description`     VARCHAR(500)  DEFAULT NULL            COMMENT '指标说明',
    `status`          TINYINT       DEFAULT 1               COMMENT '状态（0停用 1启用）',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_indicator_code` (`indicator_code`),
    KEY               `idx_project_type` (`project_type`),
    KEY               `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KPI指标表';

DROP TABLE IF EXISTS `kpi_score`;
CREATE TABLE `kpi_score` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '评分ID',
    `user_id`         BIGINT        NOT NULL                COMMENT '用户ID',
    `period`          VARCHAR(7)    NOT NULL                COMMENT '考核周期（YYYY-MM）',
    `indicator_id`    BIGINT        NOT NULL                COMMENT '指标ID',
    `base_score`      DECIMAL(5,2)  DEFAULT NULL            COMMENT '基础分',
    `adjust_score`    DECIMAL(5,2)  DEFAULT 0.00            COMMENT '调整分',
    `final_score`     DECIMAL(5,2)  DEFAULT NULL            COMMENT '最终得分',
    `adjust_reason`   VARCHAR(500)  DEFAULT NULL            COMMENT '调整原因',
    `level`           VARCHAR(10)   DEFAULT NULL            COMMENT '等级（A/B/C/D）',
    `status`          TINYINT       DEFAULT 0               COMMENT '状态（0草稿 1已确认 2已归档）',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_period_indicator` (`user_id`, `period`, `indicator_id`),
    KEY               `idx_user_id` (`user_id`),
    KEY               `idx_period` (`period`),
    KEY               `idx_indicator_id` (`indicator_id`),
    KEY               `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KPI评分表';

DROP TABLE IF EXISTS `kpi_weight`;
CREATE TABLE `kpi_weight` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '权重ID',
    `project_type`    TINYINT       NOT NULL                COMMENT '项目类型（1整机厂 2技改 3研发）',
    `indicator_id`    BIGINT        NOT NULL                COMMENT '指标ID',
    `weight`          DECIMAL(5,2)  NOT NULL                COMMENT '权重值（百分比）',
    `period`          VARCHAR(7)    DEFAULT NULL            COMMENT '适用周期（YYYY-MM，NULL表示全局）',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_indicator_period` (`project_type`, `indicator_id`, `period`),
    KEY               `idx_project_type` (`project_type`),
    KEY               `idx_indicator_id` (`indicator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='KPI权重表';

-- ============================================================================
-- 9.7 精益改进模块
-- ============================================================================

DROP TABLE IF EXISTS `improvement_proposal`;
CREATE TABLE `improvement_proposal` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '提案ID',
    `project_id`        BIGINT       DEFAULT NULL            COMMENT '项目ID',
    `title`             VARCHAR(200) NOT NULL                COMMENT '提案标题',
    `proposal_type`     VARCHAR(50)  DEFAULT NULL            COMMENT '提案类型（流程改进/质量提升/成本节约等）',
    `proposer_id`       BIGINT       DEFAULT NULL            COMMENT '提案人ID',
    `current_state`     TEXT         DEFAULT NULL            COMMENT '现状描述',
    `target_state`      TEXT         DEFAULT NULL            COMMENT '目标状态',
    `proposal_content`  TEXT         DEFAULT NULL            COMMENT '改进方案内容',
    `status`            TINYINT      DEFAULT 0               COMMENT '状态（0待审核 1审核通过 2实施中 3已完成 4已驳回）',
    `review_result`     TINYINT      DEFAULT NULL            COMMENT '审核结果（0待审核 1通过 2驳回）',
    `review_comment`    VARCHAR(500) DEFAULT NULL            COMMENT '审核意见',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY                 `idx_project_id` (`project_id`),
    KEY                 `idx_proposer_id` (`proposer_id`),
    KEY                 `idx_status` (`status`),
    KEY                 `idx_proposal_type` (`proposal_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='改进提案表';

DROP TABLE IF EXISTS `a3_report`;
CREATE TABLE `a3_report` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'A3报告ID',
    `project_id`          BIGINT       DEFAULT NULL            COMMENT '项目ID',
    `proposal_id`         BIGINT       DEFAULT NULL            COMMENT '关联提案ID',
    `title`               VARCHAR(200) NOT NULL                COMMENT '报告标题',
    `background`          TEXT         DEFAULT NULL            COMMENT '背景描述',
    `current_condition`   TEXT         DEFAULT NULL            COMMENT '现状描述',
    `goal`                TEXT         DEFAULT NULL            COMMENT '目标设定',
    `root_cause`          TEXT         DEFAULT NULL            COMMENT '根本原因分析',
    `countermeasures`     TEXT         DEFAULT NULL            COMMENT '对策措施',
    `check_result`        TEXT         DEFAULT NULL            COMMENT '效果验证',
    `follow_up`           TEXT         DEFAULT NULL            COMMENT '跟进与标准化',
    `status`              TINYINT      DEFAULT 0               COMMENT '状态（0草稿 1进行中 2已完成 3已关闭）',
    `create_by`           VARCHAR(50)  DEFAULT NULL            COMMENT '创建人',
    `create_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY                   `idx_project_id` (`project_id`),
    KEY                   `idx_proposal_id` (`proposal_id`),
    KEY                   `idx_status` (`status`),
    KEY                   `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='A3报告表';

DROP TABLE IF EXISTS `root_cause_analysis`;
CREATE TABLE `root_cause_analysis` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分析ID',
    `project_id`          BIGINT       DEFAULT NULL            COMMENT '项目ID',
    `problem_desc`        TEXT         NOT NULL                COMMENT '问题描述',
    `analysis_method`     VARCHAR(50)  DEFAULT NULL            COMMENT '分析方法（5Why/鱼骨图/故障树/FMEA）',
    `findings`            TEXT         DEFAULT NULL            COMMENT '分析过程与发现',
    `root_cause`          TEXT         DEFAULT NULL            COMMENT '根本原因',
    `corrective_action`   TEXT         DEFAULT NULL            COMMENT '纠正措施',
    `preventive_action`   TEXT         DEFAULT NULL            COMMENT '预防措施',
    `status`              TINYINT      DEFAULT 0               COMMENT '状态（0分析中 1已完成 2已关闭）',
    `create_by`           VARCHAR(50)  DEFAULT NULL            COMMENT '创建人',
    `create_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY                   `idx_project_id` (`project_id`),
    KEY                   `idx_analysis_method` (`analysis_method`),
    KEY                   `idx_status` (`status`),
    KEY                   `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='根因分析表';

DROP TABLE IF EXISTS `lessons_learned`;
CREATE TABLE `lessons_learned` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '经验ID',
    `project_id`      BIGINT       NOT NULL                COMMENT '项目ID',
    `phase_id`        BIGINT       DEFAULT NULL            COMMENT '阶段ID',
    `title`           VARCHAR(200) NOT NULL                COMMENT '经验标题',
    `category`        VARCHAR(50)  DEFAULT NULL            COMMENT '类别（成功经验/失败教训/改进建议）',
    `content`         TEXT         DEFAULT NULL            COMMENT '详细内容',
    `impact_level`    VARCHAR(20)  DEFAULT NULL            COMMENT '影响程度（高/中/低）',
    `keywords`        VARCHAR(200) DEFAULT NULL            COMMENT '关键词（逗号分隔）',
    `create_by`       VARCHAR(50)  DEFAULT NULL            COMMENT '创建人',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_project_id` (`project_id`),
    KEY               `idx_phase_id` (`phase_id`),
    KEY               `idx_category` (`category`),
    KEY               `idx_impact_level` (`impact_level`),
    KEY               `idx_create_by` (`create_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='经验教训表';

-- ============================================================================
-- 9.8 价值流模块
-- ============================================================================

DROP TABLE IF EXISTS `value_stream_record`;
CREATE TABLE `value_stream_record` (
    `id`                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `project_id`              BIGINT        NOT NULL                COMMENT '项目ID',
    `phase_id`                BIGINT        DEFAULT NULL            COMMENT '阶段ID',
    `record_date`             DATE          DEFAULT NULL            COMMENT '记录日期',
    `process_name`            VARCHAR(200)  DEFAULT NULL            COMMENT '流程名称',
    `process_type`            VARCHAR(50)   DEFAULT NULL            COMMENT '流程类型',
    `cycle_time`              DECIMAL(10,2) DEFAULT NULL            COMMENT '周期时间（小时）',
    `value_added_time`        DECIMAL(10,2) DEFAULT NULL            COMMENT '增值时间（小时）',
    `non_value_added_time`    DECIMAL(10,2) DEFAULT NULL            COMMENT '非增值时间（小时）',
    `lead_time`               DECIMAL(10,2) DEFAULT NULL            COMMENT '前置时间（天）',
    `efficiency_rate`         DECIMAL(5,2)  DEFAULT NULL            COMMENT '流程效率（%）',
    `description`             VARCHAR(500)  DEFAULT NULL            COMMENT '描述',
    `create_time`             DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`             DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`                 TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY                       `idx_project_id` (`project_id`),
    KEY                       `idx_phase_id` (`phase_id`),
    KEY                       `idx_record_date` (`record_date`),
    KEY                       `idx_process_type` (`process_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='价值流记录表';

DROP TABLE IF EXISTS `waste_record`;
CREATE TABLE `waste_record` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `project_id`          BIGINT       NOT NULL                COMMENT '项目ID',
    `waste_type`          VARCHAR(50)  DEFAULT NULL            COMMENT '浪费类型（等待/缺陷/搬运/过度加工/库存/动作/过量生产）',
    `waste_desc`          VARCHAR(500) DEFAULT NULL            COMMENT '浪费描述',
    `waste_source`        VARCHAR(200) DEFAULT NULL            COMMENT '浪费来源',
    `severity`            TINYINT      DEFAULT 2               COMMENT '严重程度（1高 2中 3低）',
    `status`              TINYINT      DEFAULT 0               COMMENT '状态（0未处理 1处理中 2已消除 3已关闭）',
    `improvement_action`  VARCHAR(500) DEFAULT NULL            COMMENT '改进措施',
    `handler_id`          BIGINT       DEFAULT NULL            COMMENT '处理人ID',
    `create_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY                   `idx_project_id` (`project_id`),
    KEY                   `idx_waste_type` (`waste_type`),
    KEY                   `idx_severity` (`severity`),
    KEY                   `idx_status` (`status`),
    KEY                   `idx_handler_id` (`handler_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浪费记录表';

-- ============================================================================
-- 9.9 消息通知模块
-- ============================================================================

DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `notice_title`    VARCHAR(200) NOT NULL                COMMENT '通知标题',
    `notice_type`     TINYINT      DEFAULT 1               COMMENT '通知类型（1通知 2公告）',
    `notice_content`  TEXT         DEFAULT NULL            COMMENT '通知内容',
    `status`          TINYINT      DEFAULT 1               COMMENT '状态（0停用 1正常）',
    `publish_by`      VARCHAR(50)  DEFAULT NULL            COMMENT '发布人',
    `publish_time`    DATETIME     DEFAULT NULL            COMMENT '发布时间',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_notice_type` (`notice_type`),
    KEY               `idx_status` (`status`),
    KEY               `idx_publish_time` (`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';

DROP TABLE IF EXISTS `user_message`;
CREATE TABLE `user_message` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `user_id`         BIGINT       NOT NULL                COMMENT '接收用户ID',
    `message_type`    VARCHAR(50)  DEFAULT NULL            COMMENT '消息类型（任务提醒/审核通知/事件通知等）',
    `title`           VARCHAR(200) DEFAULT NULL            COMMENT '消息标题',
    `content`         TEXT         DEFAULT NULL            COMMENT '消息内容',
    `related_id`      BIGINT       DEFAULT NULL            COMMENT '关联业务ID',
    `is_read`         TINYINT      DEFAULT 0               COMMENT '是否已读（0未读 1已读）',
    `read_time`       DATETIME     DEFAULT NULL            COMMENT '阅读时间',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_user_id` (`user_id`),
    KEY               `idx_is_read` (`user_id`, `is_read`),
    KEY               `idx_message_type` (`message_type`),
    KEY               `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户消息表';

-- ============================================================================
-- 9.10 其他模块
-- ============================================================================

DROP TABLE IF EXISTS `project_bonus`;
CREATE TABLE `project_bonus` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '奖金ID',
    `project_id`      BIGINT        NOT NULL                COMMENT '项目ID',
    `user_id`         BIGINT        NOT NULL                COMMENT '用户ID',
    `bonus_type`      TINYINT       NOT NULL                COMMENT '奖金类型（1整机厂 2技改 3研发）',
    `project_period`  TINYINT       DEFAULT NULL            COMMENT '项目阶段（1前期 2中期 3后期 4大后期）',
    `base_amount`     DECIMAL(12,2) DEFAULT NULL            COMMENT '基础金额',
    `coefficient`     DECIMAL(5,2)  DEFAULT 1.00            COMMENT '系数',
    `monthly_amount`  DECIMAL(12,2) DEFAULT NULL            COMMENT '月度奖金金额',
    `yearly_amount`   DECIMAL(12,2) DEFAULT NULL            COMMENT '年度奖金金额',
    `paid_monthly`    DECIMAL(12,2) DEFAULT 0.00            COMMENT '已发月度奖金',
    `paid_yearly`     DECIMAL(12,2) DEFAULT 0.00            COMMENT '已发年度奖金',
    `status`          TINYINT       DEFAULT 1               COMMENT '状态（0停用 1启用）',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_user_type_period` (`project_id`, `user_id`, `bonus_type`, `project_period`),
    KEY               `idx_project_id` (`project_id`),
    KEY               `idx_user_id` (`user_id`),
    KEY               `idx_bonus_type` (`bonus_type`),
    KEY               `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目奖金表';

DROP TABLE IF EXISTS `project_bonus_record`;
CREATE TABLE `project_bonus_record` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `bonus_id`        BIGINT        NOT NULL                COMMENT '奖金配置ID',
    `project_id`      BIGINT        NOT NULL                COMMENT '项目ID',
    `user_id`         BIGINT        NOT NULL                COMMENT '用户ID',
    `pay_type`        TINYINT       DEFAULT NULL            COMMENT '发放类型（1月度 2年度）',
    `pay_amount`      DECIMAL(12,2) DEFAULT NULL            COMMENT '发放金额',
    `pay_period`      VARCHAR(7)    DEFAULT NULL            COMMENT '发放周期（YYYY-MM）',
    `pay_time`        DATETIME      DEFAULT NULL            COMMENT '发放时间',
    `remark`          VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY               `idx_bonus_id` (`bonus_id`),
    KEY               `idx_project_id` (`project_id`),
    KEY               `idx_user_id` (`user_id`),
    KEY               `idx_pay_period` (`pay_period`),
    KEY               `idx_pay_time` (`pay_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='奖金发放记录表';

-- ============================================================================
-- 9.11 平台底座（T-101：编码规则引擎 / 通知中心 / 导入导出物料示例）
-- ============================================================================

-- 编码规则表（配置化发号规则）
DROP TABLE IF EXISTS `sys_code_rule`;
CREATE TABLE `sys_code_rule` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    `rule_code`    VARCHAR(50)  NOT NULL                COMMENT '规则编码（唯一）：PRJ/HT/WL...',
    `rule_name`    VARCHAR(100) NOT NULL                COMMENT '规则名称',
    `pattern`      VARCHAR(100) NOT NULL                COMMENT '编码模板：{yyyy}/{yyyyMM}/{yyyyMMdd}日期变量；{seq:n}流水号左补零n位；{xxx}调用方参数',
    `date_pattern` VARCHAR(10)  DEFAULT NULL            COMMENT '流水号重置维度的日期格式（yyyy/yyyyMM/空=永不重置）',
    `status`       TINYINT      NOT NULL DEFAULT 1      COMMENT '状态（1启用 0停用）',
    `remark`       VARCHAR(200) DEFAULT NULL            COMMENT '备注',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='编码规则表';

-- 编码序号表（独立短事务原子自增，date_part 变化即流水号重置）
DROP TABLE IF EXISTS `sys_code_seq`;
CREATE TABLE `sys_code_seq` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '序号ID',
    `rule_code`     VARCHAR(50) NOT NULL                COMMENT '规则编码',
    `date_part`     VARCHAR(10) NOT NULL DEFAULT ''     COMMENT '日期段值（如 2026/202611），无日期规则固定空串',
    `current_value` BIGINT      NOT NULL DEFAULT 0      COMMENT '当前流水值',
    `update_time`   DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_date` (`rule_code`, `date_part`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='编码序号表';

-- 通知模板表
DROP TABLE IF EXISTS `sys_notification_template`;
CREATE TABLE `sys_notification_template` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `template_code`    VARCHAR(50)  NOT NULL                COMMENT '模板编码（唯一）',
    `title_template`   VARCHAR(200) NOT NULL                COMMENT '标题模板（{param}占位符）',
    `content_template` TEXT         DEFAULT NULL            COMMENT '内容模板（{param}占位符）',
    `message_type`     VARCHAR(50)  DEFAULT '系统通知'      COMMENT '落 user_message.message_type 的分类',
    `status`           TINYINT      NOT NULL DEFAULT 1      COMMENT '状态（1启用 0停用）',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知模板表';

-- 物料主数据表（一码一物，字段对齐 T-003 物料归一清单模板）
DROP TABLE IF EXISTS `mdm_material`;
CREATE TABLE `mdm_material` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '物料ID',
    `material_code` VARCHAR(30)  NOT NULL                COMMENT '物料编码（唯一，如 WL-000001）',
    `material_name` VARCHAR(100) NOT NULL                COMMENT '物料名称',
    `spec_model`    VARCHAR(100) DEFAULT NULL            COMMENT '规格型号',
    `unit`          VARCHAR(20)  DEFAULT NULL            COMMENT '计量单位',
    `category`      VARCHAR(50)  DEFAULT NULL            COMMENT '物料类别',
    `old_code`      VARCHAR(100) DEFAULT NULL            COMMENT '旧编码/来源系统',
    `alias1`        VARCHAR(100) DEFAULT NULL            COMMENT '别名1',
    `alias2`        VARCHAR(100) DEFAULT NULL            COMMENT '别名2',
    `dedup_status`  VARCHAR(10)  DEFAULT '未查重'        COMMENT '查重状态（未查重/疑似重复/已归并/唯一）',
    `verify_status` VARCHAR(10)  DEFAULT '待核验'        COMMENT '一码一物核验（待核验/通过/不通过）',
    `remark`        VARCHAR(200) DEFAULT NULL            COMMENT '备注',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_material_code` (`material_code`),
    KEY             `idx_material_name` (`material_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料主数据表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 9.12 三身份权限模型（T-102：能力点注册表 / Agent 代理授权表）
-- ============================================================================

-- 能力点注册表（deny by default：未登记的能力点一律拒绝）
DROP TABLE IF EXISTS `sys_capability`;
CREATE TABLE `sys_capability` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '能力点ID',
    `capability_code`     VARCHAR(50)  NOT NULL                COMMENT '能力点编码（唯一，如 CAP_CONTRACT_EXTRACT / AGT-06）',
    `capability_name`     VARCHAR(100) NOT NULL                COMMENT '能力点名称',
    `category`            VARCHAR(50)  DEFAULT NULL            COMMENT '分类（base基础/agent智能体/workflow工作流）',
    `allowed_identities`  VARCHAR(50)  NOT NULL                COMMENT '允许身份，逗号分隔：EXPERT,WORKER,ASSISTANT',
    `is_never`            TINYINT      NOT NULL DEFAULT 0      COMMENT '永久禁止Agent自动化（1=审批/资金/主数据建档/删除四类红线，仅拦截Agent不拦人类）',
    `status`              TINYINT      NOT NULL DEFAULT 1      COMMENT '状态（1启用 0停用）',
    `remark`              VARCHAR(200) DEFAULT NULL            COMMENT '备注',
    `create_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_capability_code` (`capability_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='能力点注册表';

-- Agent 代理授权表（ASSISTANT 以被代理人名义执行的能力白名单，规划 72h TTL）
DROP TABLE IF EXISTS `sys_agent_grant`;
CREATE TABLE `sys_agent_grant` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '授权ID',
    `agent_instance_key` VARCHAR(100) NOT NULL                COMMENT 'Agent 实例标识（规范 agent:{userId}:{能力编号}）',
    `principal_user_id`  BIGINT       NOT NULL                COMMENT '被代理人用户ID',
    `capability_code`    VARCHAR(50)  NOT NULL                COMMENT '能力点编码',
    `status`             TINYINT      NOT NULL DEFAULT 1      COMMENT '状态（1有效 0撤销）',
    `expire_time`        DATETIME     NOT NULL                COMMENT '过期时间（默认72小时）',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_capability` (`agent_instance_key`, `capability_code`),
    KEY                  `idx_principal` (`principal_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent代理授权表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- DDL脚本执行完毕，共创建43张业务表
-- ============================================================================
