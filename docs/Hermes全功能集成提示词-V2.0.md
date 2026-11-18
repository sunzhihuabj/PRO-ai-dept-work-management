# Hermes Agent 全功能集成提示词（V2.0）

> 基线：V1.0 嵌入式改造已完成 A1-A5（服务化/网关/MCP 只读 27 工具/身份透传/审批注册表）、B1-B5（委托路由/事件回写/写工具审批化/双向闭环）、C1-C2（对话卡片/委托面板）。
> 本提示词面向下一阶段（V2.0）全功能集成，按「模块 → 集成步骤 → 校验标准 → 前端入口 → 外设逻辑」四要素展开，可直接作为迭代开发指令执行。
> 迭代模式沿用：每个切片按 需求→论证→实现→测试→记录→commit 循环递交。

---

## 一、集成总体架构（现状基线 → V2.0 目标）

```
┌─────────────────────────── 前端 Vue3 + Element Plus（5173）───────────────────────────┐
│ AI 助手对话（Hermes 卡片/时间线） │ AI Agent 员工面板 │ 决策建议中心 │ AI 服务配置 │
│ ★新增：Hermes 控制台页 │ 语音交互条（耳/嘴） │ 视觉感知上传（眼） │ 记忆中心 │
└──────────┬─────────────────────────┬──────────────────────┬──────────────────────────┘
           │ REST/SSE                │ WebSocket(语音)       │ REST
┌──────────▼─────────┐   ┌───────────▼──────────┐   ┌───────▼──────────┐
│ backend (8081)      │   │ ai-gateway (8090)     │   │ Hermes Gateway    │
│ HermesService 客户端 │──▶│ sys_ai_provider_config│   │ (8642 API Server) │
│ 委托/事件/审批/记忆  │   │ 模型通道管理          │   │ config.yaml 原生  │
└──────────┬─────────┘   └───────────────────────┘   │ SOUL.md / skills  │
           │                                          │ toolsets/MCP      │
┌──────────▼──────────────────────────────────────────▼──────────────────────────┐
│ MySQL 3307：ai_conversation/ai_message/hermes_run_event/agent_decision_advice/  │
│ ai_user_memory/sys_ai_provider_config/sys_ai_call_log                           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**集成原则（红线，沿用 V1.0）**：
1. backend 业务代码不得直连模型 SDK/URL，一律经 AiGatewayClient → ai-gateway（8090）
2. Hermes 原生能力经 HermesService（8642 API Server）调用，Bearer 认证
3. 一切写操作必须经审批建议（HMAC 锁定）人工采纳后执行（HITL）
4. 模型调用 ≤35 次/分钟；受限时等待 10 分钟再调

---

## ⚡ 安全架构五项铁律（V2.0 强制约束，优先级高于一切功能需求）

> 五项铁律是 Hermes 与业务系统集成的**架构级约束**，所有模块（M1-M9）与外设（E1-E3）的设计必须满足；冲突时以铁律为准。

### 铁律一：API-Only 数据访问（禁直连数据库）

**约束**：Hermes 只能通过项目 API 访问业务数据，**禁止直连 MySQL/禁止进程内绕过 API 语义**。所有读写走后端 REST 接口（本项目统一 API 面 `/api/v1/**`）。

**现状差距（V1.0 遗留）**：A3/B4 的 MCP 工具（McpOpsBridge）当前为**进程内直调 service 层**——虽有权限校验与 LoginUser 透传，但未经过 HTTP API 层，绕过了 Controller 层的参数校验/操作日志/事务边界，不满足本铁律。

**集成步骤**：
1. **MCP 工具 REST 化改造（D1 切片强制项）**：新建 `ApiBridgeClient`（backend 内，服务自调用走 loopback HTTP 或拦截器模拟）——McpOpsBridge 全部 39 工具的 executor 改为 `ApiBridgeClient.invoke("GET /v1/tasks/{id}", params, userToken)` 形式
2. **鉴权令牌传递**：HermesService.submitRun 时将**当前登录用户的 JWT token**（非服务密钥）注入 Hermes run 上下文（`x-user-token` 元数据）；MCP 工具被 Hermes 调用时带回该 token → ApiBridgeClient 以 `Authorization: Bearer {userToken}` 调业务 API——与前端请求完全同构
3. **服务内调用防绕行**：ApiBridgeClient 走 `http://127.0.0.1:8081/api`（复用完整 Spring Security 过滤链：JWT 解析→LoginUser→FinePermission→Controller 参数校验→@OperLog 注解留痕），禁用任何 Mapper 直调捷径
4. **DDL 权限收紧**：Hermes 进程与 backend 的 DB 账号分离，Hermes 侧不授予任何数据库凭据（现状 Hermes 无 DB 访问，保持；backend DB 账号仅 backend 进程持有）

**校验标准**：
- McpOpsBridge 全部工具 executor 中 0 个 Mapper/Service 直调（代码审查 + ArchUnit 规则测试）
- 工具调用链路抓包验证：请求头携带用户 JWT、响应经 Controller 序列化
- 无 token/过期 token 调用 → 401（与前端行为一致）

### 铁律二：权限跟随当前用户（数据可见性同构）

**约束**：Hermes 以**当前登录用户的身份**访问 API——用户能看什么 Hermes 就能看什么，用户不能看的 Hermes 一律 403/404。禁止任何提权（服务账号超权查询）。

**现状差距**：A4 身份透传已实现 userId 级透传（LoginUser 构造），但存在两处提权隐患：① HermesService 内部轮询/Hermes 主动调用 MCP 时若上下文丢失会退化为系统身份；② 部分工具（如 delegations 的 leader 全量）以服务端权限判断而非用户 token 判断。

**集成步骤**：
1. token 全链路透传（铁律一步骤 2 落地后自动满足）：业务 API 的数据范围过滤（如委托面板 leader/本人逻辑）以 JWT 中的角色/用户 ID 为唯一依据
2. 上下文丢失防护：McpToolRegistry.buildTools 校验每次调用必带有效用户上下文，缺失直接拒绝（fail-closed，不降级为系统身份）
3. 定期越权回归：构造低权限用户委托 → Hermes 请求管理数据（用户列表/全部委托/他人任务）→ 断言 403；纳入回归集（30 条黄金指令中设 5 条越权用例，期望全部拦截）
4. 委托面板/时间线/审批建议的接收人逻辑复核：委托人=会话所有者（B2 已有），审批人=ROLE_DEPT_LEADER（B5 已有），V2.0 保持并补单测

**校验标准**：
- 越权用例 5/5 拦截（低权限用户 + Hermes 组合访问管理数据）
- 同一委托指令：leader 会话与普通用户会话的数据范围结果与前端页面一致
- 上下文缺失场景 fail-closed（拒绝执行而非提权执行）

### 铁律三：写操作必须确认（草稿→确认→落库）

**约束**：Hermes 生成的一切业务变更（草稿/建议/状态流转/文件移动）均为**建议态**，用户在界面上点「确认」才真正写入；Hermes 自身永不直接完成写操作。

**现状基础（已达标，V2.0 保持并扩展）**：B4 写工具审批化（MCP 写工具 → PENDING 建议 + HMAC 锁定）、B5 双向闭环（采纳 once/驳回 deny）、A5 ApprovalActionRegistry。

**集成步骤**：
1. V2.0 新增写工具（M3 file.archive.borrow/file.version.tag、M6 doc.draft.create/doc.compliance.mark、M4 催办触发）**全部注册进 ApprovalActionRegistry 白名单**——新工具上线 checklist 第一项
2. 确认交互统一化：建议中心（AgentAdviceCenter）+ 委托面板（HermesPanel）+ 对话内卡片三处均可采纳/驳回；批量确认仅限同场景低风险工具（file.category.bind 类），高风险（删除/审批流转/资金相关）强制逐条确认
3. 草稿可见性：写建议生成时同步在对话内渲染「预览卡」（工具名/参数/影响范围），用户可先看再决定去哪确认

**校验标准**：
- 白名单外写工具 MCP 不暴露（tools/list 无该工具）——B4 既有测试保持
- 全部写路径端到端：未确认前业务表 0 写入（B4 集成测试模式复用，逐工具验证）
- 高风险工具无批量确认入口（前端 + 后端双重校验）

### 铁律四：敏感数据脱敏（出网内容零泄露）

**约束**：出网搜索（M5 联网检索）时，客户名、金额、手机号、身份证、项目内部代号等敏感信息**不得发给外部搜索引擎/LLM 外部通道**；结果返回后在项目内还原关联。

**现状差距**：网关 desensitize-enabled 仅覆盖商用 OPENAI_COMPAT 通道的 LLM 调用；Hermes 原生出网（web 工具集）在 Hermes 进程内，**不经网关**，无脱敏。

**集成步骤**：
1. **脱敏网关化（强制）**：Hermes web 检索不走 Hermes 进程直出网，改为经项目 API：新增 `POST /v1/ai/websearch/proxy`（backend）——Hermes 检索意图 → backend 脱敏器（正则+NER：客户名/金额/证件号/手机号 → `CLIENT_A`/`AMOUNT_RANGE` 等占位符）→ 外部搜索 API → 结果 → backend 还原映射 → 返回 Hermes
2. 脱敏映射表：`desensitize_map` 表（run_id/占位符/真实值），随 run 结束 24h 后自动清理（TTL）；仅 run 拥有者可触发还原
3. 出网内容审计：proxy 记录 `outbound_log`（脱敏前摘要哈希/脱敏后全文/目标站点），出网请求 100% 留痕；含敏感词的出网请求直接拒绝并生成 WARNING 建议
4. 任务书构造侧双保险：HermesDelegateService 构造任务书时预脱敏（复用同一脱敏器），防止 LLM 上下文里的敏感字段被 Hermes 原生工具带出

**校验标准**：
- 含真实客户名+金额的检索指令 → 外部搜索请求抓包 0 敏感词（占位符替代）
- 还原正确率：结果中 CLIENT_A → 正确客户（5/5）
- 脱敏器单测覆盖：客户名/金额/手机号/身份证/内部代号 5 类 ≥95% 召回
- 直连出网阻断：Hermes 进程出口防火墙仅放行 127.0.0.1（web 工具集只能打到项目 proxy）

### 铁律五：操作留痕（全链路可追溯）

**约束**：Hermes 每次工具调用、每次模型调用、每次出网、每次审批动作均记录日志，可按 run_id/用户/时间三维追溯；日志不可篡改。

**现状基础**：sys_ai_call_log（模型调用，网关审计）、hermes_run_event（run 事件，B3）、SysOperLog（业务操作，@OperLog）已有。

**集成步骤**：
1. 补齐 MCP 工具调用级审计：新增 `hermes_tool_audit` 表（id/run_id/tool_name/params_digest 参数哈希不存原文/user_id/outcome SUCCESS/DENIED/FAILED/cost_ms/create_time），McpOpsBridge 统一 AOP 切面写入；「铁律一」REST 化后由 @OperLog 与本表双写（工具视角 + API 视角）
2. 留痕 ID 贯通：前端 Hermes 卡片/委托面板/建议中心展示「审计链」入口——run_id → hermes_run_event（过程）→ hermes_tool_audit（工具调用）→ sys_ai_call_log（模型）→ outbound_log（出网）→ SysOperLog（落库）五表联查视图
3. 防篡改：审计表 append-only（应用层禁 UPDATE/DELETE，MyBatis 拦截器拒绝非 insert 操作）+ 每日摘要哈希链（前一日的全表 hash 存入当日首行）
4. 追溯查询 API：`GET /v1/system/hermes-audit?runId=|userId=|from=|to=`（leader 权限），导出 CSV

**校验标准**：
- 任一委托 run 可完整还原：谁在何时让 Hermes 用了什么工具改了什么数据（五表联查 100% 闭环）
- 一次工具调用五表记录 ≤1s 延迟（审计不阻塞主流程，异步写入）
- 审计表 UPDATE/DELETE 被拦截（拦截器测试）；哈希链断链可检出

---

**五项铁律与模块映射速查**：

| 铁律 | 主要落点模块 | 切片 |
| --- | --- | --- |
| 一 API-Only | M2/M3/M6 全部工具 REST 化 | D1（强制前置） |
| 二 权限跟随 | 全模块 + 越权回归集 | D1 |
| 三 写确认 | M3/M4/M6 新写工具白名单注册 | 各切片上线 checklist |
| 四 脱敏 | M5 联网检索 + 任务书构造 | D4 |
| 五 留痕 | 全模块 + 审计链视图 | D1（表）/D7（联查视图） |

---

## 二、九大核心模块集成指令

### M1. Hermes 原生配置模块

**能力映射**：模型参数（temperature/max_tokens/context_length）、模型切换（providers 路由）、权限管控（capability 白名单/精细权限/MCP requiresApproval）。

**集成步骤**：
1. 新建 `HermesConfigService`（backend）：读写 `%LOCALAPPDATA%\hermes\config.yaml` 的 model/providers 段（snakeyaml 解析；写入前备份 config.yaml.bak-时间戳；写入后调 `hermes gateway restart` 热加载或提示人工重启）
2. 网关侧复用既有 `sys_ai_provider_config`（AiConfig 页面已有）：打通「网关通道 ↔ Hermes providers.custom:dept-gateway」双视图，配置页增加「通道健康探测」（复用 `available()` 30s 缓存）
3. 权限管控：`sys_capability` 白名单 + `FinePermissionService` 精细权限点（已有），V2.0 增加「Hermes 工具级授权矩阵」表 `hermes_tool_perm`（tool_name × role × allow/deny），McpOpsBridge.checkPermission 读取
4. 新增 REST：`GET/PUT /v1/ai/hermes/config`（model/providers 子集）、`POST /v1/ai/hermes/config/probe`（通道探测）、`GET/PUT /v1/ai/hermes/tool-perms`

**校验标准**：
- 修改模型配置后 5 分钟内新委托 run 使用新模型（sys_ai_call_log.model 字段验证）
- 配置写入失败自动回滚备份，不产生半写状态
- 权限矩阵拦截验证：deny 角色调写工具 → PENDING 建议生成被拒并返回权限提示

**前端入口**：「AI 服务配置」页新增「Hermes 原生配置」Tab（模型/参数/工具授权矩阵三区块）；所有变更二次确认 + 操作日志（SysOperLog 已有）

---

### M2. 自然语言对话驱动模块

**能力映射**：V1.0 已有四级路由（WF意图→Ops单轮→Agent能力点→Hermes委托→通用对话）。V2.0 目标：**从"预设业务流程"升级为"任意业务流程编排"**。

**集成步骤**：
1. 委托判定从启发式升级为 **LLM 意图判别**（阶段二遗留项）：`AiChatService.routeAndReply` 第四级前插入轻量意图判定（复用 Ops decide 的 JSON schema 模式，输出 `{delegate:boolean, taskbook:string}`），只在含「委托/跟进/多步串联」语义时升级第四级；命中失败保持启发式兜底
2. Hermes 任务书模板库 `hermes_taskbook_template` 表：按业务场景（比对/催办/归档/审核/检索）预置任务书骨架，委托时按场景注入 SOUL 口径
3. 流程编排：Hermes run 内通过 `agent.capability.run` MCP 工具（B4 已有）回调项目 Agent 能力点，实现「Hermes 编排 + 项目原子能力执行」混合驱动

**校验标准**：
- 15 条黄金指令（C3 矩阵扩展至 30 条）路由正确率 ≥90%
- 委托指令 → hermes 卡片 ≤2s；run 终态回写 ≤10 分钟
- 误判为委托的纯查询 ≤5%（LLM 判别开启后统计）

**前端入口**：AI 助手对话（已有）+ 会话顶部「模式提示条」（当前路由等级/委托中状态徽标）

---

### M3. 文件全生命周期管理模块（整理/归档/版本/检索）

**能力映射**：项目已有 FileUpload/FileList/ArchiveManagement + A3 MCP 只读工具 file.*/archive.*；V2.0 补写路径。

**集成步骤**：
1. MCP 写工具白名单扩展（ApprovalActionRegistry）：`file.upload`（经后端代理落盘）、`file.category.bind`、`archive.filing`、`archive.borrow`（既有档案借阅）、`file.version.tag`
2. Hermes 侧新建文件整理 Skill（`%LOCALAPPDATA%\hermes\skills\dept-file-organizer.md`）：输入目录/规则 → 调 file.* MCP 工具执行「扫描→去重→分类建议（只读）→ 批量移动（写工具经审批）」
3. 版本留存：file 模块加 `file_version` 表（file_id/version/hash/operator/created_at），上传同名文件自动版本+1，MCP 工具 `file.version.list` 只读暴露
4. 检索调用：kb.search（已有）扩展 file 全文索引（T-106 RAG 既有向量通道）

**校验标准**：
- 整理 Skill 端到端：10 个混合文件 → 分类建议正确 ≥80%，移动操作 100% 经审批
- 版本链完整：同名覆盖后 file.version.list 可回溯全部历史版本并可下载
- 检索：关键词命中上传文件正文（向量 Top5 含目标文件）

**前端入口**：AI 助手对话上传附件（自动进入视觉识别，见 E1）+「AI Agent 员工」面板新增「文件整理任务」快捷委托卡片

---

### M4. 流程管控模块（催办/监管/风险提示）

**能力映射**：任务状态流转事件（task.status.changed）+ DecisionAdvice（TASK_CREATED/TASK_STATUS_CHANGED/PROJECT_PROGRESS 场景已有）+ 事件审批流（EventApproval 已有）。

**集成步骤**：
1. 任务催办 Agent 化：`AgentTaskScheduler`（已有 queued 轮询）增加「超期任务催办」扫描（deadline < now - N 天 & status != 已完成）→ 生成 `TASK_OVERDUE_REMIND` 建议（通知 assignee 站内信 + 可选 Hermes 委托拟催办话术）
2. 全流程监管：`sys_domain_event` 总线（已有 11 事件类型）订阅侧增加「流程健康度巡检」Job：每工作日 09:00 汇总在途流程（任务/合同/审批/交付）停留时长 → 超 SLA 生成 WARNING 建议
3. 异常节点实时提示：Hermes 事件流 `run.failed`/`approval.request`（B3 白名单已有）→ 实时推送前端（SSE 下行通道 `GET /v1/ai/ws/hermes/stream`，EventSource），前端右上角风险 Toast + 声音提醒（联动 E2 嘴巴模块）
4. MCP 只读补充：`task.overdue.list`、`process.sla.report`（巡检报告）

**校验标准**：
- 构造超期任务 → 5 分钟内建议生成且站内信到达
- run.failed 事件 → 前端 SSE Toast ≤3s
- 巡检 Job 幂等（同日重复执行不重复生成建议）

**前端入口**：「AI Agent 员工」面板顶部新增「风险预警」横幅（实时 SSE）；Dashboard 增加「流程健康度」卡片

---

### M5. 信息检索模块（联网查询/政策/技术文档）

**能力映射**：Hermes 原生 web 工具集（toolsets 增加 web-search）+ 内部 kb.search（已有）。

**集成步骤**（全部经铁律四脱敏网关，Hermes 进程不直出网）：
1. Hermes 原生 web 工具集不启用直出网；改接项目检索代理：MCP 新增 `web.search.proxy` 工具（backend 实现）→ `POST /v1/ai/websearch/proxy` 脱敏 → 外部搜索 API → 还原 → 返回 Hermes（铁律四完整链路）
2. 项目侧检索路由：对话「搜一下外部/联网/最新政策…」→ Ops decide 新增 `web.search` 虚工具 → 转第四级 Hermes 委托（任务书=检索请求+输出格式要求「来源+链接+要点」；任务书构造时预脱敏）
3. 检索结果结构化落库：hermes 回写消息解析「来源清单」段落 → `web_research_log` 表（query/sources_json/run_id），MCP 只读 `web.research.history` 供复盘
4. 出网双闸：防火墙层（Hermes 进程出口仅放行 127.0.0.1）+ 应用层（唯一出网口为脱敏 proxy），出网审计入 outbound_log

**校验标准**：
- 「联网查一下最新的制造业数字化转型政策」→ run 终态回写含 ≥2 个带链接来源
- 内外部检索自动分流：内部知识命中（kb.score 高）不触发出网
- 全部出网调用 100% 留痕

**前端入口**：AI 助手对话（检索类指令自动路由）；「AI Agent 员工」面板新增「联网调研」Tab（历史调研记录 + 来源溯源）

---

### M6. 文件处理模块（起草/编辑/合规审核）

**能力映射**：报告生成类 Agent 能力（已有基础）+ Hermes 文档写作能力（原生）。

**集成步骤**：
1. MCP 写工具扩展：`doc.draft.create`（docx/md 模板渲染）、`doc.version.commit`、`doc.compliance.mark`（合规批注写入）
2. 合规审核 Skill：`dept-doc-reviewer.md`——输入文档 → 调 kb.search 拉取行业规范片段 → 逐节比对输出「合规风险清单（章节/风险项/依据/建议改法）」→ 写工具生成批注（经审批）
3. 起草流程编排：对话「起草XX方案」→ Hermes 委托（任务书含模板选择+要素清单）→ 产出经 `doc.draft.create` 落库 → 前端「文档工作台」查看/编辑/一键送审（复用 EventApproval）
4. 输出格式：docx 导出（office-plugin 通道已有）+ Markdown 双格式

**校验标准**：
- 起草端到端：指令 → 草稿落库 ≤10 分钟，要素完整率按模板 checklist ≥90%
- 合规审核输出结构化风险清单（JSON schema 校验），批注 100% 经人工审批后写入
- 导出 docx 打开无格式损坏

**前端入口**：AI 助手对话 +「文档工作台」页（草稿列表/版本对比/合规批注面板/送审按钮）

---

### M7. 专业输出模块（行业标准合规意见/优化建议）

**能力映射**：kb 知识库（行业规范片段）+ LLM 专业推理。

**集成步骤**：
1. 知识库分域：kb 增加 `domain` 字段（风电消防/制造/质量/安全…），上传时自动/手动打标
2. 专业意见 Agent：`AGT-ADVISOR` 能力点（AgentCapabilityRegistry 注册）——输入业务问题 → kb 分域检索（Top-K 依据）→ 输出「意见 + 依据引用（source+ref，复用 DecisionAdvice evidenceJson 结构）+ 置信度」
3. 置信度 <0.85 强制人工审核（复用 requiresApproval 机制），意见经采纳后可一键转为正式建议单
4. 行业标准版本管理：kb 文档带 `standard_version` 元数据，过期标准自动降权并在意见中标注「依据已过期」

**校验标准**：
- 5 条行业黄金问题 → 意见含可溯依据引用（source:ref 100% 可回链到 kb 文档）
- 低置信度意见 100% 进入人工审核流
- 依据引用回链点击可打开原文档对应位置

**前端入口**：AI 助手对话（专业问题自动路由 AGT-ADVISOR）；「决策建议中心」增加「专业意见」场景筛选

---

### M8. 自迭代模块（自我进化/能力迭代）

**能力映射**：Hermes 原生 skills 体系（skills 目录 + SOUL.md 自定义）+ spawn-ledger + 项目侧反馈闭环。

**集成步骤**：
1. 反馈采集：消息评分（T-409 rating 已有 ±1）+ 任务回写满意度 → `agent_feedback` 表（run_id/rating/comment/dimension）
2. 迭代管线：每周 Job 汇总低分 run（rating<0 或 failed）→ 归因分类（任务书不清/工具缺失/知识不足）→ 生成「SOUL 口径补丁建议」/「新工具需求建议」→ 人工采纳后写入 SOUL.md 追加段（版本化，git 管理 hermes 配置目录）
3. Skill 沉淀：高频成功任务书自动模板化（M2 任务书模板库反哺），模板带使用统计与成功率
4. 能力评估回归集：`hermes_regression_suite`（30 条黄金指令 + 评分），每次 SOUL/工具变更后自动回归，通过才发布

**校验标准**：
- 反馈闭环端到端：评分 <0 → 周汇总建议生成 → 采纳 → SOUL.md 变更留痕（diff 可见）
- 回归集通过率变更后不下降（≥基线 90%）
- SOUL.md 全部变更可回滚（版本链）

**前端入口**：「AI Agent 员工」面板新增「自迭代」Tab（反馈统计/补丁建议列表/SOUL 版本 diff/回归报告）

---

### M9. 记忆存储模块（全量记忆/跨会话调用）

**能力映射**：项目侧 ai_user_memory（P0-3 已有 recall）+ Hermes 原生会话记忆（response_store.db/state.db）。

**集成步骤**：
1. 记忆分层：L1 会话内（Hermes 原生）、L2 用户长期事实（ai_user_memory，AiMemoryService.recall 已注入 prompt）、L3 业务事实（MySQL 结构化，MCP 只读工具实时查询）、L4 组织知识（kb）
2. 记忆写入策略：对话结束/会话摘要时由 LLM 抽取「长期事实」→ `ai_memory` 建议化写入（写操作，经用户确认后落库，防止污染）
3. 跨会话调用：新会话首条消息自动注入 L2 摘要 + L3 关键上下文（复用 P0-3 注入点）；Hermes 委托任务书自动携带委托人 L2/L3 上下文
4. 记忆管理：`GET/DELETE /v1/ai/memory`（用户查看/删除本人记忆，合规要求）+ 记忆有效期（TTL 90 天自动过期）

**校验标准**：
- 会话 A 告知「我负责三一重能项目交付」→ 新会话 B 问「我的项目进展」→ 回复正确关联项目（跨会话召回率 5/5）
- 记忆写入 100% 经用户确认；用户删除后 recall 不再返回
- 委托任务书含委托人上下文（hermes_run_event.message.interim 可见注入痕迹）

**前端入口**：「AI 助手」侧栏新增「记忆」抽屉（本人记忆列表/编辑/删除）；对话中显示「已记忆」角标

---

## 三、三类感知交互外设专属配置

### E1. 眼睛模块（视觉感知）

**调用逻辑**：
- **页面内容读取**：前端「AI 助手」增加「读屏」按钮 → 获取当前路由组件名 + 用户可见区域关键 DOM 文本摘要（脱敏：仅 class 标记 `data-ai-readable` 的区域）→ 作为上下文注入对话（"我正在看XX页面，请基于此回答"）
- **图片/文档识别**：对话上传图片 → 后端转调网关多模态通道（sys_ai_provider_config 增加 `vision_model` 通道字段，GLM-4.6V 系）→ 识别结果进对话上下文；上传文档（pdf/docx）→ 既有解析管线（office-plugin）+ kb 入库
- **流程运行状态可视化监控**：Hermes 事件流（B3）已结构化 → 前端时间线组件增加「图形化模式」（甘特/泳道图渲染 tool.started→completed 序列）；「AI Agent 员工」面板卡片状态灯（运行中/等待授权/终态）实时 SSE

**集成步骤**：
1. backend：`POST /v1/ai/vision/describe`（multipart 图片 → 网关 vision 通道 → 结构化描述）；`data-ai-readable` 标注规范写入前端开发文档
2. 前端：AiWorkspace 对话框增加图片上传按钮（已有文件上传组件复用）；读屏按钮（当前页面摘要注入）
3. 时间线图形化：HermesCard.vue 增加「视图切换」（列表 ↔ 泳道图，ECharts gantt）

**校验标准**：
- 上传含表格的业务图片 → 识别出表格结构并正确转述（5 样本 ≥80% 要素正确）
- 读屏摘要不包含权限外数据（低权限用户读屏不含管理字段）
- 泳道图与事件列表数据一致（同一 run 双视图切换无损）

**前端入口**：AI 助手对话附件按钮/读屏按钮；HermesCard 视图切换；委托面板状态灯

### E2. 嘴巴模块（语音输出/TTS）

**调用逻辑**：
- 主通道：浏览器 Web Speech API（speechSynthesis，零成本离线，兼容 Chrome/Edge）播报文案 = 前端从 SSE 事件结构化生成的播报脚本
- 高保真通道：Hermes 原生 voice provider（config.yaml voice: edge/eleven_multilingual_v2 已配置）——经 `hermes tts` CLI 封装为后端 `POST /v1/ai/tts`（文本 → 音频流），用于重要预警（run.failed/审批请求）的定制播报
- 播报策略矩阵：事件类型 × 优先级（静默/Toast/Toast+语音），用户可在设置中开关

**集成步骤**：
1. 前端 `useSpeech` composable：speechSynthesis 封装（语速/音量/中断）+ 队列（事件密集时合并播报）
2. backend TTS 接口：调 Hermes CLI `hermes tts "文本" --out mp3` → 返回音频流（缓存 10 分钟）
3. 播报触发点：委托完成回写（B2 assistant 消息到达）、HERMES_APPROVAL 待审批、run.failed、SLA 超期预警（M4）

**校验标准**：
- Chrome/Edge 播报成功且可中断；TTS 接口 P95 ≤3s（缓存命中 ≤100ms）
- 事件风暴（10 事件/10s）合并为 ≤3 条播报
- 用户关闭开关后 0 播报

**前端入口**：全局右上角语音开关（持久化 localStorage）；AI 助手设置抽屉「语音播报策略」配置区

### E3. 耳朵模块（语音输入/STT）

**调用逻辑**：
- 主通道：浏览器 MediaRecorder 录音 → backend `POST /v1/ai/stt` → 网关 ASR 通道（whisper/voxtral 系，sys_ai_provider_config 增加 `asr_model` 通道）→ 文本回填对话输入框（用户可编辑后发送）
- 快捷指令模式：录音 ≤2s 且命中指令前缀（"查询/委托/创建…"）→ 直接执行（语音指令识别与执行）
- 实时转写：优先浏览器 Web Speech API（webkitSpeechRecognition，Chrome 支持边说边出字）；不支持的环境降级录音后整体转写

**集成步骤**：
1. backend STT 接口（multipart audio → 网关 asr 通道 → 文本），音频 ≤60s/≤5MB 限制
2. 前端 AiWorkspace 输入框增加麦克风按钮：按住说话（MediaRecorder）→ 波形动画 → 松开转写 → 回填输入框；「连续对话模式」开关（转写自动发送 + TTS 回答，全语音闭环）
3. 指令识别：转写文本先过本地正则（触发词/动词前缀），命中且无歧义（单工具单参数）→ 直接执行确认卡（写操作仍走确认，红线不破）

**校验标准**：
- 安静环境普通话转写准确率 ≥90%（20 条测试指令）；转写 P95 ≤2s（10s 音频）
- 全语音闭环：语音指令 → 语音回答端到端 ≤15s（简单查询）
- 写操作语音指令 100% 出现确认卡（不直接执行）

**前端入口**：对话输入框麦克风按钮 + 连续对话模式开关；移动端（响应式）同入口

---

## 四、功能校验标准总表（V2.0 验收基线）

| 类别 | 项 | 标准 |
| --- | --- | --- |
| 单测回归 | 全量 mvn test | ≥193 例全绿（新增模块各自带测） |
| 黄金指令 | 30 条（查询/办理/审核/检索/文档/专业意见） | 路由正确率 ≥90% |
| HITL 红线 | 写操作/出网/文件移动/批注 | 100% 经审批建议执行 |
| 委托链路 | 指令→卡片→事件→终态→面板 | 卡片 ≤2s，终态 ≤10min，面板数据一致 |
| 语音闭环 | STT→执行→TTS | 简单查询端到端 ≤15s |
| 视觉 | 图片识别/读屏/泳道图 | 要素正确 ≥80%，权限无越界 |
| 记忆 | 跨会话召回 | 5/5 黄金用例 |
| 自迭代 | 反馈→SOUL 变更→回归 | 闭环可回溯，回归不降级 |
| 治理 | 模型调用 | ≤35/min，sys_ai_call_log 100% 留痕 |
| 兼容 | Chrome/Edge 最新两个大版本 | 核心功能全通过 |

---

## 五、迭代切片规划（V2.0，延续循环递交模式）

| 切片 | 范围 | 交付节点 |
| --- | --- | --- |
| D1 | **铁律一/二落地（MCP 工具 REST 化 + token 全链路透传 + 越权回归集）+ 铁律五审计表** + M1 原生配置模块 + M9 记忆分层 | 第 1 周 |
| D2 | E1 眼睛（图片识别/读屏/泳道图） | 第 2 周 |
| D3 | E3 耳朵（STT/语音指令）+ E2 嘴巴（TTS 播报） | 第 3 周 |
| D4 | M2 LLM 意图判别 + M5 联网检索（**铁律四脱敏 proxy 全链路**） | 第 4 周 |
| D5 | M3 文件全生命周期 + M4 流程管控（催办/SSE 预警） | 第 5 周 |
| D6 | M6 文档工作台 + M7 专业意见 | 第 6 周 |
| D7 | M8 自迭代闭环 + **铁律五审计链联查视图** + V2.0 全量验收（30 条黄金指令 + 5 条越权用例 + 压测） | 第 7 周 |

每切片完成即执行：全维度测试 → 文档化记录（本系列报告追加）→ git commit → 递交审核。

## 六、风险与依赖

| 风险 | 缓解 |
| --- | --- |
| Hermes 原生 config.yaml 写入与网关配置双源 | M1 统一「Hermes 原生配置」Tab 单入口，网关通道管理保留只读视图联动 |
| 多模态/ASR/TTS 通道依赖外部模型供给 | sys_ai_provider_config 通道化设计，支持 LM Studio 本地多模态模型兜底 |
| 语音浏览器兼容性（Firefox 无 webkitSpeechRecognition） | 主通道 MediaRecorder+ASR 通道，浏览器原生能力为增强项 |
| 小模型（gemma）JSON 遵循度差（C3 已证 K4） | M2 LLM 意图判别限定使用高质量通道（优先级路由），启发式永远兜底 |
| SOUL.md 自迭代污染 | 补丁建议人工采纳 + 版本化 git 管理 + 回归集门槛 |

---

*本文档为 V2.0 集成实施提示词，与 V1.0 规划（docs/Hermes嵌入式改造实施规划-V1.0.md）衔接；C3/C4 收尾后按 D1-D7 推进。*
