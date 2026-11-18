-- ============================================================================
-- Hermes Agent 运行参数配置表（AI 能力 Hermes 化迁移 M-2026-11-18）
-- ============================================================================
DROP TABLE IF EXISTS `hermes_agent_config`;
CREATE TABLE `hermes_agent_config` (
  `config_key`   VARCHAR(64)  NOT NULL COMMENT '配置键',
  `config_value` VARCHAR(500) NOT NULL COMMENT '配置值',
  `description`  VARCHAR(200) DEFAULT NULL COMMENT '说明',
  `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by`   VARCHAR(50)  DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Hermes Agent 运行参数配置';

INSERT INTO `hermes_agent_config` (`config_key`,`config_value`,`description`) VALUES
('migrate.chatViaHermes','true','chat 出口经 Hermes 原生 run（总迁移开关）'),
('migrate.syncTimeoutSec','180','同步等待 Hermes run 终态超时（秒）'),
('run.pollIntervalMs','2000','run 终态轮询间隔（毫秒）'),
('run.autoStopOnTimeout','true','同步等待超时后自动 stopRun'),
('delegate.enabled','true','Hermes 委托路由开关'),
('delegate.triggerWords','委托AI Agent员工,委托AI员工,委托Agent,交给AI员工,让AI员工跟进','委托触发词（逗号分隔）'),
('delegate.minLength','25','多步串联判定的最短消息长度'),
('output.language','中文','输出语言规则'),
('output.maxResultChars','2000','结果摘要最大字符数');
