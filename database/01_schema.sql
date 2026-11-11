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

-- ============================================================================
-- 9.13 AI 网关（T-105：通道配置 / 调用审计）
-- ============================================================================

-- AI 通道配置（多服务商行按 priority 排序形成降级链；api_key AES-GCM 加密存库，密钥走环境变量）
DROP TABLE IF EXISTS `sys_ai_provider_config`;
CREATE TABLE `sys_ai_provider_config` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `provider_code`   VARCHAR(50)  NOT NULL                COMMENT '配置编码（唯一）',
    `provider_name`   VARCHAR(100) NOT NULL                COMMENT '服务商显示名（如 DeepSeek/智谱/通义）',
    `provider_type`   VARCHAR(20)  NOT NULL                COMMENT '类型（OPENAI_COMPAT/MOCK/OLLAMA）',
    `endpoint`        VARCHAR(200) DEFAULT NULL            COMMENT 'API 地址（如 https://api.deepseek.com/v1）',
    `model`           VARCHAR(100) DEFAULT NULL            COMMENT '模型名（如 deepseek-chat/glm-4-flash）',
    `api_key_cipher`  VARCHAR(500) DEFAULT NULL            COMMENT 'API Key 密文（AES-GCM，Base64）',
    `api_key_hint`    VARCHAR(20)  DEFAULT NULL            COMMENT 'Key 脱敏提示（sk-****1234）',
    `priority`        INT          NOT NULL DEFAULT 100    COMMENT '优先级（小者优先，形成降级链）',
    `enabled`         TINYINT      NOT NULL DEFAULT 0      COMMENT '启用（1启用 0停用）',
    `rpm_limit`       INT          NOT NULL DEFAULT 35     COMMENT '每分钟请求上限（商用API默认35，用户硬性规则）',
    `daily_limit`     INT          NOT NULL DEFAULT 500    COMMENT '每日请求上限',
    `remark`          VARCHAR(200) DEFAULT NULL            COMMENT '备注',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_code` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI通道配置表';

-- AI 调用审计表（全量落库，保留 2 年，网关每日定时清理）
DROP TABLE IF EXISTS `sys_ai_call_log`;
CREATE TABLE `sys_ai_call_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `request_id`       VARCHAR(64)  NOT NULL                COMMENT '请求唯一标识',
    `capability_code`  VARCHAR(50)  NOT NULL                COMMENT '能力点编码',
    `provider_type`    VARCHAR(20)  DEFAULT NULL            COMMENT '实际使用的通道类型',
    `model`            VARCHAR(100) DEFAULT NULL            COMMENT '实际使用的模型',
    `caller_type`      VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '调用方类型（USER/AGENT）',
    `caller_id`        VARCHAR(100) DEFAULT NULL            COMMENT '调用方标识（userId 或 agentKey）',
    `prompt_chars`     INT          DEFAULT 0               COMMENT '提示词字符数（脱敏后）',
    `response_chars`   INT          DEFAULT 0               COMMENT '响应字符数',
    `prompt_preview`   VARCHAR(500) DEFAULT NULL            COMMENT '提示词预览（脱敏后前500字）',
    `response_preview` VARCHAR(500) DEFAULT NULL            COMMENT '响应预览（前500字）',
    `total_tokens`     INT          DEFAULT 0               COMMENT 'Token 用量',
    `duration_ms`      BIGINT       DEFAULT 0               COMMENT '耗时（毫秒）',
    `status`           VARCHAR(20)  NOT NULL                COMMENT '状态（SUCCESS/PROVIDER_ERROR/QUOTA_EXCEEDED/CONTRACT_FAILED/DEGRADED/MOCK）',
    `error_msg`        VARCHAR(500) DEFAULT NULL            COMMENT '错误信息（收敛后，不透出上游细节）',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`),
    KEY                `idx_capability` (`capability_code`),
    KEY                `idx_caller` (`caller_id`),
    KEY                `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI调用审计表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 9.14 知识库 RAG 基础（T-106：文档 / 分块 / 向量）
-- ============================================================================

-- 知识文档表（SOP/制度/规范/案例首批语料载体）
DROP TABLE IF EXISTS `kb_document`;
CREATE TABLE `kb_document` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档ID',
    `title`         VARCHAR(200) NOT NULL                COMMENT '文档标题',
    `source_type`   VARCHAR(20)  NOT NULL DEFAULT 'TEXT' COMMENT '来源类型（PDF/WORD/TXT/MD/TEXT）',
    `origin_name`   VARCHAR(200) DEFAULT NULL            COMMENT '原始文件名（上传时）',
    `char_count`    INT          NOT NULL DEFAULT 0      COMMENT '提取后的正文字符数',
    `chunk_count`   INT          NOT NULL DEFAULT 0      COMMENT '分块数量',
    `status`        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT解析中/INDEXED已索引/FAILED失败）',
    `doc_category`  VARCHAR(20)  NOT NULL DEFAULT 'SOP'  COMMENT '知识分类（SOP/REGULATION制度/SPEC规范/CASE案例，T-306）',
    `sensitive`     TINYINT      NOT NULL DEFAULT 0      COMMENT '敏感知识（0/1，敏感须人工审核后可检索，T-306）',
    `audit_status`  VARCHAR(20)  NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态（非敏感默认APPROVED；敏感PENDING/APPROVED/REJECTED，T-306）',
    `hit_count`     INT          NOT NULL DEFAULT 0      COMMENT '检索命中计数（评分来源之一，T-306）',
    `fail_reason`   VARCHAR(500) DEFAULT NULL            COMMENT '解析失败原因（收敛后）',
    `content`       MEDIUMTEXT   DEFAULT NULL            COMMENT '提取后的正文全文（检索展示用）',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY             `idx_status` (`status`),
    KEY             `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档表';

-- 知识分块表（向量以 JSON 数组存储，双精度；pgvector 实现见 T-106 抽象层，可配置切换）
DROP TABLE IF EXISTS `kb_chunk`;
CREATE TABLE `kb_chunk` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '块ID',
    `document_id`      BIGINT      NOT NULL                COMMENT '所属文档ID',
    `chunk_index`      INT         NOT NULL                COMMENT '块序号（文档内从0起）',
    `content`          TEXT        NOT NULL                COMMENT '块正文',
    `char_count`       INT         NOT NULL DEFAULT 0      COMMENT '块字符数',
    `embedding_model`  VARCHAR(100) DEFAULT NULL           COMMENT '向量化模型标识（mock/真实模型名）',
    `embedding`        JSON        DEFAULT NULL            COMMENT '向量（JSON 数组，双精度）',
    `score`            INT         NOT NULL DEFAULT 0      COMMENT '知识评分（检索命中+1/点赞+3/点踩-2，淘汰依据，T-306）',
    `create_time`      DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY                `idx_document` (`document_id`, `chunk_index`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识分块表';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 9.15 Agent 调度（T-107：任务状态机 / 队列）
-- ============================================================================

-- Agent 任务表（状态机：queued → running → pending_confirm → done / failed / escalated / cancelled / rolled_back）
DROP TABLE IF EXISTS `agent_task`;
CREATE TABLE `agent_task` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `task_no`            VARCHAR(30)  NOT NULL                COMMENT '任务单号（唯一，T-101 编码引擎 AGT 规则）',
    `capability_code`    VARCHAR(50)  NOT NULL                COMMENT '路由到的能力点',
    `instruction`        VARCHAR(2000) NOT NULL               COMMENT '用户原始指令',
    `params`             JSON         DEFAULT NULL            COMMENT '扩展参数',
    `status`             VARCHAR(20)  NOT NULL DEFAULT 'queued' COMMENT '状态（queued/running/pending_confirm/done/failed/escalated/cancelled/rolled_back）',
    `caller_user_id`     BIGINT       NOT NULL                COMMENT '发起用户ID',
    `agent_instance_key` VARCHAR(100) DEFAULT NULL            COMMENT '执行实例标识（ASSISTANT 代理时）',
    `intent_confidence`  DOUBLE       DEFAULT NULL            COMMENT '意图识别置信度（0~1）',
    `result_text`        MEDIUMTEXT   DEFAULT NULL            COMMENT '执行结果（AI 生成内容必带标注）',
    `ai_generated`       TINYINT      DEFAULT 0               COMMENT '结果是否 AI 生成（0否 1是）',
    `error_msg`          VARCHAR(500) DEFAULT NULL            COMMENT '失败原因（收敛后）',
    `executed_by`        VARCHAR(100) DEFAULT NULL            COMMENT '实际执行者（agent:{userId}:{能力编号} 或 user:{userId}）',
    `started_at`         DATETIME     DEFAULT NULL            COMMENT '开始执行时间',
    `finished_at`        DATETIME     DEFAULT NULL            COMMENT '结束时间',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_no` (`task_no`),
    KEY                `idx_status` (`status`),
    KEY                `idx_caller` (`caller_user_id`),
    KEY                `idx_capability` (`capability_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent任务表';

-- Agent 任务事件表（任务状态流转留痕，供工作台时间线展示）
DROP TABLE IF EXISTS `agent_task_event`;
CREATE TABLE `agent_task_event` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    `task_id`     BIGINT       NOT NULL                COMMENT '任务ID',
    `from_status` VARCHAR(20)  DEFAULT NULL            COMMENT '原状态',
    `to_status`   VARCHAR(20)  NOT NULL                COMMENT '目标状态',
    `operator`    VARCHAR(100) NOT NULL                COMMENT '操作者（user:{id}/agent:{key}/system）',
    `detail`      VARCHAR(500) DEFAULT NULL            COMMENT '事件说明',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY           `idx_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent任务事件表';

-- 编码规则：Agent 任务单号
INSERT INTO `sys_code_rule` (`rule_code`, `rule_name`, `pattern`, `date_pattern`, `status`, `remark`) VALUES
('AGTT', 'Agent任务单号', 'AGTT-{yyyyMMdd}-{seq:4}', 'yyyyMMdd', 1, 'T-107 MasterAgent 任务单');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 9.16 W2 合同域（T-111：台账 / 审批流状态机 / 决策门）
-- ============================================================================

-- 合同台账
DROP TABLE IF EXISTS `contract`;
CREATE TABLE `contract` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '合同ID',
    `contract_no`      VARCHAR(30)   NOT NULL                COMMENT '合同编号（T-101 引擎 HT 规则）',
    `name`             VARCHAR(200)  NOT NULL                COMMENT '合同名称',
    `customer_name`    VARCHAR(200)  NOT NULL                COMMENT '客户名称（主数据 T-103 建成后切换主体引用）',
    `amount`           DECIMAL(18,2) NOT NULL                COMMENT '合同金额（元，禁用 float）',
    `sign_date`        DATE          DEFAULT NULL            COMMENT '签订日期',
    `effective_date`   DATE          DEFAULT NULL            COMMENT '生效日期',
    `warranty_months`  INT           NOT NULL DEFAULT 24     COMMENT '质保期（月）',
    `pay_ratio_prepay` INT           NOT NULL DEFAULT 30     COMMENT '预付款比例%',
    `pay_ratio_delivery` INT         NOT NULL DEFAULT 30     COMMENT '到货款比例%',
    `pay_ratio_accept` INT           NOT NULL DEFAULT 30     COMMENT '验收款比例%',
    `pay_ratio_warranty` INT         NOT NULL DEFAULT 10     COMMENT '质保金比例%',
    `status`           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT草稿/COUNTERSIGN会签中/APPROVING审批中/SEALING用印中/EFFECTIVE已生效/CLOSED已关闭/TERMINATED已终止）',
    `extracted_json`   JSON          DEFAULT NULL            COMMENT 'AGT-06 要素提取结果（JSON）',
    `risk_level`       VARCHAR(10)   DEFAULT NULL            COMMENT 'AGT-07 风险等级（高/中/低）',
    `risk_summary`     VARCHAR(1000) DEFAULT NULL            COMMENT 'AGT-07 风险摘要',
    `remark`           VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_contract_no` (`contract_no`),
    KEY                `idx_status` (`status`),
    KEY                `idx_customer` (`customer_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同台账表';

-- 合同审批记录（会签/审批/用印节点留痕）
DROP TABLE IF EXISTS `contract_approval`;
CREATE TABLE `contract_approval` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `contract_id` BIGINT       NOT NULL                COMMENT '合同ID',
    `node`        VARCHAR(20)  NOT NULL                COMMENT '审批节点（COUNTERSIGN会签/APPROVE审批/SEAL用印）',
    `approver_id` BIGINT       NOT NULL                COMMENT '审批人用户ID',
    `approver_name` VARCHAR(50) DEFAULT NULL            COMMENT '审批人姓名',
    `result`      VARCHAR(10)  NOT NULL                COMMENT '结果（APPROVED通过/REJECTED驳回）',
    `comment`     VARCHAR(500) DEFAULT NULL            COMMENT '审批意见',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间',
    PRIMARY KEY (`id`),
    KEY           `idx_contract` (`contract_id`, `node`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同审批记录表';

-- ============================================================================
-- 9.17 W3 设计域（T-115：模板库 / 勘测记录 / 方案版本 / BOM 校验 / 受控发布）
-- ============================================================================

-- 方案模板库（AGT-09 匹配源）
DROP TABLE IF EXISTS `design_template`;
CREATE TABLE `design_template` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `template_name` VARCHAR(100)  NOT NULL                COMMENT '模板名称',
    `scene`         VARCHAR(50)   NOT NULL                COMMENT '适用场景（塔筒/机舱/基础环/电气等，匹配键）',
    `template_text` LONGTEXT      NOT NULL                COMMENT '模板正文（含占位符 {{projectName}}/{{customerName}} 等）',
    `spec_refs`     VARCHAR(500)  DEFAULT NULL            COMMENT '引用规范（如 GB/T 19072-2018）',
    `enabled`       TINYINT       NOT NULL DEFAULT 1      COMMENT '启用（1启用 0停用）',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_scene` (`scene`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='方案模板库';

-- 勘测记录
DROP TABLE IF EXISTS `design_survey`;
CREATE TABLE `design_survey` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `project_id`  BIGINT        NOT NULL                COMMENT '项目ID',
    `site_name`   VARCHAR(100)  NOT NULL                COMMENT '勘测点位',
    `survey_date` DATE          DEFAULT NULL            COMMENT '勘测日期',
    `surveyor`    VARCHAR(50)   DEFAULT NULL            COMMENT '勘测人',
    `wind_class`  VARCHAR(20)   DEFAULT NULL            COMMENT '风区等级',
    `content`     TEXT          DEFAULT NULL            COMMENT '勘测结论',
    `create_time` DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='勘测记录';

-- 方案版本（AI 起草与人工编写共存；受控发布后不可修改）
DROP TABLE IF EXISTS `design_scheme`;
CREATE TABLE `design_scheme` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '方案ID',
    `project_id`   BIGINT        NOT NULL                COMMENT '项目ID',
    `contract_id`  BIGINT        DEFAULT NULL            COMMENT '关联合同ID',
    `version_no`   INT           NOT NULL DEFAULT 1      COMMENT '版本号（项目内递增）',
    `title`        VARCHAR(200)  NOT NULL                COMMENT '方案标题',
    `content`      LONGTEXT      NOT NULL                COMMENT '方案正文',
    `gen_type`     VARCHAR(10)   NOT NULL DEFAULT 'MANUAL' COMMENT '生成方式（MANUAL人工/AI起草）',
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT草稿/RELEASED已受控发布/ARCHIVED已归档）',
    `release_note` VARCHAR(500)  DEFAULT NULL            COMMENT '发布说明',
    `release_time` DATETIME      DEFAULT NULL            COMMENT '发布时间',
    `released_by`  VARCHAR(50)   DEFAULT NULL            COMMENT '发布人',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`, `version_no`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设计方案版本表';

-- BOM 明细（方案附属，AGT-11 校验对象）
DROP TABLE IF EXISTS `design_bom_item`;
CREATE TABLE `design_bom_item` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    `scheme_id`     BIGINT         NOT NULL                COMMENT '方案ID',
    `material_code` VARCHAR(30)    NOT NULL                COMMENT '物料编码（WL-XXXXXX）',
    `material_name` VARCHAR(100)   NOT NULL                COMMENT '物料名称',
    `spec_model`    VARCHAR(100)   DEFAULT NULL            COMMENT '规格型号',
    `qty`           DECIMAL(12,2)  NOT NULL DEFAULT 1      COMMENT '数量',
    `unit`          VARCHAR(20)    DEFAULT NULL            COMMENT '计量单位',
    `remark`        VARCHAR(200)   DEFAULT NULL            COMMENT '备注',
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_scheme` (`scheme_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='方案BOM明细';

-- BOM 校验记录（AGT-11 输出留痕）
DROP TABLE IF EXISTS `design_bom_check_log`;
CREATE TABLE `design_bom_check_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `scheme_id`   BIGINT       NOT NULL                COMMENT '方案ID',
    `passed`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否通过（0有缺陷 1通过）',
    `total_items` INT          DEFAULT NULL            COMMENT '校验明细数',
    `result_json` LONGTEXT     DEFAULT NULL            COMMENT '违规明细 JSON',
    `checker`     VARCHAR(20)  DEFAULT 'AGT-11'        COMMENT '校验方（AGT-11/MANUAL）',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '校验时间',
    PRIMARY KEY (`id`),
    KEY `idx_scheme` (`scheme_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='BOM校验记录';

-- 模板库种子（匹配率演示）
INSERT INTO `design_template` (`template_name`, `scene`, `template_text`, `spec_refs`) VALUES
('塔筒制造方案模板', '塔筒', '# {{projectName}} 塔筒制造方案\n\n## 一、项目概况\n客户：{{customerName}}；依据勘测记录与设计规范编制。\n\n## 二、制造工艺\n卷板→纵缝焊接→组对→环缝焊接→法兰焊接→喷锌防腐。\n\n## 三、质量控制\n焊缝 100% UT 探伤；几何尺寸按图纸公差执行。\n\n## 四、引用规范\n{{specRefs}}', 'GB/T 19072-2018,GB/T 1591'),
('机舱装配方案模板', '机舱', '# {{projectName}} 机舱装配方案\n\n## 一、装配流程\n底架就位→偏航系统→传动链→发电机→罩壳。\n\n## 二、力矩要求\n螺栓按 GB/T 1231 分级力矩紧固。\n\n## 三、引用规范\n{{specRefs}}', 'GB/T 19568,GB/T 1231');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 9.18 W4 样机域（T-201：齐套/PO/到货/交底/工单/FAI/质检/整改，BR-01/02/08/09 硬卡点）
-- ============================================================================

-- 齐套审查表（BR-08：齐套率<95% 工单禁止开工）
DROP TABLE IF EXISTS `kitting_check`;
CREATE TABLE `kitting_check` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '审查ID',
    `project_id`   BIGINT        NOT NULL                COMMENT '项目ID',
    `workorder_id` BIGINT        DEFAULT NULL            COMMENT '关联工单ID（可先于工单）',
    `kit_rate`     DECIMAL(5,2)  NOT NULL                COMMENT '齐套率%（0~100）',
    `result`       VARCHAR(10)   NOT NULL                COMMENT '结果（PASS通过/BLOCK不满足）',
    `detail_json`  LONGTEXT      DEFAULT NULL            COMMENT '缺料明细 JSON',
    `checker`      VARCHAR(20)   DEFAULT 'AGT-13'        COMMENT '审查方（AGT-13/MANUAL）',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '审查时间',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='齐套审查表';

-- 采购订单（PO，编码引擎 PO 规则）
DROP TABLE IF EXISTS `purchase_order`;
CREATE TABLE `purchase_order` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT 'PO ID',
    `po_no`         VARCHAR(30)    NOT NULL                COMMENT 'PO 编号',
    `project_id`    BIGINT         NOT NULL                COMMENT '项目ID',
    `material_code` VARCHAR(30)    NOT NULL                COMMENT '物料编码',
    `material_name` VARCHAR(100)   DEFAULT NULL            COMMENT '物料名称（冗余）',
    `qty`           DECIMAL(12,2)  NOT NULL                COMMENT '采购数量',
    `arrived_qty`   DECIMAL(12,2)  NOT NULL DEFAULT 0      COMMENT '已到货数量',
    `supplier`      VARCHAR(100)   DEFAULT NULL            COMMENT '供应商',
    `expected_date` DATE           DEFAULT NULL            COMMENT '期望到货日期（AGT-15 交期跟踪）',
    `status`        VARCHAR(20)    NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态（DRAFT草稿/SUBMITTED已下达/CLOSED完结）',
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT        DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_po_no` (`po_no`),
    KEY `idx_project` (`project_id`),
    KEY `idx_material` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='采购订单表';

-- 到货跟踪（物料到货登记，AGT-15 数据源）
DROP TABLE IF EXISTS `arrival_record`;
CREATE TABLE `arrival_record` (
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `po_id`         BIGINT         NOT NULL                COMMENT 'PO ID',
    `project_id`    BIGINT         NOT NULL                COMMENT '项目ID',
    `material_code` VARCHAR(30)    NOT NULL                COMMENT '物料编码',
    `qty`           DECIMAL(12,2)  NOT NULL                COMMENT '到货数量',
    `arrived_at`    DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '到货时间',
    `operator`      VARCHAR(50)    DEFAULT NULL            COMMENT '登记人',
    `remark`        VARCHAR(200)   DEFAULT NULL            COMMENT '备注',
    `create_time`   DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_po` (`po_id`),
    KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='到货跟踪记录';

-- 技术交底记录（BR-09：未签字禁止开工）
DROP TABLE IF EXISTS `disclosure_record`;
CREATE TABLE `disclosure_record` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `project_id`   BIGINT        NOT NULL                COMMENT '项目ID',
    `workorder_id` BIGINT        DEFAULT NULL            COMMENT '关联工单ID',
    `discloser`    VARCHAR(50)   NOT NULL                COMMENT '交底人',
    `receivers`    VARCHAR(500)  DEFAULT NULL            COMMENT '被交底人（逗号分隔）',
    `signed`       TINYINT       NOT NULL DEFAULT 0      COMMENT '是否签字确认（0未签 1已签）',
    `sign_time`    DATETIME      DEFAULT NULL            COMMENT '签字时间',
    `content`      TEXT          DEFAULT NULL            COMMENT '交底内容',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`),
    KEY `idx_workorder` (`workorder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技术交底记录';

-- 生产工单（WO，编码引擎 WO 规则；BR-01/08/09 开工卡点内嵌）
DROP TABLE IF EXISTS `work_order`;
CREATE TABLE `work_order` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '工单ID',
    `wo_no`        VARCHAR(30)   NOT NULL                COMMENT '工单编号',
    `project_id`   BIGINT        NOT NULL                COMMENT '项目ID',
    `order_type`   VARCHAR(20)   NOT NULL DEFAULT 'PROTOTYPE' COMMENT '类型（PROTOTYPE样机/BATCH批量）',
    `qty`          INT           NOT NULL DEFAULT 1      COMMENT '台数',
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT/STARTED已开工/CLOSED已完工）',
    `started_at`   DATETIME      DEFAULT NULL            COMMENT '开工时间',
    `closed_at`    DATETIME      DEFAULT NULL            COMMENT '完工时间',
    `remark`       VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wo_no` (`wo_no`),
    KEY `idx_project` (`project_id`, `order_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产工单表';

-- FAI 首件检验报告（BR-02：整机厂首次发货必须持有，W6 发货拦截时引用）
DROP TABLE IF EXISTS `fai_report`;
CREATE TABLE `fai_report` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '报告ID',
    `workorder_id` BIGINT        NOT NULL                COMMENT '工单ID',
    `project_id`   BIGINT        NOT NULL                COMMENT '项目ID',
    `result`       VARCHAR(10)   NOT NULL                COMMENT '结论（PASS/FAIL）',
    `inspector`    VARCHAR(50)   NOT NULL                COMMENT '检验员',
    `checked_at`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '检验时间',
    `content`      TEXT          DEFAULT NULL            COMMENT '检验记录',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_workorder` (`workorder_id`),
    KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FAI首件检验报告';

-- 质检记录（工序质检）
DROP TABLE IF EXISTS `quality_check`;
CREATE TABLE `quality_check` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `workorder_id` BIGINT        NOT NULL                COMMENT '工单ID',
    `stage`        VARCHAR(50)   NOT NULL                COMMENT '工序',
    `result`       VARCHAR(10)   NOT NULL                COMMENT '结果（PASS/FAIL）',
    `checker`      VARCHAR(50)   NOT NULL                COMMENT '质检员',
    `remark`       VARCHAR(500)  DEFAULT NULL            COMMENT '备注（FAIL 时必填缺陷描述）',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '质检时间',
    PRIMARY KEY (`id`),
    KEY `idx_workorder` (`workorder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序质检记录';

-- 整改单（质检/验收 FAIL 派生）
DROP TABLE IF EXISTS `rectification`;
CREATE TABLE `rectification` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '整改单ID',
    `project_id`   BIGINT        NOT NULL                COMMENT '项目ID',
    `source`       VARCHAR(20)   NOT NULL                COMMENT '来源（QC质检/ACCEPT验收）',
    `source_id`    BIGINT        NOT NULL                COMMENT '来源记录ID',
    `issue`        VARCHAR(500)  NOT NULL                COMMENT '问题描述',
    `owner`        VARCHAR(50)   DEFAULT NULL            COMMENT '责任人',
    `due_date`     DATE          DEFAULT NULL            COMMENT '整改期限',
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT '状态（OPEN/CLOSED）',
    `close_note`   VARCHAR(500)  DEFAULT NULL            COMMENT '关闭说明',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`),
    KEY `idx_source` (`source`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='整改单';

-- 样机验收（BR-01：技改项目样机验收 PASS 后才允许创建批量工单）
DROP TABLE IF EXISTS `prototype_acceptance`;
CREATE TABLE `prototype_acceptance` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '验收ID',
    `project_id`   BIGINT        NOT NULL                COMMENT '项目ID',
    `workorder_id` BIGINT        NOT NULL                COMMENT '样机工单ID',
    `result`       VARCHAR(10)   NOT NULL                COMMENT '结论（PENDING/PASS/FAIL）',
    `acceptor`     VARCHAR(50)   DEFAULT NULL            COMMENT '验收人',
    `accepted_at`  DATETIME      DEFAULT NULL            COMMENT '验收时间',
    `comment`      VARCHAR(500)  DEFAULT NULL            COMMENT '验收意见',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`),
    KEY `idx_workorder` (`workorder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='样机验收表';

-- 编码规则：PO / WO
INSERT INTO `sys_code_rule` (`rule_code`, `rule_name`, `pattern`, `date_pattern`, `status`, `remark`) VALUES
('PO', '采购订单编号', 'PO-{yyyyMM}-{seq:4}', 'yyyyMM', 1, 'W4 样机域采购订单'),
('WO', '生产工单编号', 'WO-{yyyyMM}-{seq:4}', 'yyyyMM', 1, 'W4 样机域生产工单')
ON DUPLICATE KEY UPDATE `pattern` = VALUES(`pattern`);

-- ============================================================================
-- 9.19 W4 扫码过账链路（T-204：出入库记录→T+推送队列→对账，AGT-16 Auto）
-- 本机无 T+ 系统：推送走文件适配器 outbox 落盘，T+ 回执由 inbound 目录模拟（T-211 替换为真实解析）
-- ============================================================================

-- 出入库记录（扫码过账落库，IO 编码引擎；tplus_status 跟踪推送状态）
DROP TABLE IF EXISTS `inventory_record`;
CREATE TABLE `inventory_record` (
    `id`                BIGINT         NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `record_no`         VARCHAR(30)    NOT NULL                COMMENT '出入库单号（IO 编码引擎）',
    `project_id`        BIGINT         NOT NULL                COMMENT '项目ID',
    `workorder_id`      BIGINT         DEFAULT NULL            COMMENT '关联工单ID（出库必填）',
    `po_id`             BIGINT         DEFAULT NULL            COMMENT '关联PO ID（入库必填）',
    `material_code`     VARCHAR(30)    NOT NULL                COMMENT '物料编码',
    `material_name`     VARCHAR(100)   DEFAULT NULL            COMMENT '物料名称（冗余）',
    `direction`         VARCHAR(10)    NOT NULL                COMMENT '方向（IN采购入库/OUT生产出库）',
    `qty`               DECIMAL(12,2)  NOT NULL                COMMENT '数量',
    `barcode`           VARCHAR(64)    DEFAULT NULL            COMMENT '扫码条码内容',
    `source`            VARCHAR(10)    NOT NULL DEFAULT 'SCAN' COMMENT '来源（SCAN扫码/MANUAL手工）',
    `operator`          VARCHAR(50)    DEFAULT NULL            COMMENT '操作人',
    `scanned_at`        DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '扫码时间',
    `tplus_status`      VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT 'T+推送状态（PENDING待推送/PUSHED已推送/FAILED失败）',
    `tplus_push_time`   DATETIME       DEFAULT NULL            COMMENT 'T+推送时间',
    `tplus_voucher_no`  VARCHAR(30)    DEFAULT NULL            COMMENT 'T+凭证号',
    `remark`            VARCHAR(200)   DEFAULT NULL            COMMENT '备注',
    `create_time`       DATETIME       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT        DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_no` (`record_no`),
    KEY `idx_project` (`project_id`),
    KEY `idx_material` (`material_code`),
    KEY `idx_tplus_status` (`tplus_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出入库记录表';

-- T+ 推送队列（出入库单→T+ 单向推送；失败留痕可重试）
DROP TABLE IF EXISTS `tplus_push_queue`;
CREATE TABLE `tplus_push_queue` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '队列ID',
    `record_id`    BIGINT       NOT NULL                COMMENT '出入库记录ID',
    `record_no`    VARCHAR(30)  NOT NULL                COMMENT '出入库单号（冗余）',
    `payload_json` LONGTEXT     NOT NULL                COMMENT 'T+推送报文 JSON',
    `status`       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/PUSHED/FAILED）',
    `retry_count`  INT          NOT NULL DEFAULT 0      COMMENT '重试次数',
    `pushed_at`    DATETIME     DEFAULT NULL            COMMENT '推送成功时间',
    `error_msg`    VARCHAR(500) DEFAULT NULL            COMMENT '失败原因',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    `update_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_record` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='T+推送队列';

-- T+ 对账批次（本地已推送 ↔ T+ 导出回执 逐单核对，差异清单落库）
DROP TABLE IF EXISTS `tplus_reconciliation`;
CREATE TABLE `tplus_reconciliation` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '批次ID',
    `batch_no`       VARCHAR(30)  NOT NULL                COMMENT '对账批次号',
    `reconcile_date` DATE         NOT NULL                COMMENT '对账日期',
    `local_count`    INT          NOT NULL DEFAULT 0      COMMENT '本地已推送单数',
    `tplus_count`    INT          NOT NULL DEFAULT 0      COMMENT 'T+ 回执单数',
    `matched_count`  INT          NOT NULL DEFAULT 0      COMMENT '核对一致单数',
    `diff_count`     INT          NOT NULL DEFAULT 0      COMMENT '差异单数',
    `diff_json`      LONGTEXT     DEFAULT NULL            COMMENT '差异明细 JSON',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'DONE' COMMENT '状态（DONE）',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '对账时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='T+对账批次表';

-- 编码规则：IO 出入库单号
INSERT INTO `sys_code_rule` (`rule_code`, `rule_name`, `pattern`, `date_pattern`, `status`, `remark`) VALUES
('IO', '出入库单编号', 'IO-{yyyyMM}-{seq:4}', 'yyyyMM', 1, 'W4 扫码过账出入库单')
ON DUPLICATE KEY UPDATE `pattern` = VALUES(`pattern`);

-- ============================================================================
-- 9.20 W6 交付域（T-206：发货/物流/施工日志/分级验收，BR-02/03/04/05 硬卡点）
-- BR-02 整机厂首次发货必须持有 PASS 的 FAI 报告；BR-03 工程量 30% 自动触发中期验收；
-- BR-04 工程量 85% 自动触发后期验收；BR-05 验收单三方签字（业主+监理+我方）齐备才生效
-- ============================================================================

-- 发货单（BR-02 提交入口拦截）
DROP TABLE IF EXISTS `shipment`;
CREATE TABLE `shipment` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '发货单ID',
    `shipment_no`  VARCHAR(30)   NOT NULL                COMMENT '发货单号（SP 编码引擎）',
    `project_id`   BIGINT        NOT NULL                COMMENT '项目ID',
    `workorder_id` BIGINT        DEFAULT NULL            COMMENT '关联工单ID',
    `batch_no`     VARCHAR(30)   DEFAULT NULL            COMMENT '批次号',
    `qty`          INT           NOT NULL DEFAULT 1      COMMENT '发货台数',
    `carrier`      VARCHAR(100)  DEFAULT NULL            COMMENT '承运商',
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态（DRAFT草稿/SUBMITTED已发运）',
    `shipped_at`   DATETIME      DEFAULT NULL            COMMENT '发运时间',
    `remark`       VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    `create_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shipment_no` (`shipment_no`),
    KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发货单';

-- 物流跟踪记录
DROP TABLE IF EXISTS `logistics_record`;
CREATE TABLE `logistics_record` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `shipment_id` BIGINT       NOT NULL                COMMENT '发货单ID',
    `event_type`  VARCHAR(20)  NOT NULL                COMMENT '事件（DISPATCH发运/TRANSIT在途/ARRIVED到货/INSTALL进场安装）',
    `event_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
    `location`    VARCHAR(200) DEFAULT NULL            COMMENT '位置',
    `description` VARCHAR(500) DEFAULT NULL            COMMENT '说明',
    `operator`    VARCHAR(50)  DEFAULT NULL            COMMENT '记录人',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_shipment` (`shipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物流跟踪记录';

-- 施工日志（progress_pct 为当日累计完成工程量快照，项目当前工程量取最大值）
DROP TABLE IF EXISTS `construction_log`;
CREATE TABLE `construction_log` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `project_id`      BIGINT        NOT NULL                COMMENT '项目ID',
    `log_date`        DATE          NOT NULL                COMMENT '日志日期',
    `weather`         VARCHAR(20)   DEFAULT NULL            COMMENT '天气',
    `personnel_count` INT           DEFAULT NULL            COMMENT '在场人数',
    `work_content`    TEXT          DEFAULT NULL            COMMENT '施工内容',
    `progress_pct`    DECIMAL(5,2)  NOT NULL DEFAULT 0      COMMENT '累计完成工程量%（当日快照）',
    `issues`          VARCHAR(500)  DEFAULT NULL            COMMENT '当日问题',
    `recorder`        VARCHAR(50)   DEFAULT NULL            COMMENT '记录人',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_project_date` (`project_id`, `log_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='施工日志';

-- 分级验收单（BR-05 三方签字齐备生效；BR-03/04 工程量自动触发）
DROP TABLE IF EXISTS `acceptance`;
CREATE TABLE `acceptance` (
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '验收单ID',
    `project_id`          BIGINT        NOT NULL                COMMENT '项目ID',
    `stage`               VARCHAR(10)   NOT NULL                COMMENT '阶段（FIRST首台/MID中期/LATE后期/FINAL终验）',
    `status`              VARCHAR(20)   NOT NULL DEFAULT 'TRIGGERED' COMMENT '状态（TRIGGERED已触发/PENDING_SIGN待签字/PASS生效/FAIL不通过）',
    `progress_pct`        DECIMAL(5,2)  DEFAULT NULL            COMMENT '触发时工程量%',
    `acceptor_owner`      VARCHAR(50)   DEFAULT NULL            COMMENT '我方验收人',
    `acceptor_supervisor` VARCHAR(50)   DEFAULT NULL            COMMENT '监理验收人',
    `acceptor_client`     VARCHAR(50)   DEFAULT NULL            COMMENT '业主验收人',
    `signed_owner`        TINYINT       NOT NULL DEFAULT 0      COMMENT '我方签字（0/1）',
    `signed_supervisor`   TINYINT       NOT NULL DEFAULT 0      COMMENT '监理签字（0/1）',
    `signed_client`       TINYINT       NOT NULL DEFAULT 0      COMMENT '业主签字（0/1）',
    `accepted_at`         DATETIME      DEFAULT NULL            COMMENT '三方签字齐备（生效）时间',
    `comment`             VARCHAR(500)  DEFAULT NULL            COMMENT '验收意见',
    `triggered_by`        VARCHAR(10)   NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式（MANUAL/AGENT）',
    `create_time`         DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project_stage` (`project_id`, `stage`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分级验收单';

-- 编码规则：SP 发货单号
INSERT INTO `sys_code_rule` (`rule_code`, `rule_name`, `pattern`, `date_pattern`, `status`, `remark`) VALUES
('SP', '发货单编号', 'SP-{yyyyMM}-{seq:4}', 'yyyyMM', 1, 'W6 交付域发货单')
ON DUPLICATE KEY UPDATE `pattern` = VALUES(`pattern`);

-- ============================================================================
-- 9.21 M1 看板（T-209：九板数据集+三级视图+红灯引擎，规则配置化）
-- 九板口径自主制定（V2 仅定义框架）：投标签约/设计交付/齐套采购/生产质检/发货物流/
-- 验收收款/扫码过账/整改风险/总览驾驶舱；红灯规则全部存库可配
-- ============================================================================

-- 红灯规则配置（指标/comparator/阈值/级别/升级天数全部可配）
DROP TABLE IF EXISTS `redcard_rule`;
CREATE TABLE `redcard_rule` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '规则ID',
    `rule_code`     VARCHAR(50)   NOT NULL                COMMENT '规则编码',
    `rule_name`     VARCHAR(100)  NOT NULL                COMMENT '规则名称',
    `board`         VARCHAR(30)   NOT NULL                COMMENT '所属板编码（CONTRACT/DESIGN/KITTING/PRODUCE/SHIP/ACCEPT/TPLUS/RECTIFY/OVERVIEW）',
    `metric`        VARCHAR(50)   NOT NULL                COMMENT '指标 key（BoardsService 指标字典）',
    `comparator`    VARCHAR(5)    NOT NULL DEFAULT 'GT'   COMMENT '比较符（GT/LT/EQ）',
    `threshold`     DECIMAL(18,2) NOT NULL DEFAULT 0      COMMENT '阈值',
    `level`         VARCHAR(10)   NOT NULL DEFAULT 'YELLOW' COMMENT '级别（RED红灯/YELLOW黄灯）',
    `escalate_days` INT           NOT NULL DEFAULT 3      COMMENT '超期升级天数（OPEN 超 N 天未处理升级推送）',
    `enabled`       TINYINT       NOT NULL DEFAULT 1      COMMENT '启用（0/1）',
    `remark`        VARCHAR(200)  DEFAULT NULL            COMMENT '备注',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rule_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红灯规则配置';

-- 红灯记录（规则评估触发；OPEN→ASSIGNED 过堂→ESCALATED 超期升级→RESOLVED 消除）
DROP TABLE IF EXISTS `redcard`;
CREATE TABLE `redcard` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '红灯ID',
    `rule_code`     VARCHAR(50)   NOT NULL                COMMENT '规则编码',
    `rule_name`     VARCHAR(100)  NOT NULL                COMMENT '规则名称（冗余）',
    `board`         VARCHAR(30)   NOT NULL                COMMENT '所属板编码',
    `project_id`    BIGINT        DEFAULT NULL            COMMENT '项目ID（全局指标为空）',
    `metric_value`  DECIMAL(18,2) NOT NULL                COMMENT '触发时指标值',
    `level`         VARCHAR(10)   NOT NULL                COMMENT '级别（RED/YELLOW）',
    `status`        VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT '状态（OPEN/ASSIGNED已过堂/ESCALATED已升级/RESOLVED已消除）',
    `owner`         VARCHAR(50)   DEFAULT NULL            COMMENT '责任人（会议过堂指定）',
    `due_date`      DATE          DEFAULT NULL            COMMENT '处理期限（会议过堂指定）',
    `escalated_at`  DATETIME      DEFAULT NULL            COMMENT '升级时间',
    `resolved_at`   DATETIME      DEFAULT NULL            COMMENT '消除时间',
    `resolved_note` VARCHAR(500)  DEFAULT NULL            COMMENT '消除说明',
    `create_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    `update_time`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_rule` (`rule_code`),
    KEY `idx_status` (`status`),
    KEY `idx_board` (`board`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红灯记录';

-- 红灯规则种子（九板每板至少一条；阈值/级别可随时调整）
INSERT INTO `redcard_rule` (`rule_code`, `rule_name`, `board`, `metric`, `comparator`, `threshold`, `level`, `escalate_days`, `enabled`, `remark`) VALUES
('RC-CONTRACT-PENDING',  '合同待会签/审批/用印积压', 'CONTRACT', 'contract.pending_count',   'GT', 0, 'YELLOW', 7, 1, '投标签约板'),
('RC-DESIGN-DRAFT',      '方案草稿未受控发布',       'DESIGN',   'design.draft_count',       'GT', 0, 'YELLOW', 7, 1, '设计交付板'),
('RC-KITTING-BLOCK',     '项目齐套率低于95%',        'KITTING',  'kitting.block_count',      'GT', 0, 'RED',    3, 1, '齐套采购板（BR-08 数据源）'),
('RC-PO-OVERDUE',        'PO 到货逾期',              'KITTING',  'po.overdue_count',         'GT', 0, 'RED',    3, 1, '齐套采购板'),
('RC-DISCLOSURE-UNSIGN', '技术交底未签字',           'PRODUCE',  'disclosure.unsigned_count','GT', 0, 'RED',    3, 1, '生产质检板（BR-09 数据源）'),
('RC-QC-FAIL',           '质检/FAI 不合格',          'PRODUCE',  'qc.fail_count',            'GT', 0, 'YELLOW', 5, 1, '生产质检板'),
('RC-ACCEPT-PENDING',    '验收单待签字',             'ACCEPT',   'acceptance.pending_sign',  'GT', 0, 'YELLOW', 5, 1, '验收收款板（BR-05 载体）'),
('RC-PUSH-FAILED',       'T+ 推送失败单积压',        'TPLUS',    'inventory.push_failed',    'GT', 0, 'RED',    2, 1, '扫码过账板'),
('RC-RECON-DIFF',        'T+ 对账存在差异',          'TPLUS',    'recon.diff_count',         'GT', 0, 'RED',    2, 1, '扫码过账板（最近一次对账）'),
('RC-RECT-OPEN',         '整改单未闭环',             'RECTIFY',  'rectification.open_count', 'GT', 0, 'YELLOW', 7, 1, '整改风险板'),
('RC-RECT-OVERDUE',      '整改单逾期未闭环',         'RECTIFY',  'rectification.overdue_count','GT', 0, 'RED',  2, 1, '整改风险板')
ON DUPLICATE KEY UPDATE `rule_name` = VALUES(`rule_name`), `metric` = VALUES(`metric`),
  `comparator` = VALUES(`comparator`), `threshold` = VALUES(`threshold`), `level` = VALUES(`level`);

-- ============================================================================
-- 9.22 数据迁移（T-213：客户主体库建表；存量客户/项目/物料导入+清洗校验报告）
-- ============================================================================

-- 客户主体库（M2 主数据补全：名称唯一+社会信用代码，"能选不填"数据源）
DROP TABLE IF EXISTS `mdm_customer`;
CREATE TABLE `mdm_customer` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '客户ID',
    `customer_name`      VARCHAR(200) NOT NULL                COMMENT '客户名称（唯一）',
    `social_credit_code` VARCHAR(20)  DEFAULT NULL            COMMENT '统一社会信用代码（18位，可空）',
    `alias1`             VARCHAR(200) DEFAULT NULL            COMMENT '别名1',
    `alias2`             VARCHAR(200) DEFAULT NULL            COMMENT '别名2',
    `contact_person`     VARCHAR(50)  DEFAULT NULL            COMMENT '联系人',
    `contact_phone`      VARCHAR(30)  DEFAULT NULL            COMMENT '联系电话',
    `level`              VARCHAR(20)  DEFAULT NULL            COMMENT '客户分级（战略/重点/普通）',
    `remark`             VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_customer_name` (`customer_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户主体库';

-- ============================================================================
-- 9.23 W1 投标域（T-301：标讯/资格比对/招标文件分析/报价建议/竞品分析，AGT-01~05）
-- 标讯采集器按规划砍序规则改人工导入路径（AGT-01 解析结构化仍为 Auto），DMZ 抓取后续切换
-- ============================================================================

-- 标讯表（招标信息结构化；AGT-01 解析产出）
DROP TABLE IF EXISTS `tender_notice`;
CREATE TABLE `tender_notice` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '标讯ID',
    `tender_no`        VARCHAR(30)   NOT NULL                COMMENT '标讯编号（TN 编码引擎）',
    `title`            VARCHAR(300)  NOT NULL                COMMENT '标讯标题',
    `platform`         VARCHAR(50)   DEFAULT NULL            COMMENT '来源平台',
    `budget`           DECIMAL(18,2) DEFAULT NULL            COMMENT '预算金额',
    `deposit`          DECIMAL(18,2) DEFAULT NULL            COMMENT '投标保证金',
    `qualification_req` TEXT         DEFAULT NULL            COMMENT '资质要求（原文，分号/换行分隔）',
    `deadline`         DATE          DEFAULT NULL            COMMENT '投标截止日期',
    `publish_date`     DATE          DEFAULT NULL            COMMENT '发布日期',
    `status`           VARCHAR(20)   NOT NULL DEFAULT 'NEW'  COMMENT '状态（NEW/FOLLOWED跟进/APPLIED已报名/PREPARED投标准备/DECIDED已决策/WON中标/LOST未中标）',
    `analysis_json`    LONGTEXT      DEFAULT NULL            COMMENT 'AGT-03 招标文件分析结果 JSON',
    `import_source`    VARCHAR(20)   NOT NULL DEFAULT 'MANUAL' COMMENT '来源（MANUAL人工导入/AGENT采集）',
    `raw_text`         LONGTEXT      DEFAULT NULL            COMMENT '标讯原文',
    `remark`           VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    `create_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT       DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tender_no` (`tender_no`),
    KEY `idx_status` (`status`),
    KEY `idx_deadline` (`deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标讯表';

-- 公司资质清单（AGT-02 资格比对基准）
DROP TABLE IF EXISTS `company_qualification`;
CREATE TABLE `company_qualification` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '资质ID',
    `qual_name`   VARCHAR(200) NOT NULL               COMMENT '资质名称',
    `cert_no`     VARCHAR(100) DEFAULT NULL            COMMENT '证书编号',
    `valid_until` DATE        DEFAULT NULL            COMMENT '有效期至',
    `remark`      VARCHAR(200) DEFAULT NULL            COMMENT '备注',
    `create_time` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_qual_name` (`qual_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司资质清单';

-- 资格比对结果（AGT-02 产出）
DROP TABLE IF EXISTS `tender_qualification_check`;
CREATE TABLE `tender_qualification_check` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '比对ID',
    `tender_id`      BIGINT       NOT NULL                COMMENT '标讯ID',
    `requirement`    VARCHAR(500) NOT NULL                COMMENT '招标要求项',
    `company_status` VARCHAR(20)  NOT NULL                COMMENT '比对结论（SATISFIED满足/NOT_SATISFIED不满足/UNCERTAIN待核实）',
    `evidence`       VARCHAR(500) DEFAULT NULL            COMMENT '比对依据（命中的公司资质或缺失说明）',
    `checked_by`     VARCHAR(20)  NOT NULL DEFAULT 'AGT-02' COMMENT '比对方',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '比对时间',
    PRIMARY KEY (`id`),
    KEY `idx_tender` (`tender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资格比对结果';

-- 投标报价记录（AGT-04 建议载体 / 历史成交价数据源）
DROP TABLE IF EXISTS `tender_bid`;
CREATE TABLE `tender_bid` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '报价ID',
    `tender_id`      BIGINT        NOT NULL                COMMENT '标讯ID',
    `price`          DECIMAL(18,2) DEFAULT NULL            COMMENT '我方报价',
    `cost_estimate`  DECIMAL(18,2) DEFAULT NULL            COMMENT '预估成本',
    `gross_margin`   DECIMAL(5,2)  DEFAULT NULL            COMMENT '预估毛利率%',
    `strategy_text`  VARCHAR(1000) DEFAULT NULL            COMMENT '投标策略说明',
    `submitted_at`   DATETIME      DEFAULT NULL            COMMENT '递交时间',
    `open_date`      DATE          DEFAULT NULL            COMMENT '开标日期',
    `open_result`    VARCHAR(200)  DEFAULT NULL            COMMENT '开标结果（我方名次/中标价）',
    `create_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tender` (`tender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='投标报价记录';

-- 竞品开标记录（AGT-05 数据源）
DROP TABLE IF EXISTS `tender_competitor`;
CREATE TABLE `tender_competitor` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `tender_id`       BIGINT        NOT NULL                COMMENT '标讯ID',
    `competitor_name` VARCHAR(200)  NOT NULL                COMMENT '竞品单位',
    `price`           DECIMAL(18,2) DEFAULT NULL            COMMENT '竞品报价',
    `note`            VARCHAR(500)  DEFAULT NULL            COMMENT '备注',
    `create_time`     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_tender` (`tender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞品开标记录';

-- 编码规则：TN 标讯编号
INSERT INTO `sys_code_rule` (`rule_code`, `rule_name`, `pattern`, `date_pattern`, `status`, `remark`) VALUES
('TN', '标讯编号', 'TN-{yyyyMM}-{seq:4}', 'yyyyMM', 1, 'W1 投标域标讯')
ON DUPLICATE KEY UPDATE `pattern` = VALUES(`pattern`);

-- 公司资质种子（AGT-02 比对基准）
INSERT INTO `company_qualification` (`qual_name`, `cert_no`, `valid_until`, `remark`) VALUES
('电力工程施工总承包叁级', 'D361000001', '2027-12-31', '承接 35kV 及以下电力工程'),
('钢结构工程专业承包贰级', 'D261000002', '2028-06-30', '塔筒/钢结构制作安装'),
('ISO9001 质量管理体系认证', '00122Q30001R0M', '2027-03-31', '覆盖设计生产服务'),
('安全生产许可证', '(京)JZ安许证字[2024]000001', '2027-01-15', ''),
('起重机械安装改造维修许可', 'TS3310001-2026', '2028-01-01', '')
ON DUPLICATE KEY UPDATE `valid_until` = VALUES(`valid_until`);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- DDL脚本执行完毕，共创建78张业务表
-- ============================================================================
