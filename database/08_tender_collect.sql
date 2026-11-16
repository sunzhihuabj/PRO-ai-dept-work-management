-- ============================================================================
-- 08_tender_collect.sql  AGT-27 标讯联网自动采集与登记（T-415）
-- 招标商务平台配置 + 登录会话 + 采集运行监控 三表 + 标讯表采集溯源字段 + 能力点种子
-- 执行顺序：在 01_schema.sql / 02_seed_data.sql 之后执行
-- ============================================================================

USE dept_mgmt;

-- ----------------------------------------------------------------------------
-- 1. 招标商务平台配置表（R1：人工动态维护，凭据 AES-GCM 加密落库）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `tender_platform`;
CREATE TABLE `tender_platform` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '平台ID',
    `platform_name`         VARCHAR(100) NOT NULL                COMMENT '平台名称',
    `login_url`             VARCHAR(500) DEFAULT NULL            COMMENT '登录入口URL（NONE 模式可空）',
    `list_url`              VARCHAR(500) NOT NULL                COMMENT '标讯列表页URL（支持 {page} 占位符）',
    `detail_url_prefix`     VARCHAR(500) DEFAULT NULL            COMMENT '详情页URL前缀（列表项相对链接拼接用，空则直接用列表项链接）',
    `auth_type`             VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT '认证方式（NONE/FORM/COOKIE）',
    `auth_params_cipher`    TEXT         DEFAULT NULL            COMMENT '认证参数密文（AES-256-GCM，JSON：username/password/cookie），接口层永不回显明文',
    `collect_rules`         JSON         NOT NULL                COMMENT '采集规则配置（JSON：mode/list/detail/login/headers，见功能说明）',
    `request_interval_ms`   INT          NOT NULL DEFAULT 1500   COMMENT '请求最小间隔毫秒（限频，实际间隔=该值+随机抖动）',
    `jitter_ms`             INT          NOT NULL DEFAULT 800    COMMENT '随机抖动毫秒上限（拟人化反爬）',
    `max_pages`             INT          NOT NULL DEFAULT 3      COMMENT '单次采集最大列表页数',
    `enabled`               TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用（0停用 1启用）',
    `remark`                VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`               TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_platform_name` (`platform_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='招标商务平台配置表（AGT-27 采集源）';

-- ----------------------------------------------------------------------------
-- 2. 平台登录会话表（R2：Cookie/会话复用，会话串加密存储）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `tender_platform_session`;
CREATE TABLE `tender_platform_session` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `platform_id`       BIGINT       NOT NULL                COMMENT '平台ID',
    `cookies_cipher`    TEXT         DEFAULT NULL            COMMENT '会话 Cookie 密文（AES-256-GCM）',
    `login_type`        VARCHAR(20)  DEFAULT NULL            COMMENT '本次登录方式（FORM/COOKIE）',
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE有效/EXPIRED失效/BLOCKED疑似封禁）',
    `last_login_at`     DATETIME     DEFAULT NULL            COMMENT '最近登录/校验时间',
    `expires_at`        DATETIME     DEFAULT NULL            COMMENT '预计过期时间（可空）',
    `fail_count`        INT          NOT NULL DEFAULT 0      COMMENT '连续校验失败次数（>=3 标记 BLOCKED 告警）',
    `remark`            VARCHAR(500) DEFAULT NULL            COMMENT '备注（如验证码识别方式）',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_platform` (`platform_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台登录会话表（AGT-27）';

-- ----------------------------------------------------------------------------
-- 3. 采集运行监控表（R3/R6：全链路指标 + 断点续采检查点 + 异常现场快照）
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS `tender_collect_run`;
CREATE TABLE `tender_collect_run` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '运行ID',
    `platform_id`       BIGINT       NOT NULL                COMMENT '平台ID',
    `platform_name`     VARCHAR(100) DEFAULT NULL            COMMENT '平台名称快照',
    `trigger_type`      VARCHAR(20)  NOT NULL DEFAULT 'AGENT' COMMENT '触发方式（AGENT/AGENT_DIALOG/MANUAL）',
    `trigger_user_id`   BIGINT       DEFAULT NULL            COMMENT '触发用户',
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态（RUNNING/SUCCESS/PARTIAL/FAILED）',
    `login_status`      VARCHAR(20)  DEFAULT NULL            COMMENT '登录结果（NONE/FORM_OK/COOKIE_OK/FAILED）',
    `pages_fetched`     INT          NOT NULL DEFAULT 0      COMMENT '已抓取列表页数',
    `items_found`       INT          NOT NULL DEFAULT 0      COMMENT '发现标讯条数',
    `items_collected`   INT          NOT NULL DEFAULT 0      COMMENT '详情拉取成功条数',
    `extract_ok`        INT          NOT NULL DEFAULT 0      COMMENT '提取校验全过条数',
    `extract_repaired`  INT          NOT NULL DEFAULT 0      COMMENT '二次核验补全条数',
    `extract_failed`    INT          NOT NULL DEFAULT 0      COMMENT '提取失败（转人工）条数',
    `register_ok`       INT          NOT NULL DEFAULT 0      COMMENT '登记成功条数',
    `register_dup`      INT          NOT NULL DEFAULT 0      COMMENT '去重跳过条数',
    `checkpoint`        JSON         DEFAULT NULL            COMMENT '断点续采检查点（JSON：lastPage/doneUrls）',
    `last_error_type`   VARCHAR(50)  DEFAULT NULL            COMMENT '最近异常类型（LOGIN_FAILED/NETWORK/EXTRACT/REGISTER）',
    `error_snapshot`    TEXT         DEFAULT NULL            COMMENT '异常现场快照（URL+状态码+响应摘要，排查用）',
    `started_at`        DATETIME     DEFAULT NULL            COMMENT '开始时间',
    `finished_at`       DATETIME     DEFAULT NULL            COMMENT '结束时间',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`           TINYINT      DEFAULT 0               COMMENT '逻辑删除（0未删除 1已删除）',
    PRIMARY KEY (`id`),
    KEY `idx_run_platform` (`platform_id`, `create_time`),
    KEY `idx_run_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标讯采集运行监控表（AGT-27）';

-- ----------------------------------------------------------------------------
-- 4. 标讯表补采集溯源字段（R5：来源 URL + 采集运行ID）
-- ----------------------------------------------------------------------------
ALTER TABLE `tender_notice`
    ADD COLUMN `source_url`   VARCHAR(500) DEFAULT NULL COMMENT '标讯来源URL（联网采集详情页地址）' AFTER `raw_text`,
    ADD COLUMN `collect_run_id` BIGINT     DEFAULT NULL COMMENT '采集运行ID（AGT-27 采集批次溯源）' AFTER `source_url`,
    ADD COLUMN `tender_code`    VARCHAR(100) DEFAULT NULL COMMENT '招标编号（采集提取原始编号）' AFTER `tender_no`,
    ADD COLUMN `purchaser`      VARCHAR(200) DEFAULT NULL COMMENT '采购方/招标人' AFTER `platform`,
    ADD COLUMN `open_time`      VARCHAR(50)  DEFAULT NULL COMMENT '开标时间（原文口径）' AFTER `deadline`,
    ADD COLUMN `contact_info`   VARCHAR(300) DEFAULT NULL COMMENT '项目联系人及联系方式' AFTER `open_time`,
    ADD COLUMN `bid_requirement` TEXT        DEFAULT NULL COMMENT '投标要求（原文摘要）' AFTER `contact_info`,
    ADD COLUMN `verify_status`  VARCHAR(20)  DEFAULT 'NONE' COMMENT '提取校验状态（NONE/PASSED/REPAIRED/PENDING_VERIFY）' AFTER `bid_requirement`,
    ADD KEY `idx_collect_run` (`collect_run_id`),
    ADD UNIQUE KEY `uk_source_dedup` (`source_url`);

-- ----------------------------------------------------------------------------
-- 5. AGT-27 能力点登记（R7/Agent 接入；AGT-27 在 02 种子中未占用）
-- ----------------------------------------------------------------------------
INSERT INTO `sys_capability` (`capability_code`, `capability_name`, `category`, `allowed_identities`, `is_never`, `status`, `remark`) VALUES
('AGT-27', '标讯联网采集登记', 'agent', 'EXPERT,WORKER,ASSISTANT', 0, 1,
 'W1 投标域，Auto 级：平台登录→列表/详情采集→LLM 提取→校验→自动登记 TN 台账，缺字段二次核验，失败告警+快照');
