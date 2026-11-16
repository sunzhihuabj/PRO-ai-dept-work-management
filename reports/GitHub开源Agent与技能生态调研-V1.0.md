# GitHub 开源 Agent / 技能生态调研 —— 如何让部门工作管理平台更智能

| 项 | 内容 |
|---|---|
| 版本 | V1.0 |
| 日期 | 2026-11-13 |
| 调研范围 | GitHub 开源：Agent 编排框架、技能（Skill）规范与市场、工具协议（MCP / A2A）、Agent 平台、记忆与 RAG、评测与可观测 |
| 服务对象 | 本项目（Spring Boot 3.4 + MyBatis-Plus + Vue3 + ai-gateway 8090） |
| 结论性质 | 技术选型建议 + 分阶段落地清单（不含代码改动） |

> 数据口径说明：星标/版本为公开资料与第三方榜单在 2026 年不同月份的快照，同一仓库在不同来源间存在 5%~15% 差异（例如 AutoGen 59.4k~61.3k、RAGFlow 81.6k~89.7k）。本文只用于判断**量级与活跃度**，不用于精确引用。所有"建议引入"的组件均标注许可证。

---

## 一、结论摘要（先看这 5 条）

1. **不要引入 Python Agent 框架作为主链路。** LangGraph / CrewAI / AutoGen 解决的是"从零搭 Agent"，而我们的 `OpsToolRegistry`（33 工具 / 13 版块）+ `SystemOpsService`（tool/ask/chat/agent 四态判定）+ 网关治理（能力点、35/min、脱敏、审计）已经是**一套可运行的 Agent Harness**，缺的是 Harness 的"精细度"，不是框架。
2. **真正值得抄的是四件事：技能（Skill）分层、渐进披露、状态持久化、评测闭环。** 前三件能把"33 个工具全量塞进 Prompt"的模式升级为可扩展到 100+ 能力的体系；第四件是当前最大的空白——我们没有回归评测集，任何 Prompt 改动都是"盲改"。
3. **MCP 已成为事实标准（10k+ 公开 Server、97M/月 SDK 下载、41% 企业生产使用率），但它对我们最大的价值不是"接外部工具"，而是"把我们的 33 个工具标准化暴露出去"**，让 IDE、其他 Agent、未来的小程序端复用同一套工具定义——同时必须保留网关作为唯一出口。
4. **A2A 1.0 已稳定（2026-03）并与 MCP 同归 Agentic AI Foundation，但对我们是 P2。** 只有当我们需要与客户/供应商/集团系统做"Agent 对 Agent"协同时才启动；内部单系统多 Agent 用进程内编排即可。
5. **一个量化事实：同一模型只更换 Harness，SWE-bench Pro 通过率可从 23% 提升到 52%。** 这解释了为什么"换更大的模型"收益递减，而"补校验、补状态、补评测"收益陡增。我们的投入应压在**编排与校验层**。

---

## 二、生态全景：六层结构

```
┌ 平台层      Dify / n8n / Flowise / Langflow / Coze Studio / FastGPT
├ 编排层      LangGraph / CrewAI / OpenAI Agents SDK / AutoGen(维护模式) / Spring AI / Semantic Kernel
├ 技能层      Agent Skills(SKILL.md) / anthropics-skills / awesome-agent-skills / OpenAI skills
├ 协议层      MCP（Agent↔工具）  |  A2A（Agent↔Agent）
├ 记忆/RAG层  RAGFlow / mem0 / LlamaIndex / Milvus·Qdrant·pgvector
└ 评测/可观测  Langfuse / Ragas / LangSmith(商业) / Helicone
```

### 2.1 编排框架（我们最需要判断"要不要用"的一层）

| 项目 | 星标（约） | 许可 | 状态 | 架构心智 | 与本项目的关系 |
|---|---|---|---|---|---|
| **LangGraph** | 36k~43k | MIT | 活跃，1.x 起 API 稳定（2025-10 GA） | 有向状态图 + checkpoint + interrupt | **只借鉴**：状态图/检查点/人工中断三个机制，用 Java 自研轻量版 |
| **CrewAI** | 54k~59k | MIT | 活跃，独立于 LangChain，支持 MCP/A2A | 角色+任务+Flows | 不引入：其"角色扮演"对我们 13 个业务版块无增量 |
| **OpenAI Agents SDK** | ~30k | MIT | 活跃 | Agent + Handoff + Guardrails + Tracing | 借鉴 Guardrails/Tracing 概念 |
| **AutoGen** | 59k~61k | MIT（文档 CC-BY-4.0） | **维护模式**，微软转向 Microsoft Agent Framework（MAF 1.0，2026-04） | 对话式 GroupChat | **不选型**，新项目不应从 AutoGen 起步 |
| **Spring AI** | — | Apache-2.0 | 活跃：1.0.x / 1.1.x 稳定线，2.0.0 GA（2026-06） | ChatClient + ToolCallback + Advisor | **首选 Java 侧组件**；版本必须锁 1.1.x（原因见 4.2） |
| **Semantic Kernel** | — | MIT | 活跃 | Kernel + Plugin + Planner | 可选替代，与我们 Spring 栈契合度低于 Spring AI |

**判断**：编排框架对我们不是"替换"，而是"补齐三个机制"——① 多步状态持久化（长流程不丢）；② 人工中断/恢复（审批类）；③ 工具调用前后拦截（Guardrails）。

### 2.2 技能层（本次调研最有价值的发现）

Agent Skills 已从 Anthropic 私有格式变成**开放标准**（2025-10 提出，2025-12 开放），核心就三条：

```
my-skill/
├── SKILL.md      # YAML frontmatter(name/description/triggers) + Markdown 正文
├── scripts/      # 可选脚本
└── references/   # 可选参考资料
```

**关键机制：渐进披露（progressive disclosure）。** 会话开始时只加载每个技能的 name + description（约 100 tokens/个），命中后才加载完整正文（<5000 tokens），`scripts/`、`references/` 再按需读。这就是"一个 Agent 挂几百个技能而不炸上下文"的原因。

生态现状（2026）：`anthropics/skills`（官方规范与 docx/pdf/pptx/xlsx 基线技能）、`VoltAgent/awesome-agent-skills`（跨 Agent 聚合）、`ComposioHQ/awesome-claude-skills`（1000+ 技能索引，78.6k 星，但**无许可证文件 → 默认保留所有权利，不可直接复用代码**）、`openai/skills`（Codex CLI 官方目录，分 .system/.curated/.experimental 三级）。

**对照我们的现状**：`OpsToolRegistry.SYSTEM_KNOWLEDGE` 是一整块硬编码业务知识（约 15 行×13 版块），`catalog()` 把 33 个工具**全量**注入 Prompt。这在 33 个工具时勉强可行，到 60~100 个必然退化（选择准确率下降、token 成本线性上升、无关工具干扰）。→ **P0 第一优先级：把"一块大知识 + 全量目录"改成"按需加载的版块技能卡"。**

### 2.3 协议层

**MCP（Model Context Protocol）** —— Agent ↔ 工具/数据
- 2024-11 由 Anthropic 发布，现由 Agentic AI Foundation（AAIF，Linux Foundation 下）托管
- 规模：10,000+ 公开 Server、97M/月 SDK 下载、GitHub `mcp-server` 标签仓库 15,926 个、企业生产使用率 41%（2026-05）
- 原语：Tools / Resources / Prompts / Sampling；传输：stdio、Streamable-HTTP（推荐）、SSE（已废弃）
- Java 侧：Spring 是官方 MCP Java SDK 的主要贡献者；`spring-ai-starter-mcp-server-webmvc` 三行依赖即可把 Spring Bean 暴露为 MCP Server
- **风险**：96% 的相关会议议题集中在安全（过度授权、数据外泄、Prompt 注入）→ 我们若要暴露，必须白名单 + 只读优先 + 走网关审计

**A2A（Agent2Agent）** —— Agent ↔ Agent
- 2025-04 Google 发布 → 2025-06 捐赠 Linux Foundation → 2026-03 v1.0 稳定 → 2026-08 并入 AAIF
- 规模：150+ 组织、22k+ 星、5 种语言 SDK、Azure AI Foundry / AWS Bedrock AgentCore / Vertex AI 原生集成
- 核心概念：Agent Card（机器可读的能力声明 + 签名身份）、任务委派、跨组织边界
- **现实差距**：公告多、生产用例少。开发者普遍用 MCP 近似实现 Agent 间协调 → **我们定 P2**

### 2.4 平台层（不引为核心运行时，只作为能力参照）

| 平台 | 星标（约） | 许可 | 定位 | 对我们的用法 |
|---|---|---|---|---|
| **Dify** | 142k~152k | 修改版 Apache-2.0（禁止多租户 SaaS 转售、禁止去 Logo） | LLM 应用 + RAG + Workflow + Agent | **参照其 Workflow/RAG 交互设计**，不作运行时 |
| **n8n** | 188k~201k | Sustainable Use License（**非 OSI**，禁止托管转售/白标） | 通用自动化 + AI 节点 | 仅外围原型（如临时数据同步） |
| **Flowise / Langflow** | 55k / 152k | Apache-2.0 / MIT | 拖拽搭链 | 不引入 |
| **Coze Studio** | ~18k~21k | Apache-2.0（许可最干净） | Bot 搭建 | 开源版功能残缺、更新慢，不引入 |
| **FastGPT** | ~29k | 开源（附加条款） | RAG 问答 | 参照 |
| **RAGFlow** | 82k~89.7k | Apache-2.0 | 深度文档理解 RAG 引擎 | **P1 候选**（见 4.4） |

### 2.5 记忆与 RAG

| 项目 | 星标（约） | 许可 | 价值点 | 对我们的用法 |
|---|---|---|---|---|
| **mem0** | 60k~68k | Apache-2.0 | add/search 两调用；user/agent/run 三级隔离；v3 单遍抽取 + 时序检索；LoCoMo 92.5 | **借鉴数据模型**（不引 Python 服务）：建 `ai_user_memory` 表存"事实型记忆"（偏好、常用项目、口径） |
| **RAGFlow** | 82k~89.7k | Apache-2.0 | DeepDoc 版式解析、模板化分块、chunk 可视化、混合检索 + rerank、可溯源引用 | **P1 评估**：我们已有 `KbService` 混合检索，缺"分块可视化 + 版式解析"，而技改项目以 PDF/扫描件/图纸为主，正好命中其强项 |
| **Milvus / Qdrant / pgvector** | 45k / 33k / 22k | Apache-2.0 | 向量库 | pgvector 最省事（复用现有 MySQL 之外再加 PG 不划算）→ 现阶段维持现有方案 |

### 2.6 评测与可观测（我们最缺的一层）

| 项目 | 星标（约） | 许可 | 说明 |
|---|---|---|---|
| **Langfuse** | ~31k | MIT（核心） | 自托管 trace + 评测 + Prompt 管理，最贴合"内网私有化" |
| **Ragas** | — | Apache-2.0 | RAG 专项指标（faithfulness / context precision / recall） |
| **LangSmith** | 商业 | — | 能力最强但闭源，内网受限 |
| **Helicone** | ~6k | Apache-2.0 | 轻量代理式可观测 |

我们已有 `ai_call_log`（网关审计落库）——这已经是 trace 的雏形，缺的是**业务语义层字段**（选了哪个工具、参数是什么、是否被用户取消、是否降级）和**离线评测集**。

---

## 三、与本系统现状的映射

### 3.1 已具备（不用重造）

| 能力 | 我们的实现 | 对应开源概念 |
|---|---|---|
| 工具注册与声明式参数 | `OpsToolRegistry`（33 工具 / 13 版块 / OpsParam 枚举校验） | Tool Schema（MCP Tools） |
| 意图判定 + 参数抽取 | `SystemOpsService.decide()`（JSON 契约 + 枚举约束） | Function Calling / Structured Output |
| 写入两段确认 | `OpsPendingStore`（TTL 5 分钟） | Human-in-the-loop / interrupt |
| 防幻觉兜底 | `WRITE_INTENT` 正则强制重判定 | Guardrails（输入侧） |
| 权限与红线 | `sys_capability`（AGT-01~26）+ `is_never` 四类红线 | Capability / Policy |
| 统一模型出口 | ai-gateway（能力点→限流→配额→脱敏→降级链→契约校验→审计） | AI Gateway / LLM Proxy |
| 自主分级 | `AutonomyService`（0 禁用 / 1 建议 / 2 自动 / 3 批量自动） | Autonomy Level |
| 知识库检索 | `KbService` + `kb.search` 工具（混合检索 + 溯源） | RAG |

**结论：我们的"骨架"比多数开源 Demo 更完整，尤其在治理侧（限流、脱敏、审计、红线）远超通用框架。差距全在"精细化"。**

### 3.2 差距矩阵

| 维度 | 开源标杆做法 | 我们现状 | 差距等级 |
|---|---|---|---|
| 工具/技能加载 | 渐进披露，name+desc 常驻，正文按需 | 全量注入 `SYSTEM_KNOWLEDGE` + `catalog()` | **高** |
| 长流程状态 | LangGraph checkpoint + resume | 仅单轮 pending 确认，无跨会话任务状态机 | **高** |
| 评测 | golden set + 自动回归 + 指标看板 | 无 | **高** |
| 记忆 | mem0 事实抽取 + 时序 + 隔离 | 只取最近 6 条消息，无摘要、无长期记忆 | **中高** |
| 参数语义校验 | 模糊匹配 + 候选确认 | 仅类型/必填/枚举 | **中** |
| 工具暴露标准 | MCP Server（Spring AI Starter） | 私有 JSON 目录，仅内部可用 | **中** |
| 可观测 | trace 树 + 每步耗时/token/工具 | `ai_call_log` 仅记录网关层 | **中** |
| 多 Agent 协作 | Subagent 编排（上下文隔离） | MasterAgent + TaskExecutor（较粗） | **中** |
| RAG 质量 | 版式解析 + rerank + chunk 可视化 | 常规混合检索 | **中** |
| Agent 互联 | A2A Agent Card | 无 | **低（暂不需要）** |

---

## 四、分阶段落地建议

### P0 — 低成本高收益（不引新依赖，纯后端改造）

#### P0-1 技能化改造：把"一块知识 + 全量目录"改为"技能卡 + 渐进披露"
- **做法**
  1. 把 `SYSTEM_KNOWLEDGE` 按 13 个版块拆为 13 张 `BizSkill`（字段：`code`、`name`、`module`、`triggers`（关键词/同义词）、`knowledge`（正文，即该版块的业务口径）、`tools`（工具名列表））。
  2. 首轮 Prompt 只注入：**全局规则（约 300 字）+ 每张技能卡的 name/description/triggers（约 30 字/张）+ 工具名清单（只名称+一句话，不含参数）**。
  3. 命中版块（关键词或 LLM 首选）后，第二轮再注入该版块完整知识 + 该版块工具的参数详情（相当于 skill 正文加载）。
  4. 拆两个持久化：技能卡放 DB（`sys_ai_skill`）而非硬编码，让"能力"页签可运营（见 P2-2）。
- **涉及文件**：`OpsToolRegistry.java`（拆分常量、新增 `skillCatalog()` / `skillDetail(code)`）、`SystemOpsService.java`（decide 两段式）、新增 `aiworkspace/skill/BizSkill*.java`、`sql/*.sql`
- **验收**：同一批 100 条测试问句，工具选择准确率不下降；首轮 Prompt 字符数下降 ≥50%
- **风险**：两段式增加 1 次模型调用 → 用"规则优先命中 + LLM 兜底"降低触发率；受 35/min 限流约束，需在网关侧按会话维度打标

#### P0-2 建立 golden set 回归评测（最高性价比）
- **做法**
  1. 造 100~150 条评测集：`用户话术 → 期望 action / tool / 关键参数`，覆盖 13 版块 ×（查询 / 写入 / 闲聊 / 拒答 / 多轮补参）。
  2. 用网关 **MOCK 通道 + 固定 seed** 跑离线回归，输出四个指标：**工具选择准确率、参数抽取 F1、一次成功率（无需补参）、幻觉率（声称已执行但未调用工具）**。
  3. 做成 `mvn test` 下可跑的用例（不依赖真实模型），Prompt 改动必须过基线。
- **涉及文件**：`backend/src/test/java/.../ops/`（新增评测用例 + 用例集 JSON）、复用 `reports/data-prep/` 现有素材
- **验收**：形成基线报告（当前值 → 改造后值），并纳入每次 Prompt 变更的准入
- **风险**：MOCK 通道无法反映真实模型行为 → 分两层：MOCK 跑"契约/参数"层，真实通道每周跑一次抽样（≤35 次/分钟）

#### P0-3 会话记忆压缩 + 事实型长期记忆
- **做法**
  1. 现状：`CONTEXT_MESSAGES = 6` 硬截断，超长会话会丢关键约束（如"这个任务是给三一重能的"）。
  2. 加**滚动摘要**：会话超过 N 轮后，把前段压缩为 ≤300 字摘要存 `ai_conversation.summary`，后续作为系统消息带入。
  3. 加事实型记忆表 `ai_user_memory(user_id, kind, content, confidence, updated_at)`，只存**稳定事实**（常用项目、负责版块、汇报对象、偏好格式），由"确认后写入"触发，避免污染。
  4. 检索时按 `user_id` 严格隔离（mem0 的教训：不隔离会串数据）。
- **涉及文件**：`SystemOpsService.java`（历史组装）、`AiChatService.java`、新增 `AiUserMemory` 实体/Mapper、`sql/` 迁移脚本
- **验收**：10 轮以上长会话，第 12 轮的指代消解正确率提升；记忆条目可人工查看/删除
- **风险**：摘要失真 → 保留原文，摘要只作辅助；记忆只增不删会膨胀 → 加"过期/失效"字段

#### P0-4 参数语义校验（补"看起来对但其实错"的洞）
- **做法**：现有校验只查必填/枚举/类型。补三类：
  - **实体存在性**：`projectName` / `assignee` 不存在时，走模糊匹配返回 Top3 候选并追问（"你是指「三一重能-XX技改」吗？"），而不是直接报错
  - **业务前置条件**：如"推进工单到 STARTED"需先校验齐套率≥95%、交底签字（这些规则已在 `SYSTEM_KNOWLEDGE` 里，但执行前未硬校验）
  - **歧义检测**：同名项目/任务命中多条时强制追问，禁止默认取第一条
- **涉及文件**：`SystemOpsService.validate()`、各 `OpsTool` executor 的前置断言
- **验收**：构造 20 条"错名/同名/缺前置"用例，全部走追问而非误执行
- **风险**：追问过多伤体验 → 只在"即将写入"时严格，只读查询可降级为"按最可能结果返回 + 标注歧义"

---

### P1 — 中期（1~2 个月，引入少量依赖）

#### P1-1 把工具暴露为 MCP Server（只读优先）
- **价值**：33 个工具一次定义、多处复用（IDE 插件、其他 Agent、未来的小程序端），且**不改动现有 HTTP 链路**。
- **做法**
  1. 在 backend 引入 `spring-ai-starter-mcp-server-webmvc`（**必须锁 Spring AI 1.1.x**，见 4.2 版本约束），`spring.ai.mcp.server.protocol=STREAMABLE`。
  2. 用一个 `OpsMcpFacade` 把 `OpsTool` 适配为 MCP Tool（名称、描述、JSON Schema 直接由 `OpsParam` 生成——我们的声明式设计正好复用）。
  3. **只读工具先开**（查询类），写入类暂不暴露；MCP 端点单独端口 + 内网白名单 + 复用 `LoginUser` 鉴权。
  4. 所有 MCP 调用仍必须经 `ai-gateway` 才能触达模型（禁止 MCP 直连模型），审计字段补 `source=mcp`。
- **涉及文件**：`backend/pom.xml`、新增 `aiworkspace/mcp/OpsMcpFacade.java`、`application.yml`
- **验收**：Claude Desktop / Cursor 能列出并成功调用 `task.myTodo`、`task.list`（只读）
- **风险**：绕过网关的审计与限流（**必须显式禁止**）；过度授权（MCP 安全议题的第一风险）；版本升级破坏性变更（2.x 有 MCP 注解包重命名、Jackson 2→3 迁移）

#### P1-2 长流程状态机（自研轻量版，不引 LangGraph）
- **场景**：ECN 变更（DRAFT→PENDING→APPROVED→IMPLEMENTED→CLOSED）、投标（标讯→备标→报价→定标）、验收（首发→30%→85%→终验）——都是有状态的、可能中断数周的流程。
- **做法**
  1. 建 `ai_agent_run`（run_id、skill_code、state、context_json、checkpoint_at、status）+ `ai_agent_step`（步骤、输入、输出、耗时、是否人工介入）。
  2. 把现有 `OpsPendingStore`（内存 TTL 5 分钟）升级为**持久化 interrupt/resume**：写入类操作落库，可跨会话、跨重启恢复，确认超时从 5 分钟延长为业务可配（如审批类 24 小时）。
  3. 每个节点执行前后写 checkpoint；失败可从最后 checkpoint 重跑（对齐 LangGraph durable 能力）。
- **涉及文件**：`OpsPendingStore.java`（改造为持久化实现，保留内存缓存）、新增表与实体、`TaskExecutor` / `MasterAgentService`
- **验收**：模拟进程重启后，未完成的多步任务可恢复并继续
- **风险**：状态机过度设计 → 只对 ≥3 步且含人工节点的流程建状态机，单步操作维持现状

#### P1-3 RAG 质量升级（先评估 RAGFlow，再决定自研 or 集成）
- **决策点**：我们的资料以**招标文件 PDF、扫描件、图纸、方案**为主，普通按字符切块会破坏表格与版式。
- **两条路**
  - A. 引入 RAGFlow 作为独立检索服务（Apache-2.0，Docker Compose，但**最低 16GB RAM、需调 `vm.max_map_count`、仅 x86 镜像**），`KbService` 改为调用其 HTTP API；
  - B. 自研增量：只补"版式解析（MinerU/Docling 思路）+ rerank + chunk 可视化"，保留现有 `KbService`。
- **建议**：先做 A 的 POC（3 天内用 20 份真实招标文件对比检索命中率），命中率提升 >15% 才引入，否则走 B。
- **风险**：基础设施翻倍（ES/MinIO/Redis/MySQL 一套）+ 运维负担；离线内网部署可行但资源吃紧

#### P1-4 可观测与评测看板
- **做法**
  1. 给 `ai_call_log` 补业务字段：`tool_name`、`params_digest`、`confirm_result`（确认/取消/超时）、`degrade_chain_index`、`cost_tokens`。
  2. 前端"任务"页签增加"AI 运行轨迹"视图（每次调用 → 判定 → 工具 → 结果 → 人工介入）。
  3. 可选：自托管 Langfuse（MIT）做 trace UI，或先自建轻量页面（推荐后者，避免多一套服务）。
- **验收**：能回答"上周 AI 最常失败的操作是哪个、失败原因是参数错还是权限拒绝"

---

### P2 — 长期（3~6 个月，视业务需要）

| 项 | 内容 | 触发条件 |
|---|---|---|
| **P2-1 A2A 互联** | 对外发布 Agent Card，与客户/供应商/集团系统做任务委派；复用 A2A 1.0 的签名 Agent Card 做身份 | 出现明确的跨组织 Agent 协同需求 |
| **P2-2 技能市场（前端"能力"页签升级）** | 由 `sys_ai_skill` 驱动：卡片展示名称/描述/触发词/所属版块/是否启用/自主等级；支持导入导出（SKILL.md 兼容格式） | P0-1 完成后即可做，成本低 |
| **P2-3 多 Agent 协作** | 主 Agent + 专家 Agent（投标专家/成本专家/售后专家），上下文隔离、工具集隔离；参考 Spring AI 的 Subagent Orchestration 与 Claude Code 的 subagent 模式，用 Java 实现 | 单 Agent 上下文开始互相污染（表现为跨版块串答） |
| **P2-4 编码/运维 Agent** | OpenHands（MIT，76k~100k 星，Docker 沙箱）用于内部工单修复、SQL 巡检、日志分析 | 内部研发效率成为瓶颈时；**绝不接入生产写权限** |

---

## 五、明确"不做"清单

| 不做 | 理由 |
|---|---|
| 引入 LangGraph / CrewAI / AutoGen 作为主运行链路 | 双语言运维成本；与 ai-gateway 的治理模型（能力点/限流/审计）无法对齐；我们的 Java Harness 已成型 |
| 用 Dify / n8n 替代现有后端流程引擎 | 会架空网关的能力点校验、35/min 限流、脱敏与审计——这是本系统最核心的合规资产 |
| 选型 AutoGen | 官方已进入维护模式（2026-04 起由 Microsoft Agent Framework 承接），新项目不应起步于此 |
| 上 Spring AI 2.0 | 2.1.0-M1 已明确把基线抬到 Spring Boot 4.2；我们锁 Boot 3.4，应使用 **Spring AI 1.0.x / 1.1.x**，2.x 会连带 Jackson 3 迁移等破坏性变更 |
| 直接复用 awesome-claude-skills 里的代码 | 该仓库**无 LICENSE 文件**（默认保留所有权利），只能当索引参考，不能复制代码 |
| 把 n8n 作为对外服务的一部分 | Sustainable Use License 非 OSI 认证，禁止托管转售/付费白标 |
| 让 MCP Server 直连模型 | 必须保留"唯一出口"，否则审计与限流失效（MCP 生态最大安全风险正是过度授权与数据外泄） |

---

## 六、可直接复用的开源组件（含许可证）

| 组件 | 用途 | 许可证 | 引入方式 | 备注 |
|---|---|---|---|---|
| `spring-ai-starter-mcp-server-webmvc` | 把 33 工具暴露为 MCP Server | Apache-2.0 | Maven 依赖（1.1.x） | 只读优先；Streamable-HTTP |
| `spring-ai-mcp` (MCP Java SDK) | MCP 客户端（未来接外部工具） | Apache-2.0 | Maven 依赖 | Spring 官方维护 |
| Spring AI `ChatClient` + `ToolCallback` | 替代手写 Prompt 拼接（可选） | Apache-2.0 | Maven 依赖 | 我们现有手写链路可保留，逐步替换 |
| Langfuse（自托管） | trace + 评测 + Prompt 版本 | MIT（核心） | Docker | 可选，优先自建轻量页面 |
| Ragas | RAG 指标计算 | Apache-2.0 | Python 离线 | 只用于离线评测，不进生产 |
| RAGFlow | 版式解析 + 深度 RAG | Apache-2.0 | Docker（≥16GB） | 先 POC 再定 |
| SKILL.md 规范（anthropics/skills） | 技能元数据格式 | 官方规范 + 仓库许可 | 仅格式借鉴 | 我们的 `sys_ai_skill` 字段可兼容 |
| metaso/mem0 数据模型 | 事实型记忆表设计 | Apache-2.0 | 仅设计借鉴 | 不引 Python 服务 |

---

## 七、如何衡量"更智能"（建议纳入验收）

| 指标 | 定义 | 现状基线 | 目标 |
|---|---|---|---|
| 工具选择准确率 | 期望工具 == 实际工具 | 待测 | ≥92% |
| 参数抽取 F1 | 期望参数集 vs 实际 | 待测 | ≥90% |
| 一次成功率 | 无需追问即完成 | 待测 | ≥75% |
| 幻觉率 | 声称已执行但未调用工具 | 待测（已有正则兜底） | ≤1% |
| 平均交互轮次 | 完成一次任务所需轮次 | 待测 | ≤2.0 |
| 人工接管率 | 用户取消/超时/转人工占比 | 待测 | ≤10% |
| 首轮 Prompt 体积 | 注入模型的字符数 | 待测（全量目录） | 下降 ≥50% |
| RAG 命中率 | Top5 含正确片段的比例 | 待测 | ≥85% |

> 建议在 P0-2 完成后补齐"现状基线"一列，作为后续所有改造的对照。

---

## 八、落地顺序（一页纸）

```
第 1 周    P0-2 评测集（先有尺子）→ 得到基线
第 2 周    P0-1 技能卡拆分 + 渐进披露 → 用评测集验证
第 3 周    P0-3 会话摘要/长期记忆 + P0-4 参数语义校验
第 4 周    P2-2 能力页签升级为技能市场（前端小改，可并行）
第 5-8 周  P1-1 MCP 只读暴露 → P1-2 长流程状态机
第 9-12 周 P1-3 RAGFlow POC（二选一）→ P1-4 可观测看板
长期       P2-1 A2A / P2-3 多 Agent / P2-4 编码 Agent
```

**一句话总结**：我们不需要"再装一个 Agent 框架"，需要的是把已有的 Harness 从"能跑"升级到"可度量、可扩展、可恢复"——技能分层解决扩展，状态持久化解决流程，评测集解决信任。

---

## 九、参考资料

- [Multi-Agent Orchestration 2026: LangGraph, CrewAI, and AutoGen Compared](https://dev.to/saaro_net/multi-agent-orchestration-2026-langgraph-crewai-and-autogen-compared-34bd)
- [LangGraph vs CrewAI vs OpenAI SDK: The 2026 Verdict](https://niteagent.com/blog/langgraph-vs-crewai-vs-openai-sdk-2026/)
- [多智能体编排完全指南：best-of-Agent-Harnesses](https://blog.gitcode.com/b0ca2aaacd630c7a89991a26126dc2f3.html)
- [CrewAI vs AutoGen vs LangGraph（2026）](https://similarlabs.com/es/blog/crewai-vs-autogen-vs-langgraph)
- [A Two-Dimensional Study of the Model Context Protocol (arXiv 2609.14721)](https://arxiv.org/html/2609.14721)
- [MCP Hits 10K Servers, 97M Monthly SDK Downloads by May 2026](https://dev.to/gentic_news/mcp-hits-10k-servers-97m-monthly-sdk-downloads-by-may-2026-1jbk)
- [GitHub 8.6万Star，OpenAI、Google、微软全部接入——MCP 凭什么成为 AI 的 USB-C](https://cloud.tencent.com/developer/article/2693820)
- [8 Must-Collect Agent Skills Repositories for Claude and AI Agents](https://www.besthub.dev/articles/8-must-collect-agent-skills-repositories-for-claude-and-ai-agents-2cb2d211512b)
- [Awesome Claude Skills 索引与许可证分析](https://hysenlabs.com/projects/composiohq-awesome-claude-skills)
- [Spring AI 2.0.0 GA / Tool Calling / Agentic Patterns](https://spring.io/authors/tzolov/)
- [Connect Your AI to Everything: Spring AI's MCP Boot Starters](https://spring.io/blog/2025/09/16/spring-ai-mcp-intro-blog/)
- [MCP Server Boot Starter 官方文档](https://docs.spring.io/spring-ai/reference/1.0/api/mcp/mcp-server-boot-starter-docs.html)
- [A2A Protocol Surpasses 150 Organizations（Linux Foundation）](https://www.linuxfoundation.org/press/a2a-protocol-surpasses-150-organizations-lands-in-major-cloud-platforms-and-sees-enterprise-production-use-in-first-year)
- [Agent2Agent Protocol 词条（含并入 AAIF）](https://aiwiki.ai/wiki/a2a_protocol)
- [n8n / Dify / Coze 开源部署对比（2026-10 核实）](https://jishuzhan.net/article/2112385715330113537)
- [Dify vs Coze vs RAGFlow vs n8n：低代码 AI 平台怎么选（2026）](https://segmentfault.com/a/1190000047774699)
- [RAGFlow 深度文档理解 RAG 引擎实测](https://juejin.cn/post/7646084756715962383)
- [Mem0: The Open-Source Memory Layer for AI Agents](https://www.diego-rodriguez.work/blog/mem0-memory-layer-agents-2026-guide)
- [OpenHands Review 2026](https://dev.to/jovan_chan_9500711396d4e6/openhands-review-2026-the-76k-star-coding-agent-5pa)
