-- ============================================================================
-- 基于AI的部门工作管理软件 - 种子数据脚本
-- 说明: 包含用户、角色、菜单、部门、交付物模板、KPI指标等基础数据
-- 默认密码: 123456 (BCrypt密文)
-- ============================================================================

USE `dept_mgmt`;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================================
-- 系统管理模块 种子数据
-- ============================================================================

-- ==================== sys_user 种子数据 ====================
-- 默认密码: 123456  BCrypt密文
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `phone`, `email`, `role_codes`, `status`) VALUES
(1, 'sunzhihua',   '$2b$10$RKQHeTSFJpJQkzlcbzoxS.F55tUMFEOImnegl0VUtCTIq2GoKGBCS', '孙志华', '13800000001', 'sunzhihua@company.com',   '["ROLE_DEPT_LEADER"]',    1),
(2, 'lihailong',   '$2b$10$RKQHeTSFJpJQkzlcbzoxS.F55tUMFEOImnegl0VUtCTIq2GoKGBCS', '李海龙', '13800000002', 'lihailong@company.com',   '["ROLE_PROJECT_LEADER"]', 1),
(3, 'zhangsibei',  '$2b$10$RKQHeTSFJpJQkzlcbzoxS.F55tUMFEOImnegl0VUtCTIq2GoKGBCS', '张四辈', '13800000003', 'zhangsibei@company.com',  '["ROLE_REFORM_LEADER"]',  1),
(4, 'yangshuang',  '$2b$10$RKQHeTSFJpJQkzlcbzoxS.F55tUMFEOImnegl0VUtCTIq2GoKGBCS', '杨双',   '13800000004', 'yangshuang@company.com',   '["ROLE_TECHNICIAN"]',     1),
(5, 'fubinbin',    '$2b$10$RKQHeTSFJpJQkzlcbzoxS.F55tUMFEOImnegl0VUtCTIq2GoKGBCS', '付彬彬', '13800000005', 'fubinbin@company.com',     '["ROLE_DEVELOPER"]',     1),
(6, 'zhangyujing', '$2b$10$RKQHeTSFJpJQkzlcbzoxS.F55tUMFEOImnegl0VUtCTIq2GoKGBCS', '张玉静', '13800000006', 'zhangyujing@company.com', '["ROLE_ARCHIVIST"]',      1),
(7, 'shizhiqiang', '$2b$10$RKQHeTSFJpJQkzlcbzoxS.F55tUMFEOImnegl0VUtCTIq2GoKGBCS', '石志强', '13800000007', 'shizhiqiang@company.com', '["ROLE_AUDITOR"]',        1);

-- ==================== sys_role 种子数据 ====================
INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `role_sort`, `status`, `remark`) VALUES
(1, 'ROLE_DEPT_LEADER',    '部门领导',   1, 1, '部门负责人，拥有最高权限'),
(2, 'ROLE_PROJECT_LEADER', '项目负责人', 2, 1, '整机厂项目经理'),
(3, 'ROLE_REFORM_LEADER',  '技改负责人', 3, 1, '技改项目负责人'),
(4, 'ROLE_TECHNICIAN',     '技术员',     4, 1, '技术执行人员'),
(5, 'ROLE_DEVELOPER',      '研发人员',   5, 1, '新产品开发人员'),
(6, 'ROLE_ARCHIVIST',      '档案管理员', 6, 1, '文件归档管理员'),
(7, 'ROLE_AUDITOR',        '审核员',     7, 1, '审核流程审批人');

-- ==================== sys_user_role 种子数据 ====================
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES
(1, 1),
(2, 2),
(3, 3),
(4, 4),
(5, 5),
(6, 6),
(7, 7);

-- ==================== sys_menu 种子数据（PC端菜单树） ====================
INSERT INTO `sys_menu` (`id`, `menu_name`, `parent_id`, `order_num`, `path`, `component`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
-- 一级目录
(1,  '系统管理', 0, 1,  '/system',     NULL,                       'M', 1, 1, NULL,      'system'),
(2,  '项目管理', 0, 2,  '/project',    NULL,                       'M', 1, 1, NULL,      'project'),
(3,  '任务管理', 0, 3,  '/task',       NULL,                       'M', 1, 1, NULL,      'task'),
(4,  '文件管理', 0, 4,  '/file',       NULL,                       'M', 1, 1, NULL,      'file'),
(5,  '审核事件', 0, 5,  '/audit',      NULL,                       'M', 1, 1, NULL,      'audit'),
(6,  '绩效管理', 0, 6,  '/kpi',        NULL,                       'M', 1, 1, NULL,      'kpi'),
(7,  '精益改进', 0, 7,  '/lean',       NULL,                       'M', 1, 1, NULL,      'lean'),
(8,  '价值流',   0, 8,  '/vsm',        NULL,                       'M', 1, 1, NULL,      'vsm'),
(9,  '消息通知', 0, 9,  '/notice',     NULL,                       'M', 1, 1, NULL,      'notice'),
(10, '奖金管理', 0, 10, '/bonus',      NULL,                       'M', 1, 1, NULL,      'bonus'),
-- 系统管理子菜单
(11, '用户管理', 1, 1, 'user',        'system/user/index',        'C', 1, 1, 'system:user:list',    'user'),
(12, '角色管理', 1, 2, 'role',        'system/role/index',        'C', 1, 1, 'system:role:list',    'role'),
(13, '菜单管理', 1, 3, 'menu',        'system/menu/index',        'C', 1, 1, 'system:menu:list',    'menu'),
(14, '部门管理', 1, 4, 'dept',        'system/dept/index',        'C', 1, 1, 'system:dept:list',    'dept'),
(15, '操作日志', 1, 5, 'operlog',     'system/operlog/index',     'C', 1, 1, 'system:operlog:list', 'log'),
-- 项目管理子菜单
(16, '项目列表',   2, 1, 'list',       'project/list/index',       'C', 1, 1, 'project:list:list',       'list'),
(17, '项目阶段',   2, 2, 'phase',      'project/phase/index',      'C', 1, 1, 'project:phase:list',      'phase'),
(18, '里程碑管理', 2, 3, 'milestone',  'project/milestone/index',  'C', 1, 1, 'project:milestone:list',  'milestone'),
(19, '阶段门禁',   2, 4, 'gate',       'project/gate/index',       'C', 1, 1, 'project:gate:list',       'gate'),
(20, '交付物模板', 2, 5, 'deliverable', 'project/deliverable/index','C', 1, 1, 'project:deliverable:list','template'),
-- 任务管理子菜单
(21, '任务列表', 3, 1, 'list',     'task/list/index',     'C', 1, 1, 'task:list:list',     'task'),
(22, '任务池',   3, 2, 'pool',     'task/pool/index',     'C', 1, 1, 'task:pool:list',     'pool'),
(23, '工时统计', 3, 3, 'workhour', 'task/workhour/index', 'C', 1, 1, 'task:workhour:list', 'workhour'),
-- 文件管理子菜单
(24, '文件列表', 4, 1, 'list',     'file/list/index',     'C', 1, 1, 'file:list:list',     'file'),
(25, '文件分类', 4, 2, 'category', 'file/category/index', 'C', 1, 1, 'file:category:list', 'category'),
(26, '归档管理', 4, 3, 'archive',  'file/archive/index',  'C', 1, 1, 'file:archive:list',  'archive'),
-- 审核事件子菜单
(27, '审核流程', 5, 1, 'flow',   'audit/flow/index',   'C', 1, 1, 'audit:flow:list',   'flow'),
(28, '审核记录', 5, 2, 'record', 'audit/record/index', 'C', 1, 1, 'audit:record:list', 'record'),
(29, '事件管理', 5, 3, 'event',  'audit/event/index',  'C', 1, 1, 'audit:event:list',  'event'),
-- 绩效管理子菜单
(30, '指标管理', 6, 1, 'indicator', 'kpi/indicator/index', 'C', 1, 1, 'kpi:indicator:list', 'indicator'),
(31, '绩效评分', 6, 2, 'score',     'kpi/score/index',     'C', 1, 1, 'kpi:score:list',     'score'),
(32, '权重配置', 6, 3, 'weight',    'kpi/weight/index',    'C', 1, 1, 'kpi:weight:list',    'weight'),
-- 精益改进子菜单
(33, '改进提案', 7, 1, 'proposal', 'lean/proposal/index', 'C', 1, 1, 'lean:proposal:list', 'proposal'),
(34, 'A3报告',   7, 2, 'a3',       'lean/a3/index',       'C', 1, 1, 'lean:a3:list',       'a3'),
(35, '根因分析', 7, 3, 'rca',      'lean/rca/index',      'C', 1, 1, 'lean:rca:list',      'rca'),
(36, '经验教训', 7, 4, 'lessons',  'lean/lessons/index',  'C', 1, 1, 'lean:lessons:list',  'lessons'),
-- 价值流子菜单
(37, '价值流记录', 8, 1, 'record', 'vsm/record/index', 'C', 1, 1, 'vsm:record:list', 'vsm'),
(38, '浪费记录',   8, 2, 'waste',  'vsm/waste/index',  'C', 1, 1, 'vsm:waste:list',  'waste'),
-- 消息通知子菜单
(39, '系统通知', 9, 1, 'sys',     'notice/system/index',  'C', 1, 1, 'notice:sys:list',     'notice'),
(40, '我的消息', 9, 2, 'message', 'notice/message/index', 'C', 1, 1, 'notice:message:list', 'message'),
-- 奖金管理子菜单
(41, '项目奖金', 10, 1, 'project', 'bonus/project/index', 'C', 1, 1, 'bonus:project:list', 'bonus'),
(42, '奖金记录', 10, 2, 'record',  'bonus/record/index',  'C', 1, 1, 'bonus:record:list',  'record');

-- ==================== sys_dept 种子数据 ====================
INSERT INTO `sys_dept` (`id`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `leader`, `status`) VALUES
(1, 0, '0',   '技术部', 1, '孙志华', 1),
(2, 1, '0,1', '项目管理组', 1, '李海龙', 1),
(3, 1, '0,1', '技改组',     2, '张四辈', 1),
(4, 1, '0,1', '研发组',     3, '付彬彬', 1);

-- ============================================================================
-- 项目管理模块 种子数据
-- ============================================================================

-- ==================== deliverable_template 种子数据 ====================
-- 整机厂项目（project_type=1）19个阶段，约44个交付物模板
INSERT INTO `deliverable_template` (`id`, `template_name`, `project_type`, `phase_order`, `phase_name`, `file_type`, `required`, `description`) VALUES
-- 阶段1：项目立项
(1,  '项目立项申请书',   1, 1, '项目立项', 'docx', 1, '项目立项审批必备文件'),
(2,  '可行性研究报告',   1, 1, '项目立项', 'docx', 1, '技术与经济可行性分析'),
(3,  '项目章程',         1, 1, '项目立项', 'docx', 1, '项目范围与权责定义'),
-- 阶段2：需求分析
(4,  '需求规格说明书',   1, 2, '需求分析', 'docx', 1, '详细功能与性能需求'),
(5,  '需求评审记录',     1, 2, '需求分析', 'docx', 1, '需求评审会议纪要'),
-- 阶段3：技术方案
(6,  '技术方案报告',     1, 3, '技术方案', 'docx', 1, '总体技术路线与方案描述'),
(7,  '技术规格书',       1, 3, '技术方案', 'docx', 1, '设备与技术参数规格'),
-- 阶段4：方案评审
(8,  '方案评审记录',     1, 4, '方案评审', 'docx', 1, '方案评审过程记录'),
(9,  '评审意见书',       1, 4, '方案评审', 'docx', 1, '专家评审意见汇总'),
-- 阶段5：详细设计
(10, '详细设计图纸',     1, 5, '详细设计', 'dwg',  1, '机械/电气详细设计图'),
(11, '设计计算书',       1, 5, '详细设计', 'xlsx', 1, '关键参数计算'),
-- 阶段6：设计评审
(12, '设计评审报告',     1, 6, '设计评审', 'docx', 1, '设计评审结论报告'),
(13, '评审签到表',       1, 6, '设计评审', 'xlsx', 0, '设计评审参会人员签到'),
-- 阶段7：采购策划
(14, '采购计划书',       1, 7, '采购策划', 'docx', 1, '采购范围与计划安排'),
(15, '供应商短名单',     1, 7, '采购策划', 'xlsx', 1, '合格供应商名录'),
-- 阶段8：招标采购
(16, '招标文件',         1, 8, '招标采购', 'docx', 1, '招标技术及商务要求'),
(17, '评标报告',         1, 8, '招标采购', 'docx', 1, '评标过程与结果'),
(18, '采购合同',         1, 8, '招标采购', 'docx', 1, '签署的采购合同'),
-- 阶段9：到货验收
(19, '到货验收单',       1, 9, '到货验收', 'xlsx', 1, '设备到货验收记录'),
(20, '设备开箱记录',     1, 9, '到货验收', 'xlsx', 0, '设备开箱检查记录'),
-- 阶段10：安装调试
(21, '安装方案',         1, 10, '安装调试', 'docx', 1, '设备安装施工方案'),
(22, '调试记录',         1, 10, '安装调试', 'xlsx', 1, '调试过程数据记录'),
(23, '调试报告',         1, 10, '安装调试', 'docx', 1, '调试结论报告'),
-- 阶段11：试运行
(24, '试运行方案',       1, 11, '试运行', 'docx', 1, '试运行计划与方案'),
(25, '试运行记录',       1, 11, '试运行', 'xlsx', 1, '试运行每日记录'),
(26, '试运行报告',       1, 11, '试运行', 'docx', 1, '试运行总结报告'),
-- 阶段12：竣工验收
(27, '验收申请',         1, 12, '竣工验收', 'docx', 1, '竣工验收申请表'),
(28, '验收报告',         1, 12, '竣工验收', 'docx', 1, '竣工验收结论报告'),
(29, '验收纪要',         1, 12, '竣工验收', 'docx', 1, '验收会议纪要'),
-- 阶段13：培训交付
(30, '培训计划',         1, 13, '培训交付', 'docx', 1, '用户培训计划安排'),
(31, '培训记录',         1, 13, '培训交付', 'xlsx', 1, '培训签到与考核记录'),
(32, '交付清单',         1, 13, '培训交付', 'xlsx', 1, '项目交付物清单'),
-- 阶段14：质保服务
(33, '质保计划书',       1, 14, '质保服务', 'docx', 1, '质保期服务计划'),
(34, '服务响应方案',     1, 14, '质保服务', 'docx', 1, '售后服务响应机制'),
-- 阶段15：项目总结
(35, '项目总结报告',     1, 15, '项目总结', 'docx', 1, '项目整体总结报告'),
(36, '经验教训总结',     1, 15, '项目总结', 'docx', 1, '项目经验教训汇总'),
-- 阶段16：资料归档
(37, '归档清单',         1, 16, '资料归档', 'xlsx', 1, '归档资料清单'),
(38, '归档确认单',       1, 16, '资料归档', 'docx', 1, '归档确认签字'),
-- 阶段17：合同管理
(39, '合同台账',         1, 17, '合同管理', 'xlsx', 1, '合同执行情况台账'),
(40, '付款计划',         1, 17, '合同管理', 'xlsx', 1, '合同付款计划表'),
-- 阶段18：变更管理
(41, '变更申请',         1, 18, '变更管理', 'docx', 1, '项目变更申请单'),
(42, '变更评估报告',     1, 18, '变更管理', 'docx', 1, '变更影响评估报告'),
-- 阶段19：风险管理
(43, '风险登记册',       1, 19, '风险管理', 'xlsx', 1, '项目风险识别与登记'),
(44, '风险应对计划',     1, 19, '风险管理', 'docx', 1, '风险应对措施计划');

-- 技改项目（project_type=2）6个阶段，约17个交付物模板
INSERT INTO `deliverable_template` (`id`, `template_name`, `project_type`, `phase_order`, `phase_name`, `file_type`, `required`, `description`) VALUES
-- 阶段1：需求调研
(45, '调研报告',         2, 1, '需求调研', 'docx', 1, '现场调研情况报告'),
(46, '需求分析报告',     2, 1, '需求调研', 'docx', 1, '技改需求分析'),
(47, '技改立项书',       2, 1, '需求调研', 'docx', 1, '技改项目立项申请'),
-- 阶段2：方案设计
(48, '技改方案',         2, 2, '方案设计', 'docx', 1, '技术改造总体方案'),
(49, '技术评估报告',     2, 2, '方案设计', 'docx', 1, '技术可行性评估'),
(50, '预算表',           2, 2, '方案设计', 'xlsx', 1, '技改投资预算明细'),
-- 阶段3：实施改造
(51, '实施计划',         2, 3, '实施改造', 'docx', 1, '改造实施详细计划'),
(52, '施工方案',         2, 3, '实施改造', 'docx', 1, '现场施工技术方案'),
(53, '进度报告',         2, 3, '实施改造', 'docx', 1, '改造进度阶段性报告'),
-- 阶段4：测试验证
(54, '测试方案',         2, 4, '测试验证', 'docx', 1, '功能与性能测试方案'),
(55, '测试报告',         2, 4, '测试验证', 'docx', 1, '测试结果报告'),
(56, '验证记录',         2, 4, '测试验证', 'xlsx', 1, '关键指标验证记录'),
-- 阶段5：验收评估
(57, '验收申请',         2, 5, '验收评估', 'docx', 1, '技改验收申请表'),
(58, '验收报告',         2, 5, '验收评估', 'docx', 1, '技改验收结论报告'),
(59, '效益评估报告',     2, 5, '验收评估', 'docx', 1, '技改经济效益评估'),
-- 阶段6：项目收尾
(60, '总结报告',         2, 6, '项目收尾', 'docx', 1, '技改项目总结报告'),
(61, '归档资料清单',     2, 6, '项目收尾', 'xlsx', 1, '技改资料归档汇总');

-- ============================================================================
-- 绩效模块 种子数据
-- ============================================================================

-- ==================== kpi_indicator 种子数据 ====================
-- 整机厂项目（project_type=1）6个指标
INSERT INTO `kpi_indicator` (`id`, `indicator_name`, `indicator_code`, `project_type`, `indicator_type`, `base_score`, `unit`, `description`) VALUES
(1, '项目进度达成率', 'KPI_PROGRESS',       1, '进度', 100.00, '%',  '实际完成里程碑数/计划里程碑数'),
(2, '成本控制率',     'KPI_COST',           1, '成本', 100.00, '%',  '实际成本/目标成本'),
(3, '质量达标率',     'KPI_QUALITY',        1, '质量', 100.00, '%',  '一次验收合格率'),
(4, '客户满意度',     'KPI_SATISFACTION',   1, '满意度', 100.00, '分', '客户满意度调研评分'),
(5, '交付及时率',     'KPI_DELIVERY',       1, '交付', 100.00, '%',  '按时交付节点数/总交付节点数'),
(6, '安全合规率',     'KPI_SAFETY',         1, '安全', 100.00, '%',  '安全事故零发生');

-- 技改项目（project_type=2）7个指标
INSERT INTO `kpi_indicator` (`id`, `indicator_name`, `indicator_code`, `project_type`, `indicator_type`, `base_score`, `unit`, `description`) VALUES
(7,  '技改完成率',    'KPI_REFORM_COMPLETE',  2, '进度', 100.00, '%',  '实际完成技改项/计划技改项'),
(8,  '效率提升率',    'KPI_EFFICIENCY',       2, '效率', 100.00, '%',  '技改后效率提升幅度'),
(9,  '成本节约率',    'KPI_COST_SAVING',      2, '成本', 100.00, '%',  '技改实现成本节约'),
(10, '质量改善率',    'KPI_QUALITY_IMPROVE',  2, '质量', 100.00, '%',  '技改后质量指标改善'),
(11, '周期缩短率',    'KPI_CYCLE_REDUCE',     2, '周期', 100.00, '%',  '生产周期缩短幅度'),
(12, '安全达标率',    'KPI_SAFETY_REFORM',    2, '安全', 100.00, '%',  '技改安全合规'),
(13, '设备利用率',    'KPI_EQUIP_UTILIZE',    2, '设备', 100.00, '%',  '设备综合利用率OEE');

-- 新产品开发项目（project_type=3）6个指标
INSERT INTO `kpi_indicator` (`id`, `indicator_name`, `indicator_code`, `project_type`, `indicator_type`, `base_score`, `unit`, `description`) VALUES
(14, '研发进度达成率', 'KPI_RD_PROGRESS',     3, '进度', 100.00, '%',  '研发计划节点完成率'),
(15, '研发成本控制率', 'KPI_RD_COST',         3, '成本', 100.00, '%',  '研发实际支出/预算'),
(16, '产品合格率',     'KPI_RD_QUALITY',      3, '质量', 100.00, '%',  '样品测试合格率'),
(17, '技术创新度',     'KPI_RD_INNOVATION',   3, '创新', 100.00, '分', '技术创新评估得分'),
(18, '市场响应速度',   'KPI_RD_MARKET',       3, '市场', 100.00, '天', '从需求提出到方案确认周期'),
(19, '专利产出数',     'KPI_RD_PATENT',       3, '产出', 100.00, '件', '年度专利申请/授权数量');

-- ==================== kpi_weight 种子数据 ====================
-- 整机厂项目权重（合计100%）
INSERT INTO `kpi_weight` (`project_type`, `indicator_id`, `weight`, `period`) VALUES
(1, 1, 25.00, NULL),
(1, 2, 20.00, NULL),
(1, 3, 20.00, NULL),
(1, 4, 15.00, NULL),
(1, 5, 10.00, NULL),
(1, 6, 10.00, NULL);

-- 技改项目权重（合计100%）
INSERT INTO `kpi_weight` (`project_type`, `indicator_id`, `weight`, `period`) VALUES
(2, 7,  20.00, NULL),
(2, 8,  15.00, NULL),
(2, 9,  15.00, NULL),
(2, 10, 15.00, NULL),
(2, 11, 15.00, NULL),
(2, 12, 10.00, NULL),
(2, 13, 10.00, NULL);

-- 研发项目权重（合计100%）
INSERT INTO `kpi_weight` (`project_type`, `indicator_id`, `weight`, `period`) VALUES
(3, 14, 25.00, NULL),
(3, 15, 20.00, NULL),
(3, 16, 20.00, NULL),
(3, 17, 15.00, NULL),
(3, 18, 10.00, NULL),
(3, 19, 10.00, NULL);

-- ============================================================================
-- 演示业务数据（可选，用于开发联调）
-- ============================================================================

-- ==================== project 演示数据 ====================
INSERT INTO `project` (`id`, `project_code`, `project_name`, `project_type`, `customer_name`, `project_leader`, `status`, `contract_amount`, `target_cost`, `plan_start_date`, `plan_end_date`, `actual_start_date`, `description`, `create_by`) VALUES
(1, 'PRJ-2026-ZJC-001', '金风科技GW155机组消防系统集成项目', 1, '金风慧能', '李海龙', 1, 860000.00, 720000.00, '2026-01-15', '2026-09-30', '2026-01-15', '为金风科技GW155-4.5MW机组提供全套消防系统集成服务', '孙志华'),
(2, 'PRJ-2026-JG-001',  '华能福清风电场消防系统技改项目',   2, '华能福清', '张四辈', 1, 320000.00, 280000.00, '2026-03-01', '2026-06-30', '2026-03-01', '华能福清风电场站#3/#5/#7机组消防系统技术改造', '孙志华'),
(3, 'PRJ-2026-YF-001',  '智能消防控制器研发项目',           3, '内部研发', '付彬彬', 1, 560000.00, 500000.00, '2026-02-01', '2026-12-31', '2026-02-01', '新一代智能消防控制器自研产品开发', '孙志华');

-- ==================== project_phase 演示数据（项目1-整机厂前5个阶段） ====================
INSERT INTO `project_phase` (`project_id`, `phase_name`, `phase_order`, `phase_type`, `plan_start_date`, `plan_end_date`, `actual_start_date`, `actual_end_date`, `status`, `description`) VALUES
(1, '项目立项', 1, 1, '2026-01-15', '2026-01-22', '2026-01-15', '2026-01-22', 2, '项目立项阶段已完成'),
(1, '需求分析', 2, 1, '2026-01-23', '2026-02-12', '2026-01-23', '2026-02-10', 2, '需求分析阶段已完成'),
(1, '技术方案', 3, 1, '2026-02-13', '2026-03-05', '2026-02-13', '2026-03-04', 2, '技术方案阶段已完成'),
(1, '方案评审', 4, 1, '2026-03-06', '2026-03-12', '2026-03-06', '2026-03-11', 2, '方案评审阶段已完成'),
(1, '详细设计', 5, 1, '2026-03-13', '2026-04-30', '2026-03-13', NULL, 1, '详细设计阶段进行中');

-- ==================== task 演示数据 ====================
INSERT INTO `task` (`project_id`, `phase_id`, `task_name`, `task_type`, `value_type`, `assignee_id`, `priority`, `status`, `progress`, `plan_hours`, `actual_hours`, `start_date`, `deadline`, `actual_start_time`, `description`, `pull_mode`, `create_by`) VALUES
(1, 5, '消防控制柜电气原理图绘制', '图纸绘制', 1, 2, 1, 1, 65, 24.0, 15.5, '2026-03-13', '2026-03-25', '2026-03-13 09:00:00', '完成消防控制柜的电气原理图和接线图绘制', 0, '孙志华'),
(1, 5, '探测器布局方案设计',       '技术方案', 1, 4, 1, 1, 40, 16.0, 6.5, '2026-03-15', '2026-03-28', '2026-03-15 10:00:00', '根据舱体结构设计探测器最优布局方案', 0, '孙志华'),
(1, 5, 'BOM物料清单编制',          'BOM编制', 2, 2, 2, 0, 0,  20.0, 0.0,  '2026-03-20', '2026-04-05', NULL, '编制项目所需全部物料的BOM清单', 0, '孙志华'),
(2, NULL, '现场勘测#3机组消防系统', '现场勘测', 1, 4, 1, 1, 80, 12.0, 9.5, '2026-03-02', '2026-03-10', '2026-03-02 08:00:00', '对#3机组进行消防系统现场勘测', 0, '张四辈'),
(3, NULL, '控制器主控板硬件设计',   '技术方案', 1, 5, 1, 1, 50, 40.0, 21.0, '2026-02-05', '2026-03-15', '2026-02-05 09:00:00', '智能消防控制器主控板原理图、PCB设计', 0, '孙志华');

-- ==================== work_hour 演示数据 ====================
INSERT INTO `work_hour` (`task_id`, `user_id`, `work_date`, `hours`, `description`, `status`) VALUES
(1, 2, '2026-03-13', 6.0, '电气原理图框架绘制', 1),
(1, 2, '2026-03-16', 5.5, '控制回路接线图绘制', 1),
(1, 2, '2026-03-17', 4.0, '电源回路设计', 1),
(4, 4, '2026-03-02', 7.5, '#3机组现场勘测-探测器位置确认', 1),
(4, 4, '2026-03-03', 2.0, '勘测数据整理', 1),
(5, 5, '2026-02-05', 8.0, '主控板原理图设计-电源部分', 1),
(5, 5, '2026-02-06', 6.5, '主控板原理图设计-主控芯片部分', 1),
(5, 5, '2026-02-09', 6.5, '主控板PCB布局设计', 1);

-- ==================== user_message 演示数据 ====================
INSERT INTO `user_message` (`user_id`, `message_type`, `title`, `content`, `related_id`, `is_read`) VALUES
(2, 'TASK_ASSIGN',   '新任务分配通知', '孙志华给你分配了任务：消防控制柜电气原理图绘制', 1, 0),
(2, 'TASK_REMINDER', '任务截止提醒',   '任务"消防控制柜电气原理图绘制"将在2天后到期，请及时完成', 1, 0),
(4, 'TASK_ASSIGN',   '新任务分配通知', '张四辈给你分配了任务：现场勘测#3机组消防系统', 4, 1),
(2, 'AUDIT_RESULT',  '审核结果通知',   '你的方案评审申请已通过', 1, 1);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 种子数据导入完毕
-- ============================================================================


-- 任务池（标准任务模板库）
INSERT INTO `task_pool` (`task_name`, `task_type`, `project_type`, `phase_order`, `value_type`, `plan_hours`, `description`, `status`) VALUES
('技术方案编制', '方案设计', 1, 3, 1, 16.0, '编制整机厂消防系统技术方案', 1),
('现场施工安全交底', '安全管理', 1, 10, 1, 4.0, '施工前安全交底会议与记录', 1),
('系统联调测试', '测试验证', 3, 4, 1, 24.0, '研发系统联调测试与问题跟踪', 1),
('竣工资料整理', '资料归档', 2, 6, 2, 12.0, '技改项目竣工资料收集与整理', 1);

-- ============================================================================
-- T-101 平台底座种子数据
-- ============================================================================

-- 编码规则（PRJ 与存量格式完全一致：PRJ-年份-类型缩写-3位序号）
INSERT INTO `sys_code_rule` (`rule_code`, `rule_name`, `pattern`, `date_pattern`, `status`, `remark`) VALUES
('PRJ', '项目编号',   'PRJ-{yyyy}-{type}-{seq:3}', 'yyyy',   1, '类型缩写由建档时传入 type 参数（ZJC整机厂/JG技改/YF研发/OTH其他）'),
('HT',  '合同编号',   'HT-{yyyyMM}-{seq:4}',       'yyyyMM', 1, '合同台账启用后使用'),
('WL',  '物料编码',   'WL-{seq:6}',                NULL,     1, '一码一物，永不重置，对齐 T-003 物料归一清单口径');

-- 通知模板
INSERT INTO `sys_notification_template` (`template_code`, `title_template`, `content_template`, `message_type`, `status`) VALUES
('GENERIC_REMIND', '【{title}】', '{content}', '系统通知', 1),
('AUDIT_RESULT',   '审核结果：{bizName}', '您提交的「{bizName}」审核{result}，{comment}', '审核通知', 1);
