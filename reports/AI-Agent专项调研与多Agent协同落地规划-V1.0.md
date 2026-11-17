# AI Agent 专项调研与多 Agent 协同落地规划（v1.0）

| 项目 | 内容 |
| --- | --- |
| 调研日期 | 2026-11-16 |
| 调研范围 | OpenClaw / Hermes Agent / Dify / Coze Studio / n8n / 豆包 / WorkBuddy 共 7 款 Agent 主体的企业嵌入可行性 + 多 Agent 协同框架设计 |
| 约束 | Java 17 / Spring Boot 3.4 / MySQL / 无 K8s / 裸机部署 / LLM 35 次/分限流 / 内网私有化 |
| 交付 | 专项调研报告 + 多 Agent 协同设计框架 + 分阶段实施路线图 + 落地执行方案 |

---

## 一、7 款 Agent 主体嵌入可行性总评

### 1.1 评估维度

| 维度 | 说明 |
| --- | --- |
| 协议与接入 | HTTP API / Webhook / MCP 支持情况、是否支持 OpenAI 兼容 |
| 部署形态 | 资源占用、数据库依赖、Windows 兼容性、裸机友好度 |
| 安全成熟度 | CVE 历史、供应链风险、隔离能力、审计完备度 |
| 业务适配 | 可承担的业务类型、事务一致性支持、与 Java 系统亲和性 |
| 生态与稳定 | 社区活跃度、迭代节奏、企业案例、许可证边界 |

### 1.2 七款 Agent 总评矩阵

| Agent | 定位 | 许可证 | 星数 | 嵌入可行性 | 核心风险 | 推荐角色 |
| --- | --- | --- | --- | --- | --- | --- |
| **Hermes Agent** | 通用开源 Agent 运行时 | MIT | 263k | **中（偏高）** | 注入类 CVE 历史、Python 独立部署 | **主 Agent 主体** |
| **Dify** | 企业级 Agent 编排平台 | Modified Apache 2.0 | 130k+ | **高** | 强制 PG+Redis+向量库 | **主编排底座** |
| **OpenClaw** | 个人助理 Agent 网关 | MIT | 394k | **中** | 漏洞高频+技能供应链投毒 | **外围轻量 Agent** |
| **WorkBuddy** | 腾讯企业 AI 智能体 | 商业订阅 | — | **中偏高** | 私有化 300 席起步、成本高 | **协同补充** |
| **豆包/Coze** | 字节企业智能体平台 | Apache 2.0 (Coze) | 21.6k | **中** | HiAgent 需商务对接、数据出域边界 | **可选协同** |
| **n8n** | 工作流自动化 | SUL（内部免费） | 100k+ | **中** | 非 LLM Agent、SUL 条款限制 | **系统集成旁路** |
| **Coze Studio** | 字节开源智能体 | Apache 2.0 | 21.6k | **低** | 组件最重、PAT 30 天短板、Windows 不友好 | **暂不推荐** |

### 1.3 关键发现

1. **Hermes Agent** 是当前最适合作为企业业务执行 Agent 主体的开源项目：MIT 许可、OpenAI 兼容 API（`/v1/chat/completions`、`/v1/runs`）、MCP client+server 双支持、18+ 模型 provider、SQLite+FTS5 记忆、Python 单进程可跑、官方指南丰富。风险可控（锁定 v0.23.x + 容器隔离 + 审批开启）

2. **Dify** 是最适合作为多 Agent 编排底座的平台：`/v1/chat-messages` 与 `/v1/workflows/run` 长期稳定 API、MCP 双向、Swagger 工具导入、Agent/Workflow/Trigger 节点齐全、社区单工作区许可合规，引入 PG 容器代价可接受

3. **OpenClaw** 架构成熟但安全问题突出（CVE-2026-25253 + ClawHavoc 24.7 万次恶意安装 + SkillProbe 94% 逃逸率），仅建议承担审批兜底的非事务型任务

4. **WorkBuddy** 反向集成（Java 系统调用其能力）规范完整（A2A + MCP + Managed Agents OpenAPI），但私有化部署 300 席起步、成本高；豆包 HiAgent 同等问题

5. **Coze Studio** 虽然 Apache 2.0 无附加限制，但组件最重（MySQL+ES+Milvus+MinIO+消息队列）、原生无定时调度、PAT 最长 30 天需轮换，与裸机 Windows 部署不匹配，建议观望

6. **n8n** 适合作为系统集成旁路（定时/Webhook 类非 LLM 自动化），MySQL 节点原生直连的特性对本项目有辅助价值

---

## 二、各 Agent 主体深度画像

### 2.1 Hermes Agent（推荐主 Agent 主体）

**身份确认**：Nous Research 的 Hermes Agent（`NousResearch/hermes-agent`），2026 年全球热度最高开源 Agent（263k star、259 个 release、约 22 小时一发），Nous 2026-10 融资 9000 万美元 [P0]

- **核心架构**：`AIAgent` run loop → prompt_builder → 18+ provider → 工具分发 → 循环；三层记忆（工作/情景/程序化）+ FTS5 跨会话检索；70+ 工具 / 28 toolsets；6 种终端后端 + 5 种浏览器后端
- **开放接口**：OpenAI 兼容 HTTP（127.0.0.1:8642，Bearer）、`/v1/runs` + 审批回调、SSE 流式、Webhook、MCP client+server、A2A、Python 嵌入库；25+ 消息平台官方插件（含飞书/钉钉/企业微信/微信/QQ）
- **安全**：8 层防御（用户授权白名单、危险命令审批 smart/manual/off、hardline 黑名单、容器隔离、MCP 凭据过滤、注入扫描、会话隔离、输入净化）；无人值守面默认 deny；4 个注入类 CVE（CVE-2026-9353/9366/10220/10222，中危，2026-04/05 版本 POC 公开）
- **国内可用性**：任意 OpenAI 兼容端点、Ollama、火山方舟 Coding Plan（GLM/Kimi/DeepSeek 等）→ 内网+国产模型/本地模型可行 [P0][P1]
- **嵌入方式**：Java 侧经 RestClient/WebClient 调用 `/v1/chat/completions` 或异步 `/v1/runs`；或 Spring AI MCP Server 供 Hermes 挂载企业工具；或 Webhook 事件触发
- **适配成本**：中——接口标准无需改 Java 架构，但需新增 Python 运维面、SQLite 与 MySQL 并存、审批/容器隔离配置、CVE 版本锁定

### 2.2 OpenClaw（外围轻量 Agent）

**身份确认**：OpenClaw = Clawdbot（2025-11）→ Moltbot（2026-01-27，Anthropic 商标 C&D）→ OpenClaw（2026-01-29 至今），作者 Peter Steinberger，2026-07 起由 OpenClaw 基金会维护 [P0]

- **核心架构**：单 Gateway 常驻进程（默认 loopback:18789），渠道插件式接入 WhatsApp/Telegram/Discord/Slack/Signal/iMessage/Teams/**飞书 Lark**/Matrix；多 Agent 按会话隔离路由；cron/一次性任务/hooks/webhook 自动化；浏览器控制带 SSRF 策略；exec approval 三层（security/ask/allowlist）
- **安全事件**：CVE-2026-25253（ClawJacked，CVSS 8.8，RCE/令牌窃取）+ ClawHavoc（1184 恶意技能、230 万美元加密币被盗）+ 腾讯朱雀扫 5 万技能 25% 可读写文件 + SkillProbe 94% 注入逃逸；CNNVD 已收录 82 个漏洞（超危 12）
- **嵌入方式**：Java 侧经 `/hooks/agent` Webhook 下发任务 + 封装 MCP Server（Streamable HTTP）暴露业务工具；技能内网自建（禁用公网 ClawHub）
- **适配成本**：中——单机可跑、渠道与 MCP 有官方路径，但安全加固工作量大

### 2.3 Dify（主编排底座）

- **许可证**：Modified Apache 2.0——商用允许，但不得运营多租户环境（1 workspace=1 tenant）、不得移除前端 LOGO；内部单租户自托管免费合规 [P0]
- **部署**：最低 2C4G，强制 PostgreSQL+Redis+向量库（默认 Weaviate），Docker Compose/Helm/源码，裸机 Windows 需 Docker Desktop [P0]
- **Agent 能力**：Workflow/Chatflow/Agent 节点齐全；Trigger（Schedule/Webhook/SaaS 事件）支持；批处理/迭代节点即批处理 [P0]
- **接入面**：`/v1/chat-messages`、`/v1/workflows/run`（Bearer Key，流式/阻塞）；v1.6.0 起 MCP 双向（Streamable HTTP）；Swagger 工具导入（直调自有 Java API）[P0]
- **企业能力**：多工作区/RBAC/SSO/SCIM/审计日志为付费版；社区版仅基础角色
- **选型理由**：`/v1` API 长期稳定 + MCP 双向 + Swagger 工具导入 + Trigger 节点已补齐；代价是引入 PG 容器（与现有 MySQL 并存，无需迁移）

### 2.4 WorkBuddy（协同补充）

- **企业版接入**：`/v2/enterprises/{id}/managedagent` OpenAPI（Agent CRUD/版本/会话/消息事件/A2A 绑定）；鉴权 `pt_`/`ck_` 双 Key，网关限频 600 QPS/IP [P0]
- **连接器**：OAuth2.0/API Key/MCP OAuth2.1/**自建应用免授权**；MCP Server URL+Streamable HTTP；对非腾讯系开放 [P0]
- **部署**：旗舰版 198 元/人/月、专享版 316 元/人/月（VPC+专属插件）、私有化版 300 席起自备算力 [P0]
- **选型理由**：反向集成（Java 系统调用其能力）规范完整；但私有化门槛高、成本大

### 2.5 豆包/Coze（可选协同）

- **火山方舟**：OpenAI 兼容 API（`ark.cn-beijing.volces.com/api/v3/chat/completions`），`stream=true` SSE；doubao-seed-2.1-pro 6 元/输出 30 元/百万 tokens [P0]
- **豆包工作**：插件=自定义连接器（MCP HTTP/STDIO），企业插件由管理员上架；豆包搜索开放服务经 MCP Server 接入，500 次/月免费 [P0]
- **HiAgent**：K8s+Helm+License 私有化，VPC 对等；采购为 License/订阅混合 [未公开/需商务]
- **Coze Studio 开源版**：Apache 2.0 无附加限制，但组件最重、PAT 最长 30 天、原生无定时调度 [P1]

### 2.6 n8n（系统集成旁路）

- **许可证**：Sustainable Use License——内部业务用途免费，外部托管/白标需商业许可 [P0]
- **部署**：最轻（Node.js 单容器 2C/2-4G）；默认 SQLite，支持 PostgreSQL；v2.0 已移除 MySQL 支持（但 MySQL 凭据节点原生存在）[P0]
- **MCP 最成熟**：MCP Server Trigger(SSE)/实例级 MCP/MCP Client Tool 三向齐备 [P0]
- **选型理由**：定时/Webhook 类非 LLM 自动化场景的最佳补充

---

## 三、多 Agent 协同驱动全业务流程设计框架

### 3.1 总体架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        企业业务系统 (Java)                           │
│  ├─ 业务域：投标/合同/设计/生产/交付/变更/成本/售后/档案              │
│  ├─ 项目管理：Project / Phase / Gate / Milestone                    │
│  ├─ 任务管理：Task / TaskLog / TaskExecutor (DB 队列轮询)            │
│  ├─ 事件总线：EventBusService (sys_domain_event 轮询)               │
│  ├─ AI 工作台：AiChatService / MasterAgentService                   │
│  └─ ai-gateway：8090 (限流/降级/加密)                               │
│                                                                     │
│  ├─ [新增] Agent Registry (AgentCard 目录 + 版本协商)               │
│  ├─ [新增] DecisionAdviceService (建议输出+护栏+置信度路由)          │
│  └─ [新增] A2A Gateway (A2A v1.0 HTTP+JSON 绑定)                    │
└──────────┬──────────────────┬──────────────────┬───────────────────┘
           │ MCP Server        │ A2A v1.0        │ HTTP API / Webhook
           │ (Streamable)     │ (Agent 间)       │ (OpenAI 兼容)
┌──────────▼──────────────────▼──────────────────▼───────────────────┐
│                        多 Agent 协同层                              │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 编排底座：Dify (Workflow/Trigger/Agent 节点)                 │   │
│  │  ├─ 编排角色：Supervisor (中心编排+状态机)                   │   │
│  │  ├─ 传输：MCP Streamable HTTP ↔ A2A v1.0 HTTP+JSON          │   │
│  │  └─ 接入：Swagger 工具导入 ← Spring AI MCP Server           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ Hermes Agent │  │  OpenClaw    │  │  n8n 工作流  │              │
│  │ (业务执行)   │  │ (审批兜底)   │  │ (系统集成)   │              │
│  │ Python 进程  │  │ Node 进程    │  │ Node 容器    │              │
│  │ SQLite 内部  │  │ 技能内网    │  │ SQL/SQLite   │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │ 外部产品（可选）：WorkBuddy 企业版 / 豆包 HiAgent            │   │
│  │ 接入方式：A2A 绑定 Managed Agents / 企业 MCP 连接器          │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 Agent 角色划分

| 角色 | 承担 Agent | 职责 | 介入方式 |
| --- | --- | --- | --- |
| **业务执行 Agent** | Hermes Agent（主）+ 自研 Java Agent（AGT 系列） | 具体业务任务执行（爬虫/提取/分析/草拟） | MCP Client ← 业务工具 Server；A2A 接受编排任务 |
| **编排 Agent** | Dify（Supervisor） | 流程编排、任务分派、状态管理、触发调度 | A2A Supervisor；MCP 调用业务工具；Trigger 调度 |
| **审批兜底 Agent** | OpenClaw | 人工审批前置的兜底建议、风险提示 | Webhook 触发；exec approval 审批流 |
| **系统集成 Agent** | n8n | 定时巡检、Webhook 转发、跨系统数据同步 | Schedule Trigger + 400+ 节点 |
| **可选协同 Agent** | WorkBuddy / 豆包 | 外部协同场景（文档生成/知识库问答） | A2A 绑定 + 企业 MCP 连接器 |

### 3.3 多 Agent 协同协议选型

| 协议 | 用途 | 传输绑定 | 选型理由 |
| --- | --- | --- | --- |
| **A2A v1.0** | Agent ↔ Agent 通信协调 | HTTP+JSON（起步），后续可扩展 gRPC | v1.0 稳定版、150+ 组织支持、官方 Java SDK、显式 Task 状态机与 HITL 挂起 [P0] |
| **MCP 2026-07** | Agent ↔ 工具/上下文集成 | Streamable HTTP（主），stdio（辅） | 事实标准、Spring AI SDK 支持、Dify/Hermes/n8n 三向支持 |
| **OpenAI 兼容 API** | Hermes Agent ↔ LLM | HTTP REST + SSE | Hermes 原生支持、统一限流入口 |

**分工原则**：MCP 做 Agent 内部的工具与上下文集成，A2A 做 Agent 间通信协调——实践中 "MCP inside agents, A2A between agents" [P0]

### 3.4 任务状态机（对齐 A2A v1.0 + 业务扩展）

```
                   ┌──────────────┐
          ┌──────►│   submitted   │◄──── 外部 Agent 提交
          │        └──────┬───────┘
          │               │
          │               ▼
          │        ┌──────────────┐
          │        │   working    │ ← 编排 Agent 分派子任务
          │        └──────┬───────┘
          │               │
          │       ┌───────┼────────┬────────┐
          │       ▼       ▼        ▼        ▼
          │  ┌──────┐ ┌──────┐ ┌──────────┐ ┌──────────┐
          │  │ input │ │ auth │ │ completed │ │  failed  │
          │  │required│ │required│ │(终态)    │ │ (终态)   │
          │  └──┬───┘ └──┬───┘ └──────────┘ └──────────┘
          │     │        │
          │     └──► working ◄── 信息补充/授权完成
          │
          │  业务扩展态：
          │  ┌──────────┐   ┌──────────┐
          └──►│ confirmed │   │  rejected │   (人工审批分支)
              │ (终态)   │   │ (终态)   │
              └──────────┘   └──────────┘
```

**状态定义**：

| 状态 | 含义 | 迁移条件 |
| --- | --- | --- |
| submitted | 已提交，待编排 | 外部 Agent 调用 A2A submit |
| working | 执行中 | 编排 Agent 分派后 |
| input-required | 需要补充信息 | Hermes Agent 判定缺信息 |
| auth-required | 需要人工授权 | 涉及 CUD/资金/审批类操作 |
| completed | 执行成功 | 业务校验通过 |
| failed | 执行失败 | 异常/超时/预算耗尽 |
| canceled | 已取消 | 用户主动取消 |
| rejected | 审批被拒 | 人工驳回 |
| confirmed | 审批通过 | 人工确认执行 |

**持久化**：在现有 `agent_task` 表基础上扩展（status 枚举对齐 A2A）+ 新增 `agent_task_step` 子任务表 + `agent_decision_advice` 建议表。

---

## 四、项目全生命周期 × 任务全流程 Agent 介入节点

### 4.1 介入节点矩阵

| # | 生命周期 | 任务环节 | 介入 Agent | 介入方式 | 建议内容 | 触发条件 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | **投标** | 中标 → 立项 | 编排 Agent + 业务执行 Agent | TENDER_WON 事件 | 自动生成项目草案（WBS 骨架、风险清单、交付物预估） | 中标通知到达 |
| 2 | **立项** | WBS/风险预审 | 业务执行 Agent | ProjectService.create 扩展事件 | WBS 完整性检查、风险登记自动草拟、合规预审 | 项目创建后 |
| 3 | **阶段** | 启动/完成决策 | 编排 Agent + 业务执行 Agent | updatePhaseStatus 扩展事件 | 阶段目标对齐检查、资源就绪建议、里程碑偏差预警 | 阶段状态变更 |
| 4 | **里程碑** | 完成/延期 | 业务执行 Agent | markMilestone 扩展事件 | 完成证据自动归档、延期原因分析、赶工计划建议 | 里程碑状态变更 |
| 5 | **门禁** | 审批 | 审批兜底 Agent | GateController + 人工审批流 | 门禁预审建议（合规/质量/交付物完整性）、风险等级提示 | 门禁提交审批 |
| 6 | **任务创建** | 拆解/指派建议 | 业务执行 Agent | TaskService.create 扩展事件 | 任务拆解建议、最优指派人选、预估工时 | 任务创建时 |
| 7 | **任务执行** | 状态变更建议 | 业务执行 Agent | changeStatus 扩展事件 | 进展偏差预警、阻塞项识别、升级建议 | 任务状态变更 |
| 8 | **任务提交** | 待审核/验收预审 | 审批兜底 Agent | changeStatus 2→3 分支 | 交付物完整性预审、验收标准匹配度、建议验收人 | 任务提交审核 |
| 9 | **验收完成** | 跨域通知 | 编排 Agent | DELIVERY_ACCEPTED 事件 | 自动触发后续流程（合同登记/回款跟踪/归档） | 验收通过 |
| 10 | **红灯升级** | 干预 | 编排 Agent + 审批兜底 Agent | REDCARD_ESCALATED 事件 | 根因分析、缓解方案、升级路径、建议会议召集 | 红灯触发 |
| 11 | **晨会** | 建议输出 | 业务执行 Agent | AGT-26 MorningBriefHandler | 今日重点、红黑榜、待办摘要、能力建议 | 每日定时 |
| 12 | **流程节点** | 流程预审/建议 | 编排 Agent | WorkflowEngineService.activate | 流程节点前置条件检查、审批建议、后置动作 | 流程实例推进 |
| 13 | **Agent 结果** | 回传通知 | 编排 Agent | AGENT_TASK_FINISHED 事件 | 结果摘要、置信度、后续建议、人工确认请求 | Agent 执行完成 |
| 14 | **收尾归档** | 复盘建议 | 业务执行 Agent | 归档扩展事件 | 经验教训提取、知识沉淀建议、流程优化建议 | 项目归档 |
| 15 | **持续运营** | 巡检报告 | 系统集成 Agent (n8n) | 定时触发 | 系统健康巡检、数据一致性校验、安全告警 | 每日/每周定时 |

### 4.2 介入方式实现路径

| 介入方式 | 实现方案 | 涉及文件 |
| --- | --- | --- |
| **事件驱动**（推荐） | EventBusService 扩展事件类型 + DomainEventHandler SPI 新增消费者 | `event/EventBusService.java`、`event/model/DomainEventType.java`、`event/bus/handlers/` |
| **状态变更监听** | TaskService.changeStatus / updatePhaseStatus / markMilestone 扩展事件发布 | `task/service/TaskService.java`、`project/service/ProjectService.java` |
| **Workflow 节点扩展** | WorkflowEngineService 新增 "AgentSuggestion" 节点类型 | `workflow/service/WorkflowEngineService.java` |
| **Notification 扩展** | NotificationService 新增 AgentSuggestionChannel | `dingtalk/service/NotificationService.java` |
| **Agent 卡片回传** | TaskExecutor 扩展 Agent 执行结果回推 | `agent/service/TaskExecutor.java` |

### 4.3 建议输出格式规范

```json
{
  "suggestionId": "uuid",
  "scene": "TASK_CREATE | PHASE_COMPLETE | ...",
  "sourceEntity": {"type": "TASK", "id": 123},
  "agentId": "hermes-agent-01",
  "agentRole": "业务执行",
  "content": {
    "type": "ACTION | INFO | WARNING | REQUEST",
    "title": "建议内容标题",
    "body": "详细建议正文",
    "structured": {
      "field1": "value1",
      "field2": "value2"
    }
  },
  "confidence": 0.92,
  "evidence": [
    {"source": "project_milestone", "ref": "M-2026-001"},
    {"source": "risk_register", "ref": "R-45"}
  ],
  "riskLevel": "LOW | MEDIUM | HIGH",
  "requiresApproval": true,
  "approvalPayload": {
    "action": "AUTO_ASSIGN | AUTO_COMPLETE | ...",
    "target": "task_123",
    "params": {"assignee": 5}
  },
  "sla": {"responseDeadline": "2026-11-16T18:00:00+08:00"}
}
```

**字段说明**：

| 字段 | 说明 | 约束 |
| --- | --- | --- |
| confidence | 建议置信度 | 0-1，双校验（schema+枚举），低于 0.85 强制人工 |
| evidence | 建议依据引用 | 至少 1 条，支持业务表引用 |
| riskLevel | 风险等级 | LOW/MEDIUM/HIGH，HIGH 强制人工审批 |
| requiresApproval | 是否需要人工审批 | CUD/资金/审批类强制 true |
| approvalPayload | 审批后执行的操作 | HMAC 锁定防篡改 |

**触发条件**：

| 场景 | 触发 Agent | 触发条件 | 置信度阈值 |
| --- | --- | --- | --- |
| 任务创建 | 业务执行 | 状态变更 submitted | ≥0.85 自动建议 |
| 阶段完成 | 业务执行 | 状态变更 completed | ≥0.9 自动建议 |
| 门禁审批 | 审批兜底 | 门禁提交 | 全部强制人工 |
| 红灯升级 | 编排+审批兜底 | REDCARD_ESCALATED | 全部强制人工 |
| 验收提交 | 审批兜底 | 状态变更 2→3 | ≥0.85 自动预审 |
| 日常巡检 | 系统集成 | 定时 | N/A |

---

## 五、技术路线图（分 4 阶段实施）

### 5.1 阶段一：Agent 接入层开发 + 业务流程适配（1-3 个月）

| 工作包 | 任务 | 交付标准 |
| --- | --- | --- |
| WP1-1 | Hermes Agent 部署与配置 | Python 进程部署于内网 Linux/Docker，对接 ai-gateway（OpenAI 兼容），MCP Server 桥接业务工具，版本锁定 v0.23.x，容器隔离开启 |
| WP1-2 | Dify 部署与配置 | Docker Compose 部署（PG+Redis+Weaviate 容器），`/v1/chat-messages` + `/v1/workflows/run` 接入验证 |
| WP1-3 | 业务工具 MCP Server 搭建 | Spring AI MCP Server（Streamable HTTP）暴露 AGT 能力点为 MCP 工具，Swagger 工具导入 Dify |
| WP1-4 | 统一 Connector 抽象（复用 AI 升级方案） | Authenticator/Signer/RateLimiter/ResponseMapper 五组件实现，钉钉/飞书/企微签名适配器 |
| WP1-5 | EventBusService 扩展 | 新增 15 类业务域事件（TASK_CREATED/TASK_STATUS_CHANGED/PHASE_COMPLETED/...） |
| WP1-6 | 建议输出通道 | NotificationService 扩展 AgentSuggestionChannel（站内信+钉钉+AI 工作台卡片） |

**验收标准**：
- [ ] Hermes Agent 通过 MCP 调用至少 5 个业务工具
- [ ] Dify Workflow 编排调用 Hermes Agent 完成至少 3 个业务场景
- [ ] EventBus 扩展事件消费者实现 ≥8 个（创建/变更/完成/红灯/验收等）
- [ ] 建议输出卡片在 AI 工作台正确渲染

### 5.2 阶段二：多 Agent 调度机制搭建 + 护栏（4-6 个月）

| 工作包 | 任务 | 交付标准 |
| --- | --- | --- | --- |
| WP2-1 | A2A Gateway 实现 | A2A v1.0 HTTP+JSON 绑定，Agent Card 签名注册，Task 状态机 8 态实现 |
| WP2-2 | Agent Registry | 内部目录服务，Agent Card 存储，版本协商，JWS 签名验证 |
| WP2-3 | Supervisor 编排器实现 | 中心编排 + 显式状态机 + 并行 fan-out，确定性流程代码路径 |
| WP2-4 | Human-in-the-Loop 审批流 | 结构化建议 → 置信度路由 → 审批 payload HMAC → 不可变审计日志 |
| WP2-5 | 护栏与治理 | 结构化输出双校验（schema+枚举）、NeMo Guardrails 输入/输出护栏、越狱检测、PII 脱敏 |
| WP2-6 | 容错与预算 | 每 Agent token/调用/时长预算、重试预算+退避+幂等键、边界 schema 校验、断路器+Saga 补偿 |
| WP2-7 | OpenClaw 部署（可选） | 作为审批兜底 Agent，exec approval 开启，内网技能仓库 |

**验收标准**：
- [ ] A2A Task 状态机全流程测试通过（submitted→working→各终态 + input/auth 挂起恢复）
- [ ] 护栏检测违规率 ≥99%，延迟 ≤0.5s
- [ ] 每任务预算控制在 35 次 LLM 调用以内
- [ ] 级联故障防护验证通过（错误值不穿越边界）

### 5.3 阶段三：辅助建议规则配置 + 全链路测试（7-9 个月）

| 工作包 | 任务 | 交付标准 |
| --- | --- | --- | --- |
| WP3-1 | 15 个介入节点规则配置 | 每个节点的触发条件、置信度阈值、建议模板、审批规则 |
| WP3-2 | 决策建议反馈闭环 | 采纳率追踪 → 数据飞轮（NVIDIA NeMo 类）→ 持续优化 |
| WP3-3 | 集成测试 | 全链路测试套件（投标→立项→执行→验收→归档全流程） |
| WP3-4 | 压力测试 | 并发 Agent 任务 ≥5 个同时执行不降级；LLM 限流保护验证 |
| WP3-5 | 安全测试 | SSRF 防护、技能包投毒防护、MCP 工具描述投毒防护、密钥泄漏测试 |
| WP3-6 | n8n 部署（可选） | 系统集成旁路（定时巡检/Webhook 转发） |

**验收标准**：
- [ ] 15 个介入节点建议准确率 ≥85%
- [ ] Agent 辅助建议采纳率 ≥60%
- [ ] 全链路测试通过率 100%
- [ ] 安全测试零高危漏洞

### 5.4 阶段四：外部协同接入 + 生产加固（10-12 个月）

| 工作包 | 任务 | 交付标准 |
| --- | --- | --- | --- |
| WP4-1 | WorkBuddy 企业版接入（可选） | A2A 绑定 Managed Agents，协同场景（文档生成/知识库问答）验证 |
| WP4-2 | 豆包 HiAgent 接入（可选） | 企业 MCP 连接器对接，数据边界合规确认 |
| WP4-3 | 评测基线 | 内部 Agent 评测集（对标 SuperCLUE-Office），自动化评测管线 |
| WP4-4 | 可观测性 | OpenTelemetry 全链路 Trace、SLO 仪表盘、告警规则 |
| WP4-5 | 降级与容灾 | 多模型主备、降级策略、跨机房容灾、灰度发布 |

**验收标准**：
- [ ] 外部 Agent 协同场景 ≥3 个
- [ ] Agent P95 响应延迟 ≤10s
- [ ] 降级切换 RTO ≤30s
- [ ] 评测基线分 ≥80

---

## 六、落地执行细节

### 6.1 Agent 与现有系统对接字段定义

#### 6.1.1 Agent Card（A2A 注册）

```json
{
  "name": "hermes-agent-01",
  "description": "业务执行 Agent：任务分解、文档草拟、数据分析",
  "version": "1.0.0",
  "skills": [
    {"id": "task.decompose", "description": "任务拆解建议", "inputs": ["taskDesc", "projectId"], "outputs": ["subTasks"]},
    {"id": "doc.draft", "description": "文档草拟", "inputs": ["docType", "context"], "outputs": ["content"]},
    {"id": "risk.analyze", "description": "风险分析", "inputs": ["projectId"], "outputs": ["risks"]}
  ],
  "securitySchemes": ["bearer"],
  "capabilities": {
    "execution": ["tool-use", "plan-and-execute"],
    "memories": ["working", "episodic"]
  },
  "endpoint": "http://hermes-agent:8642",
  "signatures": {
    "agent-card": "jws-signed"
  }
}
```

#### 6.1.2 业务实体与 Agent 交互字段

| 实体 | 新增字段 | 类型 | 说明 |
| --- | --- | --- | --- |
| agent_task | a2a_task_id | String | A2A Task ID |
| agent_task | step_count | int | 子任务数 |
| agent_task | budget_used | int | 已用 LLM 次数 |
| agent_task | budget_limit | int | 预算上限（默认 35） |
| agent_task | confidence | decimal | 结果置信度 |
| agent_task | risk_level | String | LOW/MEDIUM/HIGH |
| agent_task_step | id | Long | 主键 |
| agent_task_step | a2a_step_id | String | A2A Step ID |
| agent_task_step | status | String | submitted/working/completed/failed |
| agent_task_step | agent_id | String | 执行 Agent |
| agent_decision_advice | id | Long | 主键 |
| agent_decision_advice | scene | String | 场景标识 |
| agent_decision_advice | source_type | String | 来源实体类型 |
| agent_decision_advice | source_id | Long | 来源实体 ID |
| agent_decision_advice | agent_id | String | 建议 Agent |
| agent_decision_advice | content_json | Text | 建议内容 JSON |
| agent_decision_advice | confidence | decimal | 置信度 |
| agent_decision_advice | risk_level | String | 风险等级 |
| agent_decision_advice | requires_approval | tinyint | 是否需要审批 |
| agent_decision_advice | approval_payload_json | Text | 审批 payload JSON |
| agent_decision_advice | approval_hmac | String | HMAC 锁定 |
| agent_decision_advice | status | String | pending/approved/rejected/expired |
| agent_decision_advice | evidence_json | Text | 依据引用 JSON |

#### 6.1.3 数据库 DDL 新增

```sql
-- Agent 决策建议表
CREATE TABLE agent_decision_advice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  scene VARCHAR(64) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  agent_role VARCHAR(32) NOT NULL,
  content_json TEXT NOT NULL,
  confidence DECIMAL(3,2) NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  requires_approval TINYINT NOT NULL DEFAULT 0,
  approval_payload_json TEXT,
  approval_hmac VARCHAR(128),
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  evidence_json TEXT,
  response_deadline DATETIME,
  approver_id BIGINT,
  approved_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_scene_source (scene, source_type, source_id),
  INDEX idx_status (status),
  INDEX idx_agent (agent_id)
);

-- A2A Task 子任务表
CREATE TABLE agent_task_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  a2a_step_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  agent_role VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  input_json TEXT,
  output_json TEXT,
  error_msg VARCHAR(512),
  started_at DATETIME,
  completed_at DATETIME,
  budget_used INT NOT NULL DEFAULT 0,
  INDEX idx_task (task_id),
  INDEX idx_status (status)
);

ALTER TABLE agent_task ADD COLUMN a2a_task_id VARCHAR(64) AFTER id;
ALTER TABLE agent_task ADD COLUMN step_count INT NOT NULL DEFAULT 0 AFTER a2a_task_id;
ALTER TABLE agent_task ADD COLUMN budget_used INT NOT NULL DEFAULT 0 AFTER step_count;
ALTER TABLE agent_task ADD COLUMN budget_limit INT NOT NULL DEFAULT 35 AFTER budget_used;
ALTER TABLE agent_task ADD COLUMN confidence DECIMAL(3,2) AFTER budget_limit;
ALTER TABLE agent_task ADD COLUMN risk_level VARCHAR(16) DEFAULT 'MEDIUM' AFTER confidence;
```

### 6.2 数据流转规则

```
用户/系统触发
  │
  ▼
业务实体变更 (Task/Phase/Milestone/Gate)
  │
  ▼
EventBusService.publish(DOMAIN_EVENT)
  │
  ▼
AgentDecisionAdviceService.evaluate(scene, entity)
  │
  ├─ 置信度 ≥ 阈值 + 风险 < HIGH + 非 CUD → 自动建议
  ├─ 置信度 < 阈值 OR 风险 ≥ HIGH OR CUD → 人工审批
  │
  ▼
Agent 调度（A2A Supervisor → Hermes Agent → MCP 工具）
  │
  ▼
建议输出（结构化 JSON）
  │
  ├─ 护栏双校验（schema + 枚举）
  ├─ 置信度路由
  ├─ 审批 payload HMAC 锁定
  │
  ▼
DecisionAdvicePersist 持久化
  │
  ├─ NotificationService 推送（站内信/钉钉/AI 工作台）
  ├─ 人工审批队列（若 requiresApproval=true）
  │
  ▼
执行（自动/人工确认）
  │
  ├─ 业务 API 调用
  ├─ 结果回写 agent_task
  ├─ 审计日志记录
  │
  ▼
反馈闭环（采纳率追踪 → 数据飞轮）
```

### 6.3 异常容错处理机制

| 异常场景 | 处理策略 |
| --- | --- |
| Hermes Agent 不可用 | Dify 降级为直接 HTTP 调用业务工具（跳过 LLM 规划）+ 规则引擎回退 |
| Dify 不可用 | 降级为 Java 内置 Agent 编排（MasterAgentService 规则版） |
| MCP 工具调用超时 | Resilience4j TimeLimiter（30s）+ 降级为缓存值 + 通知编排 Agent |
| A2A Agent 不可达 | 标记失败 + 重试 3 次指数退避 + 超限转人工 |
| LLM 限流（35 次/分） | 全局令牌桶闸门 + 本地缓存命中优先 + 离线批处理 |
| 建议置信度低 | 强制人工审批 + 记录为反例供数据飞轮 |
| 护栏检测违规 | 拦截 + 审计 + 降级为规则引擎回退 |
| 预算耗尽（35 次/任务） | 标记 failed + 部分结果回传 + 通知人工接手 |
| 子 Agent 死锁 | 超时心跳检测 + 强制终止 + 部分结果回传 |
| 级联失败（错误值传播） | 边界 schema 校验 + 失败类型化 + checkpoint 恢复 |

### 6.4 权限管控逻辑

| 权限维度 | 实现方案 |
| --- | --- |
| Agent 身份 | 每 Agent 独立 OIDC/mTLS 身份（A2A securitySchemes） |
| 工具权限 | MCP 工具按 skill 最小 scope 授权（只读/写入/执行） |
| 建议审批 | CUD/资金/审批类建议强制人工审批，payload HMAC 锁定 |
| 数据访问 | Agent 通过业务 API 访问，复用现有 Spring Security + 业务权限校验 |
| 审计追踪 | 全链路审计日志（请求→决策→执行→结果），不可变 |
| 护栏策略 | 代码化、版本化、置于编排层（OPA/中断节点） |

---

## 七、测试与验证标准

### 7.1 单元测试

| 测试域 | 覆盖率目标 | 关键测试项 |
| --- | --- | --- |
| A2A Gateway | ≥85% | Task 状态机 8 态迁移、Agent Card 签名验证、异常传输 |
| 决策建议服务 | ≥80% | 置信度路由、护栏双校验、审批 payload HMAC、证据引用 |
| Connector 抽象 | ≥80% | 各平台签名校验、429 重试、密钥加密解密 |
| EventBus 扩展 | ≥80% | 15 类事件发布/消费、幂等、重试转 DEAD |
| 护栏模块 | ≥85% | 越狱检测、PII 脱敏、输出 schema 校验 |

### 7.2 集成测试

| 测试场景 | 验证标准 |
| --- | --- |
| 中标→立项全链路 | 中标通知→自动生成项目→WBS 草案→风险清单 |
| 任务创建→拆解→指派 | 创建→Hermes Agent 拆解→建议指派人选→人工确认 |
| 里程碑完成→归档 | 完成→自动归档→知识沉淀建议→跨域通知 |
| 红灯升级→干预 | 触发→根因分析→缓解方案→升级路径 |
| 验收提交→预审→通过 | 提交→预审建议→人工确认→跨域通知 |
| 35 次/分限流验证 | 并发 5 Agent 任务不超限 |
| A2A 跨 Agent 调用 | Dify Supervisor → Hermes → OpenClaw 链路 |
| 降级验证 | Hermes/Dify/LLM 不可用时的降级路径 |

### 7.3 安全测试

| 测试项 | 验证标准 |
| --- | --- |
| SSRF 防护 | 8 种内网段 + IPv6 映射段全部拦截 |
| 技能包投毒 | 恶意技能包（文件外传/命令执行）被权限清单拒绝 |
| MCP 工具描述投毒 | 描述哈希不匹配被拦截 |
| A2A 身份伪造 | 无效 JWS 签名被拒绝 |
| 密钥泄漏 | 日志/响应中无明文密钥 |
| 注入攻击 | 护栏越狱检测率 ≥99% |

### 7.4 回归与门禁

- 全量回归测试（现有 126 用例）100% 通过
- 每次 Agent/技能包/依赖升级触发全量回归
- 新增功能配套单元测试 + 集成测试，覆盖率达目标
- 评测基线每季度更新，分阶段跟踪

---

## 八、风险与应对

| 风险 | 概率 | 影响 | 缓解措施 |
| --- | --- | --- | --- |
| Hermes Agent 注入类 CVE 复发 | 中 | 高 | 锁定 v0.23.x、容器隔离、注入扫描、定期安全审计 |
| OpenClaw 供应链攻击 | 高 | 高 | 仅内网技能仓库、禁用公网 ClawHub、沙箱隔离 |
| LLM 35 次/分限流冲突 | 高 | 中 | 每 Agent 硬预算+execute_code 折叠+本地缓存优先+离线批处理 |
| A2A/MCP 规范迭代快 | 高 | 中 | 锁定小版本、Spring AI/Hermes 官方跟进、自动化适配测试 |
| Dify 强制 PG 依赖 | 中 | 低 | PG 容器并存、数据隔离、无需迁移现有 MySQL |
| 多 Agent 级联故障 | 中 | 高 | 边界 schema 校验、失败类型化、checkpoint、断路器+Saga 补偿 |
| Agent 建议准确率不足 | 中 | 中 | 反馈闭环+数据飞轮+人工审核兜底 |
| 企业数据出域合规 | 中 | 高 | 私有化部署+数据不出域+HiAgent 商务确认 |

---

## Sources

- [A2A v1.0 发布](https://a2a-protocol.org/latest/blog/2026/03/12/a2a-protocol-ships-v10-production-ready-standard-for-agent-to-agent-communication/)
- [A2A 加入 AAIF](https://www.linuxfoundation.org/press/a2a-protocol-surpasses-150-organizations-lands-in-major-cloud-platforms-and-sees-enterprise-production-use-in-first-year)
- [A2A Task 生命周期](https://a2a-protocol.org/latest/topics/life-of-a-task/)
- [Hermes Agent 文档](https://hermes-agent.nousresearch.com/docs/)
- [Hermes Agent GitHub](https://api.github.com/repos/NousResearch/hermes-agent)
- [OpenClaw 文档](https://docs.openclaw.ai/)
- [OpenClaw GitHub](https://api.github.com/repos/openclaw/openclaw)
- [CVE-2026-25253](https://nvd.nist.gov/vuln/detail/cve-2026-25253)
- [Dify 许可证](https://github.com/langgenius/dify/blob/main/LICENSE)
- [Dify MCP 发布](https://docs.dify.ai/en/cloud/use-dify/publish/publish-mcp)
- [n8n MCP 集成](https://docs.n8n.io/connect/connect-to-n8n-mcp-server.md)
- [n8n 许可证](https://support.n8n.io/article/can-i-use-your-license-for-my-use-case)
- [WorkBuddy 企业版 API](https://cloud.tencent.com/document/product/1831/140342)
- [WorkBuddy 连接器](https://cloud.tencent.com/document/product/1831/134453)
- [豆包工作插件](https://www.doubao.com/work/docs/zh-cn/articles/705018132598-plugins)
- [火山方舟 API](https://www.volcengine.com/docs/82379/1298459)
- [NeMo Guardrails](https://developer.nvidia.com/nemo-guardrails/)
- [Anthropic 多 Agent 研究](https://www.anthropic.com/engineering/multi-agent-research-system)
- [JetBrains Agent 框架对比](https://www.jetbrains.com/pages/ai-agents/building/agent-framework-comparison/)
- [SkillProbe 逃逸率](https://security.tencent.com/index.php/blog/msg/224)
- [ThaiCERT 恶意技能](https://www.thaicert.or.th/en/2026/02/04/researchers-discover-over-200-malicious-skills-on-openclaw-distributing-password-stealing-malware/)
