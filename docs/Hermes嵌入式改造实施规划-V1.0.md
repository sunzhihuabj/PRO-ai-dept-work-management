# Hermes Agent 嵌入式改造实施规划（V1.0）

| 项目 | 内容 |
| --- | --- |
| 编制日期 | 2026-11-17 |
| 目标 | 在本项目「AI 助手」（/ai）内直接使用 Hermes Agent 能力：自然语言驱动查询/办理/审核全业务流程，支持委托「AI Agent 员工」执行 |
| 前置 | 阶段一已完成（MCP Server / 网关门面 / 决策建议审批 / 事件扩展），本文档为其嵌入式转向的修正规划 |

---

## 〇、意图对齐：此前工作与新方向的关系

**您的意图**：员工在本系统 AI 助手对话框输入自然语言 → Hermes Agent 作为执行内核 → 驱动本项目全部业务流程（查询/办理/审核）→ 复杂任务委托 AI Agent 员工 → 降低工作量提升效率。

**此前已完成工作的定位**（全部复用，无废弃）：

| 已完成项 | 在嵌入式方案中的角色 |
| --- | --- |
| 网关 OpenAI 兼容门面（/v1/chat/completions） | Hermes 的**模型通道**——Hermes 的 LLM 调用统一走网关（35 次/分限流/降级链/审计全局生效） |
| MCP Server（backend /api/mcp，8 业务工具） | Hermes 的**业务执行通道**——Hermes 通过 MCP 调用本系统工具；阶段二需扩容至 33 Ops 工具 + 27 能力点全量 |
| 决策建议 + HITL 审批（agent_decision_advice + /ai/advice） | 委托机制的**审批界面**——Agent 员工的高危动作走此通道人工确认 |
| 事件总线扩展 / 预算护栏 / 审计 | 委托任务的状态回传与治理底座 |
| AiChatService 三级路由 / 33 Ops 工具 / 渐进自主 L1-L3 / 精细权限 | 嵌入的**宿主**——Hermes 接管其中的"推理与多轮执行"层 |

**需要转向的点**：此前 Hermes 是"独立 CLI 外挂经 MCP 外部调用"；新方向是 **Hermes 服务化（无头运行）被本系统后端调用**，用户交互完全留在本系统 AI 助手内。

**嵌入模式决策（推荐模式 A）**：
- **模式 A（推荐）：Hermes 服务化嵌入** —— Hermes API Server（127.0.0.1:8642，`/v1/runs`+`/v1/chat/completions`，原生支持审批回调）作为常驻无头引擎；AiChatService 第四级路由把复杂任务投递为 Hermes run；Hermes 经 MCP 驱动业务工具；结果/过程回写 ai_message。交互、权限、审计全在本系统。
- 模式 B（不采纳为主路径）：Hermes 反向接管会话（IM 插件/web-ui iframe）——会话与权限脱离本系统，审计割裂。

---

## 一、全流程实施步骤（三阶段任务节点）

### 阶段 A：系统架构适配层（1-2 周）

| # | 任务节点 | 说明 | 交付物 |
| --- | --- | --- | --- |
| A1 | Hermes API Server 服务化 | ✅ 2026-11-17 完成：API Server 常驻 127.0.0.1:8642（计划任务 DepSvc-svc-hermes → deploy/svc-hermes.cmd → `hermes gateway run --external-supervisor`）；health/Bearer 401/models/completions 全验证 | 常驻进程 + 健康检查 |
| A2 | Hermes 服务配置固化 | ✅ 2026-11-17 完成：真实配置路径 AppData\Local\hermes\config.yaml，providers.dept-gateway 绑定网关门面（审计 caller_id=openai-compat）；dept-mgmt MCP 8 工具注册（venv 补装 mcp SDK）；实测命名规则 `mcp__dept_mgmt__<tool>`；格式定稿入接口说明文档第 4 节 | 配置定稿 + E2E 工具调用 |
| A3 | MCP 工具全量化（一期） | ✅ 2026-11-17 完成：McpOpsBridge 自动注册器（遍历 OpsToolRegistry 只读工具零手写，OpsParam→JSON Schema 自动转换，去重跳过 3 个语义重复项），MCP 工具 8→27；单测 5 例 + 全量回归绿；抽样实调（task.detail/contract.list 中文枚举/kb.search）+ Hermes 全量发现 27 工具 | MCP 工具数 8→27 |
| A4 | 身份与权限映射 | ✅ 2026-11-17 完成：X-Acting-User-Id 头 → McpCallContext(ThreadLocal) → McpOpsBridge 逐调用查库校验（无效/禁用 401 拒绝防冒用），权限校验复用 SystemOpsService.checkPermission（minRole/permCode 双声明）；无头回落系统委托身份；Hermes headers 已带 "1"（引号坑已记录）；集成三场景实测通过 | 身份透传协议 + 权限拦截 |
| A5 | 决策建议审批动作扩展 | ✅ 2026-11-17 完成：ApprovalActionRegistry 白名单（11 个 Ops 写工具动作化，ecn.approve/tender.collect 明确排除；OPS_ 前缀双向映射防驼峰丢失）；DecisionAdviceService.executeApproval 增 OPS_* 分支（HMAC→动作一致性→委托人复核→precheck→executor→结果回写 evidence_json）；新增 requestToolApproval 公开入口（B4 MCP 写工具/B5 回调映射调用）；期间发现并修复 Registry→OpsToolRegistry 构造环（ObjectProvider 打环）；单测 13 例全过 + 全量回归 157 绿 | 审批动作注册表 |

### 阶段 B：Hermes Agent 核心能力对接（2-3 周）

| # | 任务节点 | 说明 | 交付物 |
| --- | --- | --- | --- |
| B1 | HermesService（Java 客户端） | ✅ 2026-11-17 完成：/v1/runs 提交/轮询/停止/审批回调/health 探测封装（契约经真实 8642 实测）；app.hermes.enabled 总开关 + available() 30s 探测缓存降级（不引 Resilience4j，C4 后评估）；单测 10 例 + 全量回归 167 绿；期间修复网关被误中断（^C）重启复验 | HermesService + 降级开关 |
| B2 | AiChatService 第四级路由 | ✅ 2026-11-17 完成：HermesDelegateService 插入路由链第四级（WF意图→Ops单轮→Agent能力点→Hermes委托→通用对话）；启发式委托判定（触发词/多步串联/排除纯查询与短内容，不耗 LLM 配额）；chat() 立即返回 msgType=hermes 卡片（content 内嵌 `<!-- hermes-run:runId -->` 标记），后台单线程 daemon 轮询 awaitRun 至终态回写 assistant 消息（成功带 output/失败带 error/超时 stopRun 提示）；Hermes 不可用返回 null 降级通用对话；单测 10 例 + 全量回归 177 绿；集成实测：委托卡片返回 + 53s 后终态回写（run 失败系 LM gemma Clean EOF 已知问题，客户端降级语义正确） | 路由决策表 + 委托服务 |
| B3 | 执行过程回写 | ✅ 2026-11-17 完成：消费 Hermes 原生 SSE 事件流（GET /v1/runs/{id}/events，HermesService.consumeEvents，HttpURLConnection+Bearer，流关闭/读超时静默降级）→ 白名单过滤（tool.started/completed、approval.request、message.interim、subagent.*、run.*，丢弃 message.delta 逐 token 噪声）→ hermes_run_event 新表落库（(run_id,seq) 唯一幂等，DDL 11_hermes_run_event.sql）；委托后台线程先消费事件再等终态（事件缺失不影响 B2 回写）；新增查询接口 GET /v1/ai/ws/hermes/runs/{runId}/events（ai_message 内嵌标记→会话归属校验，非本人 404）；单测 4 例（SSE 解析/坏行/停用短路/归属校验）+ 全量回归 181 绿；集成实测：事件落库（run.failed seq=4）+ 接口时间线返回 + 未知 run 404 | 过程可视化 + 事件表 |
| B4 | MCP 工具全量化（二期） | 写入类 Ops 工具暴露为 MCP（带 riskLevel/requiresApproval 声明）；能力点 AGT 系列（AGT-26 晨报/AGT-27 采集等）暴露 | MCP 全量 60 工具 |
| B5 | 审批回调映射 | Hermes `auth-required` 中断 → 映射为本系统决策建议（PENDING）→ /ai/advice 采纳 → 回调 `/v1/runs/{id}/approval` 继续执行 | HITL 双向闭环 |

### 阶段 C：AI 助手集成改造（2-3 周）

| # | 任务节点 | 说明 | 交付物 |
| --- | --- | --- | --- |
| C1 | 前端对话增强 | 助手消息流支持「Hermes 执行中」卡片（步骤时间线/取消按钮/审批入口）；ai_message 增 runId 关联 | 对话 UI 改造 |
| C2 | AI Agent 员工面板 | 委托任务列表（委托人/任务书/状态/结果/采纳率）；复用 /ai/advice 审批交互 | 委托面板页 |
| C3 | 三场景端到端联调 | 查询/办理/审核各 5 条黄金指令回归 | 联调报告 |
| C4 | 治理与压测 | 预算护栏计数接线（网关调用日志关联 agent_task）、并发 ≤5、降级演练 | 压测报告 |

---

## 二、自然语言驱动全业务流程：三类场景落地要求

### 2.1 交互逻辑定义

| 场景 | 路由 | Hermes 行为 | 交互形态 |
| --- | --- | --- | --- |
| **业务查询**（只读） | 第四级路由（或简单查询仍走 Ops 单轮） | 只挂只读 MCP 工具子集；1-2 轮内完成；结构化结果转 Markdown 表格 | 即时回答 + 数据来源标注 |
| **业务办理**（写） | 必经第四级路由 | Hermes 规划步骤 → 每个写工具调用前生成结构化 intent（动作/参数/影响面/置信度）→ 命中审批则挂起 | 计划展示 →（需确认时）审批卡片 → 执行 → 结果回执 |
| **业务审核**（审批类） | 审批类动作强制人工 | Hermes 只做**预审**（材料完整性/合规检查/风险提示，置信度+依据），不代批 | 预审意见卡片 → 部门领导在 /ai/advice 或流程审批节点决策 |

### 2.2 权限校验规则配置（三层，全部复用既有引擎）

1. **入口层**：AI 助手登录态（JWT）→ 委托发起人身份随任务书固化（不可提权）
2. **执行层**：Hermes 每个 MCP 工具调用携带 `X-Acting-User-Id` → 后端 EffectivePermissionService.checkForUser(capabilityCode) 逐调用校验（不是会话级一次校验）——Hermes 无法以任何方式绕过
3. **动作层**：写入类工具强制 `requiresApproval`（对应 Ops 写工具既有确认制）；资金/审批/删除类 is_never 永久禁止 AI 执行（sys_capability.is_never 兜底）

### 2.3 业务流程触发链路开发标准

- 触发链路：`用户指令 → AiChatService 路由 → HermesService.runs.submit(任务书) → Hermes 规划 → MCP 工具(带身份) → 后端 Service → 事件总线 → 决策建议/通知`
- 每个环节幂等：任务书带 bizKey；Hermes run 带 idempotency key（=agent_task.taskNo）
- 全链审计：网关调用日志（模型）+ sys_oper_log（业务写）+ agent_task_event（步骤）+ agent_decision_advice（审批）四账本可关联（runId/taskNo 贯穿）
- 输出规范：所有 Hermes 结论必须结构化（结论/置信度/依据引用/后续建议），复用阶段一建议 JSON schema；无依据引用的结论降级为"仅供参考"标注

---

## 三、AI Agent 员工委托机制设计

### 3.1 可委托任务范围界定

| 级别 | 范围 | 审批 |
| --- | --- | --- |
| 自动执行 | 只读查询/报表生成/数据核对/草拟/晨报/采集登记（AGT 已 Auto 级） | 免批，事后审计 |
| 建议确认 | 单条业务写入（建任务/改状态/指派/工时） | 决策建议审批（HMAC 锁定） |
| 强制人工 | 资金、合同生效、审批决定、删除、is_never 类 | Agent 仅预审，绝不代批 |

### 3.2 任务自动分配规则

1. 意图识别（现有 MasterAgentService 关键词 + LLM 契约升级）→ 命中可委托白名单 → 生成委托单（agent_task 扩展：delegator/delegatee='hermes-agent-01'/任务书 JSON/SLA）
2. 分配策略：单实例 Hermes（阶段二），按 agent_task 队列 FIFO + 优先级；并发 ≤5（TaskExecutor 既有信号量）；Hermes 与本地 AGT 执行器**混跑同队列**（谁是执行引擎对用户透明）
3. SLA：查询 5min / 办理 15min / 预审 30min；超时升级人工（看门狗）

### 3.3 执行结果校验逻辑

- 结构化出参 JSON Schema 双校验（schema + 枚举，阶段一护栏复用）
- 业务校验：写操作后触发对应只读工具**反向验证**（如建任务后 task.query 确认落库）
- 置信度路由：<0.85 → 强制人工复核；采纳/驳回/修改计入 autonomyService 反馈（采纳率数据源已存在）

### 3.4 异常场景兜底

| 异常 | 兜底 |
| --- | --- |
| Hermes 进程崩溃/不可达 | 健康探测 → 路由自动降级为现有三级路由（规则引擎+Ops 单轮），业务不中断；降级开关可随时关闭 Hermes 化 |
| 单任务 LLM 预算耗尽（35 次） | 预算护栏拦截 → 部分结果回传 → 转人工（阶段一已实现） |
| 工具执行异常 | 失败类型化（错误不进下游上下文）→ 重试预算（≤3 指数退避）→ 超限 ESCALATED |
| 审批超时 | 决策建议 response_deadline 过期 → EXPIRED → 通知委托人 |
| 死循环/死锁 | Hermes max_turns 上限 + run_budget_seconds 墙钟预算 |

---

## 四、前置工作盘点与后续任务清单

### 4.1 已完成前置（可直接复用）

1. ✅ 网关 OpenAI 兼容门面（Hermes 模型通道；真实模型 LM/gemma-4-e2b 已验证 READY）
2. ✅ MCP Server（8 业务工具 + 鉴权 + JSON-RPC 协议验证三客户端通过）
3. ✅ 决策建议 + HITL 审批闭环 E2E（生成→站内信→采纳→动作落库）
4. ✅ 事件总线扩展（3 事件）+ 预算护栏 + agent_task 队列/看门狗/ESCALATED
5. ✅ Hermes 0.22.5 本机就绪 + config.yaml 实测（模型经门面审计可证）
6. ✅ AiChatService 三级路由 + 33 Ops 工具 + 渐进自主 + 精细权限（既有宿主能力）
7. ✅ 阶段一测试（单元 13/13 + 回归 139 修复后全绿）+ 三份文档

### 4.2 待推进任务（优先级 / 节点 / 交付物）

| 优先级 | 任务 | 节点（周期） | 交付物标准 |
| --- | --- | --- | --- |
| **P0** | A1-A2 Hermes 服务化常驻 + profile 绑定网关模型（解决 429） | 第 1 周内 | 常驻进程 + `hermes -z` 经门面真模型跑通 |
| **P0** | A3-A4 MCP 只读工具全量化 + 身份透传校验 | 第 1-2 周 | MCP 工具 27+；越权调用被拒的冒烟 |
| **P0** | B1-B2 HermesService + 第四级路由（查询类先行） | 第 2-3 周 | AI 助手对话查询经 Hermes 多轮执行；降级演练通过 |
| **P1** | B3-B5 过程回写 + 写工具 MCP 化 + 审批双向闭环 | 第 3-5 周 | 办理场景 E2E（含审批挂起/恢复）；预审场景 E2E |
| **P1** | C1-C2 前端对话增强 + AI Agent 员工面板 | 第 5-6 周 | UI 可用 + 委托列表/审批面板 |
| **P2** | C3-C4 三场景黄金指令回归 + 压测治理 | 第 6-7 周 | 联调报告 + 压测报告 + [V1.4] 小版本 |
| **P2** | Dify 编排底座部署（可选增强） | 待 Windows 功能启用后 | 部署手册第 3 节执行完毕 |

### 4.3 预期目标达成度量

- 员工工作量下降：可委托白名单任务占比（目标：查询类 ≥60% 自动应答、办理类 ≥30% 自动执行+审批、审核类 100% AI 预审）
- 效率指标：AI 助手查询 P95 ≤10s；办理任务端到端 ≤15min；采纳率 ≥60%（autonomyService 数据源）
- 质量红线：越权调用 0、无依据结论 0、写操作 100% 留痕

---

## 五、需要用户配合的事项

1. **Hermes 凭据**：运行 `hermes setup` 将自有模型凭据配置到 CLI profile（当前匿名 GLM 429）——或确认由我把网关门面地址写入其 profile 默认位
2. **Dify（可选）**：手动启用 Windows 功能「虚拟机平台」后重启，我继续部署（P2，不阻塞主路径）
