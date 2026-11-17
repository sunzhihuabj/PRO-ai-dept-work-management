# Hermes Agent 与 Hermes UI 全量接口深度调研报告（嵌入前调研）

> 调研日期：2026-11-17 ｜ 调研方式：本地源码精读（4 路并行探索代理 + 关键代码抽查）+ 官方/社区网络调研
> 调研目的：摸清 Hermes Agent 开源项目与其 UI 项目的**全部接口**及**全量调用方式**，为嵌入本部门管理系统（Java 8081 + Vue 5173）提供决策依据。
> 源码根：`C:\Users\sunzh\AppData\Local\hermes\hermes-agent`（git 安装，v0.22.5+3482.gf0d9488，Python 3.14.9，MIT License）
> 下文行号均相对该源码根。

---

## 1. 结论速览（TL;DR）

1. **Hermes Agent** 是 Nous Research 出品的开源自我改进型 AI Agent（MIT，GitHub `NousResearch/hermes-agent`，官网 hermes-agent.nousresearch.com），Python 实现，本地以 git 方式安装（国内镜像 cnb.cool/hermesagent-cn/hermes-agent-cn-mirror，每 6 小时同步上游）。
2. **官方不存在名为 "hermes-agent-ui" 的独立仓库**。UI 有三种官方形态：内置终端 UI（`ui-tui/`）、Electron 桌面端 Hermes Desktop（`apps/desktop/`）、共享 TS 协议客户端（`apps/shared/`，7259 行生成式契约，258 个 JSON-RPC 方法）。社区另有三个 WebUI 项目（见 §4.4），其中 `0xPier/Hermes-Agent-UI` 与名字最接近但质量/活跃度低（6 star）。
3. **对外接口共四层**：① API Server（8642，aiohttp，OpenAI 兼容 + /v1/runs 异步委托 + SSE）② ACP（stdio JSON-RPC，编辑器协议，hermes-acp.exe）③ tui_gateway（WebSocket /api/ws JSON-RPC，桌面端同款，258 方法）④ Gateway 平台通道（Telegram/Discord/钉钉/飞书/企微等 23 种内置 + webhook 8644）。
4. **嵌入推荐：继续走 API Server（8642）为主通道**——语言无关、Bearer 鉴权、run 幂等/SSE/steer/stop 闭环齐全，本系统 B1-B5 已验证可行。ACP 是 stdio 进程管道（Java 服务端嵌入不合适）；tui_gateway 是"桌面端全功能面板"选项（能力最全但契约 7259 行 TS）。
5. **现状差距**：本系统已消费 6 个端点（health / runs / get-run / events / stop / approval），未消费的高价值能力有 **steer（运行中改需求）、clarify（澄清问答）、Idempotency-Key 幂等、SSE Last-Event-ID 断线续传、/api/sessions 会话管理族、/v1/capabilities 能力自描述、model_options 推理档位**等（详见 §5.2）。

---

## 2. 项目身份与架构

### 2.1 Hermes Agent 开源项目

| 项 | 内容 |
|---|---|
| 出品方 | [Nous Research](https://nousresearch.com) |
| 仓库 | [NousResearch/hermes-agent](https://github.com/nousresearch/hermes-agent)（MIT） |
| 官网/文档 | [hermes-agent.nousresearch.com](https://hermes-agent.nousresearch.com) / [/docs](https://hermes-agent.nousresearch.com/docs) |
| 本地版本 | v0.22.5+3482.gf0d9488（2026.9.24），install method: git，Python 3.14.9，OpenAI SDK 2.54.0 |
| 本地镜像 | cnb.cool/hermesagent-cn/hermes-agent-cn-mirror（29 star，6 小时同步上游） |
| 核心卖点 | 自我改进闭环（经验→skills 自动沉淀、FTS5 会话检索、用户建模）、任意模型切换、6 种执行后端（local/Docker/SSH/Modal/Daytona/Vercel）、cron 自动化、子代理并行 |

**架构一句话**：`gateway` 守护进程是**唯一执行者（session authority，durable FIFO）**，所有接入面（CLI/TUI/ACP/桌面/API Server/IM 平台）都只是它的 viewer/admitter；ACP 适配器与桌面端均不执行 agent，只投影到 gateway（`acp_adapter/gateway_server.py:1`、`gateway/platforms/api_server_bot_chat.py:1-9`）。

### 2.2 "hermes-agent-ui" 身份判定（网络调研结论）

| 候选 | 判定 |
|---|---|
| 官方 `hermes-agent-ui` 独立仓库 | **不存在**。UI 在主仓库内：`ui-tui/`（终端 UI）、`apps/desktop/`（Electron 桌面端 Hermes Desktop）、`apps/shared/`（TS 协议客户端，浏览器 dashboard 与桌面端共用） |
| [0xPier/Hermes-Agent-UI](https://github.com/0xPier/Hermes-Agent-UI) | 社区第三方（FastAPI+WebSocket 驱动 hermes CLI + React/Vite 前端，Docker 部署，14 commits/6 star，低活跃）。**不经 8642**，后端直接 spawn CLI |
| nesquena/hermes-webui | 社区最流行的 WebUI（cnb.cool 有中文镜像 `hermes-webui-cn-mirror`，3377 commits，Python+vitepress 风格无构建 vanilla JS，5303 测试）。默认绑 127.0.0.1:8787，支持 `HERMES_WEBUI_CHAT_BACKEND=gateway` 桥接 8642 |
| MNPickle/hermes-webui | 另一社区本地浏览器 UI |

**对本项目的意义**：不需要引入任何社区 UI——我们已有自己的 Vue 前端，正确姿势是后端消费 Hermes 官方 API、前端自研组件（已按此路径做了 B1-B5/C1-C2）。

### 2.3 本地安装与运行形态

- 可执行：`%LOCALAPPDATA%\hermes\bin\hermes.exe`（主 CLI）、`hermes-acp.exe`（ACP 适配器）
- 运行方式：计划任务 `DepSvc-svc-hermes` → [deploy/svc-hermes.cmd](../deploy/svc-hermes.cmd) → `hermes gateway run --external-supervisor` 前台常驻
- API Server 开关（`%LOCALAPPDATA%\hermes\.env`）：
  ```ini
  API_SERVER_ENABLED=true
  API_SERVER_HOST=127.0.0.1
  API_SERVER_PORT=8642
  API_SERVER_KEY=dept-hermes-api-2026r2-K7mR4pXwL9cT2vBn
  ```
- 数据目录：`state.db`（会话/admission FIFO）、`runs_idempotency.db`、`response_store.db`、`sessions/`、`skills/`、`kanban.db`、`webhook_subscriptions.json` 等
- 模型与工具接线（config.yaml，见 docs/阶段一接口说明文档.md §4）：`providers.dept-gateway.base_url=http://localhost:8090/v1`（模型走本系统 ai-gateway 门面）；`mcp_servers.dept-mgmt.url=http://localhost:8081/api/mcp` + `X-API-Key` + `X-Acting-User-Id: "1"`（必须加引号，否则 YAML 解析成 int 导致 httpx 连接超时——已知坑）

### 2.4 与本系统的拓扑

```
用户 → frontend :5173 (AiWorkspace/HermesPanel/AgentAdviceCenter)
      → backend :8081 /v1/ai/ws/** 
      → HermesService (RestClient) ──HTTP──→ Hermes API Server 127.0.0.1:8642
Hermes 进程
  ├─ 模型调用 → ai-gateway :8090 /v1/chat/completions（OpenAI 兼容门面）
  └─ MCP 工具 → backend :8081 /api/mcp（JSON-RPC 2.0，X-API-Key + X-Acting-User-Id）
```

---

## 3. API Server（8642）全量接口

### 3.1 横切机制

| 机制 | 说明 |
|---|---|
| 实现 | `APIServerAdapter(OpenAICompatRoutesMixin, BasePlatformAdapter)`，aiohttp（`gateway/platforms/api_server.py`） |
| 路由注册 | `connect()` 3973-3978：路由表遍历注册 + **每条路由自动镜像 `/p/{profile}` 前缀版** + 兜底 ingress |
| 中间件链 | config_guard → profile_prefix → CORS → body_limit(10MB) → security_headers（3966-3969） |
| **鉴权** | `Authorization: Bearer <API_SERVER_KEY>`，hmac 常量时间比较（1389-1435）；401 envelope `{"error":{"type":"gateway_auth_error","code":"gateway_auth_failed"}}`；**key 必填且 ≥16 字符，否则拒绝启动**（3906-3935）；唯一免鉴权端点 `GET /health`；`/p/<profile>` 命名 profile 永远 fail-closed |
| **CORS** | 白名单来自 `API_SERVER_CORS_ORIGINS`（CSV，支持 `*`）；方法 `GET,POST,DELETE,OPTIONS`；头 `Authorization,Content-Type,Idempotency-Key,X-Hermes-Session-Id`；带 Origin 不在白名单直接 403；全响应带 CSP/X-Frame-Options 等（631-661） |
| **幂等** | `/v1/runs` 用 `Idempotency-Key` 头 + `runs_idempotency.db`（sqlite WAL，`BEGIN IMMEDIATE` 原子 reserve，fingerprint=全 body 哈希；同 key 同体 → 202 重放原 run_id + `Idempotency-Replayed: true`；同 key 不同体 → 409 `idempotency_key_conflict`；保留 24h）；`/v1/chat/completions` 用进程内缓存（1000 条/TTL 300s） |
| **限流** | 仅并发上限：config `gateway.api_server.max_concurrent_runs`（默认 10，0 关闭），超限 429 + `Retry-After: 1`（3553-3568） |
| **多 profile** | `/p/{profile}/v1/...`：配置/凭据/SessionDB 全隔离，用**该 profile 自己的 API_SERVER_KEY**；`hermes` 别名 = default profile（api_server_profile_prefix.py:9-46） |
| **排水** | 网关排水时新请求 503；非终态 run 盖 `shutdown_requested_at` 并协作中断为 `interrupted`（runs.py:339-365） |
| 状态保留 | SSE 传输缓冲 TTL 300s；终态状态轮询保留 3600s，60s 周期清扫（api_server.py:3841-3842） |

### 3.2 端点全量清单

#### 健康与元信息

| 方法 | 路径 | 鉴权 | 用途 | 位置 |
|---|---|---|---|---|
| GET | `/health`（`/v1/health` 同 handler） | **免** | 存活探针 → `{"status":"ok","platform":"hermes-agent","version":"..."}` | api_server.py:1555,2266 |
| GET | `/health/detailed` | Bearer | 网关状态/平台 map/读就绪/PID | 2270-2313 |
| GET | `/v1/models` | Bearer | 虚拟模型+别名列表（OpenAI 格式） | 2316-2330 |
| GET | `/api/model/options?refresh=&include_unconfigured=` | Bearer | 模型选择器目录 | 2333-2350 |
| GET | `/v1/capabilities` | Bearer | **机器可读 API 面**（feature flags + 全端点表，49-81 静态定义） | 2353-2378 |
| GET | `/v1/skills` | Bearer | 已安装 skills 列表 | 2381-2394 |
| GET | `/v1/toolsets` | Bearer | 各 toolset 启用状态 + 展开工具名 | 2397-2423 |

#### 异步运行 `/v1/runs`（核心，已验证路由表 api_server_runs.py:284-292）

| 方法 | 路径 | 用途 | 位置 |
|---|---|---|---|
| POST | `/v1/runs` | 提交 run，**立即 202 返回 run_id** | runs.py:763-896 |
| GET | `/v1/runs/{run_id}` | 轮询状态 | runs.py:1244-1248 |
| GET | `/v1/runs/{run_id}/events` | **SSE 事件流**（支持 `Last-Event-ID` 头 / `?last_seq=` 续传） | runs.py:1251-1341 |
| POST | `/v1/runs/{run_id}/approval` | 解析审批（approve/deny） | runs.py:1356-1408 |
| POST | `/v1/runs/{run_id}/clarify` | 回答澄清提问 | runs.py:1421-1429 |
| POST | `/v1/runs/{run_id}/steer` | **运行中注入引导文本** | runs.py:1437-1489 |
| POST | `/v1/runs/{run_id}/stop` | 中断（→ stopping → cancelled） | runs.py:1492-1523 |
| POST | `/v1/runs/{run_id}/resolve-unknown` | 确认失联 owner epoch | runs.py:1526-1544 |

**`POST /v1/runs` 请求字段全集**（runs.py:763-872 等）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `input` | string \| [{role,content}...] | **必填**；数组取最后一项 content |
| `session_id` | string | 绑定既有会话（加载历史、续聊）；缺省用 run_id |
| `previous_response_id` | string | 链到 /v1/responses 快照续聊 |
| `conversation_history` | [{role,content}] | 显式历史（同时给时优先于 previous_response_id） |
| `instructions` | string | 临时 system prompt |
| `model` / `provider` | string | 覆盖模型/provider（`hermes-agent` 虚拟名=网关默认） |
| `model_options` | object | `{reasoning:{enabled,effort}, reasoning_effort, service_tier, fast}` |
| `author` | object | 轮次作者（仅内存归因，不授权） |
| `hosted_room_dispatch`(+`_room_execution_policy`) | object | Hosted Room 派发（内部通道） |

> 注意：**不接受 `tools`/`tool_choice`**——工具面由服务端 toolsets/MCP 配置决定；审批也不在投递时指定，走 SSE `approval.request` 异步处理。

**响应**：`202 {"run_id":"run_<32hex>","status":"started","replayed":false}`（headers 可能带 `X-Hermes-Session-Key`）；429 限流 / 409 幂等冲突 / 503 排水。

**`GET /v1/runs/{id}` 状态对象**：`{object:"hermes.run", run_id, status, created_at, updated_at, session_id, model, runtime{provider,model,route_source}, last_event, output, error, usage{input/output/total/cache_read/cache_write/reasoning_tokens}, approval{仅 waiting_for_approval}, pending_steer, completed, partial, interrupted, turn_exit_reason}`

**状态机**：`queued → running ↔ waiting_for_approval → 终态`；终态集合 `TERMINAL_STATUSES = {completed, failed, cancelled, interrupted}`（api_server_run_idempotency.py:19）；`stopping` 为瞬时态；interrupted 映射为 cancelled 对外。**注意本系统 Java 侧 `TERMINAL` 集合写的是 `{completed, failed, cancelled, stopped, error}`，`stopped`/`error` 在 Hermes 侧不是合法终态值——虽然不影响正确性（超集），建议对齐。**

#### 会话管理 `/api/sessions`

| 方法 | 路径 | 说明 | 位置 |
|---|---|---|---|
| GET | `/api/sessions?limit=&offset=&source=&include_children=&title=` | 列表（默认 50/上限 200，`has_more`） | 2519-2571 |
| POST | `/api/sessions` | 创建空会话（可指定 id/title/system_prompt/source/model 模型锁）201 | 2573-2642 |
| GET/PATCH/DELETE | `/api/sessions/{id}` | 详情 / 改 title|pinned|archived|hidden / 硬删 | 2644-2727 |
| GET | `/api/sessions/{id}/messages?limit≤500&offset&order=oldest|latest` | 历史消息（含压缩祖先链） | 2729-2763 |
| POST | `/api/sessions/{id}/fork` | 分支会话（CLI /branch 语义） | 2765-2805 |
| POST | `/api/sessions/{id}/chat` | **同步单轮对话**（body: `message`/`input`, `system_message`, `model`, `provider`, `model_options`；响应头 `X-Hermes-Session-Id`） | 3039-3083 |
| POST | `/api/sessions/{id}/chat/stream` | SSE 单轮（事件：run.started→message.started→assistant.delta→tool.started/completed/failed→assistant.commentary→approval.request→assistant.completed→run.completed|failed|cancelled→done） | 3085-3214 |
| POST | `/api/sessions/{id}/model` | 会话级模型锁 | 3245-3274 |

session 字段白名单：`id, source, user_id, model, title, started_at, ended_at, end_reason, message_count, tool_call_count, input/output_tokens, cache_*, reasoning_tokens, estimated_cost_usd...`

#### OpenAI 兼容层

| 方法 | 路径 | 说明 | 位置 |
|---|---|---|---|
| POST | `/v1/chat/completions` | OpenAI Chat（同步+SSE）；续聊头 `X-Hermes-Session-Id`（无 key 时 403）；失败 502 error envelope + `X-Hermes-Completed/Partial/Error` 头 | openai_routes.py:865-1062 |
| POST | `/v1/responses` | OpenAI Responses API（同步+SSE）；`previous_response_id`/`conversation` 续聊；落 `response_store.db` | 1367-1504 |
| GET/DELETE | `/v1/responses/{response_id}` | 取回/删除已存 response | 1506-1529 |

`/v1/chat/completions` SSE 附加命名事件：`hermes.tool.progress`（`{tool,emoji,label,toolCallId,status:"running"|"completed"}`）、`hermes.status`、`approval.request`。
`/v1/responses` SSE：`response.created → output_item.added/done → output_text.delta/.done → reasoning_summary_* → hermes.status → response.completed|failed`。

#### Cron 任务 `/api/jobs`

| 方法 | 路径 | 说明 | 位置 |
|---|---|---|---|
| GET | `/api/jobs?include_disabled=` | 列出 | 3336-3345 |
| POST | `/api/jobs` | 创建（name/schedule 必填，prompt≤5000，deliver 默认 local，skills，repeat，paused） | 3347-3387 |
| GET/PATCH/DELETE | `/api/jobs/{job_id}` | 单查（id 必须匹配 `[a-f0-9]{12}`）/白名单字段更新/删除 | 3389-3425 |
| POST | `/api/jobs/{id}/pause` `/resume` `/run` | 暂停/恢复/立即触发（run 可带一次性 prompt） | 3427-3454 |
| POST | `/api/cron/fire` | Chronos(NAS) fire webhook——**NAS JWT 鉴权，非 API_SERVER_KEY** | 3456+ |

#### 平台事件与 Room 协作

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/platforms/{platform}/events` | 目标平台自己的 verifier | 外部平台 webhook 入口 |
| POST | `/v1/room-members/invitations` | Bearer | 为可信宿主网关铸造短时 room/profile grant（ttl 60-86400s） |
| GET/POST | `/v1/room-members/capabilities` `/grants/refresh` `/grants/revoke` | `Authorization: HermesRoom <token>` | 房间能力/刷新/吊销（api_server_room_grants.py:73-78） |

### 3.3 SSE 事件类型完整枚举（/v1/runs/{id}/events）

帧格式 `id: <seq>\nevent: <name>\ndata: <json>\n\n`；envelope `{"event", "run_id", "timestamp", "seq", ...payload}`；空闲 10s `: keepalive`；run 结束 `: stream closed` 后关闭；backlog 1000 条/慢订阅者（队列 256 溢出）断开；seq 早于保留窗先发 `replay.truncated`。

| event | payload 字段 | 本系统已消费 |
|---|---|---|
| `run.queued` | message | ✅（白名单外落库忽略） |
| `message.delta` | delta（增量文本） | ❌ 故意丢弃（噪声） |
| `message.interim` | text, already_streamed | ✅ |
| `tool.started` | tool, preview, tool_call_id? | ✅ |
| `tool.completed` | tool, duration(s), error(bool), tool_call_id?, preview(≤500 已脱敏) | ✅ |
| `reasoning.available` | text | ❌ 噪声 |
| `subagent.start` / `subagent.complete` | goal, task_count/task_index, subagent_id, child_session_id, delegation_id, parent_id, depth, model, tool_count, status, summary, duration_seconds, tokens, api_calls, cost_usd, files_read/written, output_tail（全部脱敏） | ✅ |
| `approval.request` | **command(脱敏), description(脱敏), pattern_key, pattern_keys, allow_session, allow_permanent, smart_denied?, request_id(uuid4 hex), choices** | ✅（B5 核心） |
| `approval.responded` | choice, request_id?, resolved | ⚠ 未消费（可补） |
| `run.steered` | accepted:true | ✅ |
| `run.completed` | completed, partial, interrupted, **output(最终回复)**, usage, runtime | ✅ |
| `run.failed` | 同上 + error | ✅ |
| `run.cancelled` / `run.interrupted` | error | ✅ |
| `replay.truncated` | oldest_retained_seq, requested_seq? | ❌ |

**choices 取值逻辑**（api_server.py:84-87，已抽查确认）：`smart_denied or !allow_session → ["once","deny"]`；`allow_permanent → ["once","session","always","deny"]`；否则 `["once","session","deny"]`。

### 3.4 审批机制（与 B5 对齐）

1. 危险命令 → `tools/approval.py:871-879` 构造脱敏 payload，`request_id=uuid4().hex`（approval_gateway_wait.py:32）→ run 状态置 `waiting_for_approval` + SSE `approval.request`
2. 客户端 `POST /v1/runs/{run_id}/approval`，body：`{"choice": "once"|"session"|"always"|"deny", "request_id"?, "all"/"resolve_all"?}`（别名 approve/approved/allow→once）
3. 响应 `{"object":"hermes.run.approval_response","run_id","choice","request_id"?,"resolved":<n>}` + 流上 `approval.responded`
4. 错误码：400 `invalid_approval_request/choice/scope`；409 `approval_not_active`/`approval_not_pending`；room grant 通道只允许 once|deny 且必须精确 request_id
5. 审批超时默认 `approvals.timeout`=300s——**前端审批卡片超时必须 ≥300s，否则"前端已放弃、后端仍等答案"**（桌面端 ENGINEERING.md 工程约定）

### 3.5 最小调用序列（curl 级）

```bash
BASE=http://127.0.0.1:8642; KEY=<API_SERVER_KEY>

# 1) 提交（幂等键推荐；多轮带 session_id 或 X-Hermes-Session-Key 头）
RUN=$(curl -s -X POST "$BASE/v1/runs" -H "Authorization: Bearer $KEY" \
  -H "Idempotency-Key: req-001" -H "Content-Type: application/json" \
  -d '{"input":"任务书","instructions":"临时system","model":"hermes-agent"}')
# => 202 {"run_id":"run_...","status":"started","replayed":false}

# 2a) SSE 订阅（断线可 Last-Event-ID 续传）
curl -N "$BASE/v1/runs/$RID/events" -H "Authorization: Bearer $KEY"

# 2b) 或轮询（waiting_for_approval 时读 approval.request_id）
curl -s "$BASE/v1/runs/$RID" -H "Authorization: Bearer $KEY"

# 3) 审批回调
curl -s -X POST "$BASE/v1/runs/$RID/approval" -H "Authorization: Bearer $KEY" \
  -H "Content-Type: application/json" -d '{"choice":"once","request_id":"<事件中的request_id>"}'

# 3b) 运行中改需求（可选）: POST .../steer  {"input":"跳过第3步"}
# 3c) 中断（可选）:        POST .../stop

# 4) 终态（SSE 收 run.completed/failed/cancelled/interrupted 即关；或轮询至终态，保留 1h）
```

---

## 4. 其他接入面

### 4.1 ACP 适配器（hermes-acp.exe）——编辑器协议

- **协议**：Agent Client Protocol v0.9（`agent-client-protocol==0.9.0`），stdio 上的 JSON-RPC；`use_unstable_protocol=True` 开启 v2 方法面（session/resume、list、fork、set_model）
- **启动**：客户端 spawn `hermes-acp`（等价 `hermes acp` / `python -m acp_adapter`），stdout 专用协议帧、日志走 stderr
- **双实现**：无参构造默认 `GatewayACPAgent`——ACP 进程不执行 agent，全部投影到 gateway daemon（owner 需 `acp-editor-policy-v1` 能力）
- **客户端→agent 方法**：`initialize`（返回 capabilities+authMethods）、`authenticate`、`session/new`（cwd+mcp_servers）、`session/load`（**响应前必须重放全部历史**）、`session/resume`、`session/prompt`（ContentBlock 数组，返回 stopReason+usage）、`session/cancel`、`session/set_model`、`session/set_mode`（default/accept_edits/dont_ask）、`session/set_config_option`、`session/fork`、`session/list`
- **agent→client 请求**：`session/request_permission`（审批选项 allow_once/allow_session/allow_always/deny/deny_always，超时 300s）、`elicitation/create`（clarify 表单）
- **通知流 `session/update`**：agent_message_chunk / agent_thought_chunk / tool_call / tool_call_update / plan / session_info_update / available_commands_update / usage_update / current_mode_update
- **评估**：设计给编辑器/IDE 单用户本机场景，无传输鉴权；Java 服务端嵌入不合适，不推荐

### 4.2 tui_gateway JSON-RPC/WebSocket——桌面端同款（258 方法）

- **通道**：`ws://<api_origin>/api/ws`（换行分隔 JSON-RPC）+ REST `/api/*`；桌面端本地模式经控制 socket（gateway.sock，Windows 走 Python 桥 named pipe）铸造**一次性 ticket**（WS `?ticket=` 25s 单次；REST `X-Hermes-Gateway-Ticket`）——**跨语言成本高，远程模式才适合我们**（token/OAuth + 自定义 header）
- **契约单一来源**：`tui_gateway/contracts`（Python）→ 生成 `apps/shared/src/gateway-contract.generated.ts`（7259 行）+ openrpc（258 方法）
- **方法域**：会话/输入（session.create|list|resume|history|events.since|interrupt|steer|compress|branch...）、prompt.submit|background|btw、审批（approval.pending|received|respond、server→client 请求 approval/clarify/sudo/secret）、子代理（subagent.list|roster|steer、delegation.status）、模型/工具（model.options、toolsets.list、mcp.servers.*）、cron.manage、connectors.*、billing/subscription/profiles、config.get|set、shell.exec 等
- **现成 TS 客户端**：`apps/shared/src/json-rpc-gateway.ts`（心跳/15s 超时/重连退避/断线 `session.events.since` 重放/审批超时 300s）——若未来要做"全功能 Agent 管理面板"，这是比 API Server 能力更全的选项

### 4.3 Gateway 平台通道 / webhook / cron / Portal

- **内置平台 23 种**（gateway/config.py:217-242）：local, telegram, discord, whatsapp(_cloud), slack, signal, mattermost, matrix, email, sms, **dingtalk, feishu, wecom(_callback), weixin, qqbot**, api_server, webhook, msgraph_webhook, bluebubbles, yuanbao, relay + 插件扩展（teams/line/irc/google_chat/ntfy/simplex 等）
- **平台端口表**：webhook 8644、api_server 8642、msgraph_webhook 8646、feishu 8765、wecom_callback/bluebubbles 8645、sms 8080、whatsapp_cloud 8090、line 8646、teams 3978
- **通道抽象**：`BasePlatformAdapter`（connect/disconnect/send/get_chat_info + 能力标志），统一 `MessageEvent` → run_inbound 钩子链（鉴权/pairing/busy-steer）→ turn → delivery 回投
- **webhook**：HMAC 签名 POST（GitHub/GitLab/Svix/Linear/generic），V2 带时间戳防重放；`hermes webhook add/list/remove/test` 动态路由（`/webhooks/<name>`，热加载）；可配置 events 过滤→prompt 模板→deliver 到任意平台→cron_job/mirror_to_session
- **cron 投递**：deliver=origin/home/explicit/bot-chat 四类；api_server 永不为 deliver 目标（不支持异步投递）
- **Nous Portal**：账号/订阅/计费门户 + Tool Gateway（web 搜索/图像生成/TTS/cloud browser 按订阅路由），`hermes setup --portal` OAuth；**与 UI 接入无关**，仅在选 Nous 订阅做模型供给时相关

### 4.4 社区 WebUI 对比（结论：均不需要引入）

| 项目 | 技术 | 连接方式 | 评价 |
|---|---|---|---|
| nesquena/hermes-webui | Python+vitepress vanilla JS，无构建 | 默认 8787 自带 legacy runtime；可 `HERMES_WEBUI_CHAT_BACKEND=gateway` 桥接 8742 | 功能最全（会话/工作区/cron/记忆面板），SSE 审批卡，5303 测试；但我们是自研 Vue 系统，只可参考其交互模式 |
| 0xPier/Hermes-Agent-UI | FastAPI+WS spawn CLI + React/Vite | **不走 8642**，直接驱动 CLI | 名字最接近 "hermes-agent-ui"，但低活跃（6 star） |
| MNPickle/hermes-webui | 本地浏览器 UI | — | 参考 |

---

## 5. 本项目集成现状与差距（Gap Analysis）

### 5.1 已消费端点（backend HermesService.java）

| # | 端点 | 用途 | 现状 |
|---|---|---|---|
| 1 | GET /health | 可用性探测（30s 缓存） | ✅ 生产 |
| 2 | POST /v1/runs | 投递委托（body 仅 `{input}`） | ✅ 生产（**未用 session_id/instructions/model_options/Idempotency-Key**） |
| 3 | GET /v1/runs/{id} | 轮询（poll-interval 2s / await-timeout 600s） | ✅ 生产 |
| 4 | GET /v1/runs/{id}/events | SSE 事件落库（11 类型白名单，seq 幂等） | ✅ 生产（**无断线重连/Last-Event-ID**） |
| 5 | POST /v1/runs/{id}/stop | 超时兜底取消 | ✅ 生产 |
| 6 | POST /v1/runs/{id}/approval | B5 审批回调（choice once/deny） | ✅ 生产 |

反向暴露：backend `/api/mcp`（initialize/ping/tools/list/tools/call 四方法，9 手写核心工具 + ~19 只读桥接 + 11 审批化写入工具）。

### 5.2 未消费能力清单（按嵌入价值排序）

| 能力 | 端点/机制 | 嵌入价值 | 建议优先级 |
|---|---|---|---|
| **运行中引导（steer）** | POST /v1/runs/{id}/steer | 用户在对话里补充要求时不必等 run 跑完再重来；`run.steered` 事件已在落库白名单 | **P0** |
| **Idempotency-Key** | POST /v1/runs 请求头 | 网络重试防重复委托（Hermes 有 durable 幂等库，接上即用） | **P0** |
| **会话绑定** | POST /v1/runs 的 `session_id` / `X-Hermes-Session-Key` 头 | 同一用户跨委托共享 Hermes 上下文/长期记忆作用域；当前每次委托都是无状态单 run | **P0** |
| **临时 system prompt** | `instructions` 字段 | 把部门 SOP/角色约束注入委托任务书，替代拼进 input 文本 | **P1** |
| **模型/推理档位** | `model`/`provider`/`model_options{reasoning.effort}` | 按任务复杂度选档（配 ai-gateway 的 glm-4.6 已够用，暂缓） | P2 |
| **SSE 断线续传** | `Last-Event-ID` 头 / `?last_seq=`；`replay.truncated` 事件 | 事件流 30s 读超时后可续传，减少丢事件（当前靠 DB 唯一键兜底重放） | **P1** |
| **approval.responded 事件** | SSE | 审批处理结果回执落库，面板可显示"已放行/已拒绝" | **P1** |
| **澄清问答（clarify）** | POST /v1/runs/{id}/clarify + clarify.request | Hermes 不确定时反问用户（authority 模式）；当前未启用 | P2 |
| **能力自描述** | GET /v1/capabilities | 前端按能力渐进启用，替代硬编码 | P2 |
| **会话管理族** | /api/sessions CRUD + messages + fork + model | 做独立 "Hermes 会话浏览器"（看 agent 内部视角历史/成本） | P2 |
| **定时任务托管** | /api/jobs CRUD + 立即触发 | 把部门晨报/巡检类任务交给 Hermes cron，结果回投 | P2 |
| **subagent 可观测** | subagent.start/complete 全字段（delegation_id/depth/cost_usd/child_session_id） | 委托时间线可展示子任务树与成本 | P1（字段已在 SSE 里，落库时只取了 preview，扩展成本低） |
| **OpenAI 兼容对话** | /v1/chat/completions（stream）+ X-Hermes-Session-Id | 若要"Hermes 原生对话"而非"委托"模式可用；当前走 ai-gateway 更合适 | 备选 |

**已知问题修正项**：
1. Java `TERMINAL` 集合含 `stopped`/`error`（Hermes 无此终态）——建议改为 `{completed, failed, cancelled, interrupted}` 并把轮询侧 interrupted→cancelled 映射对齐
2. `X-Acting-User-Id` 双向均为静态 "1"：Hermes→backend 方向来自 config.yaml；backend→Hermes 方向**完全没有身份头**（身份只编码在任务书文本里）——接口文档标注"B 阶段动态携带"属规划未落地
3. 任务书写死"27 个业务工具"与注册表实际数量不符（陈旧文案）
4. `hermes_run_event` 无清理策略（runs_idempotency 侧 Hermes 自己 24h 清理，我们的表会持续增长）
5. SSE 消费独立线程 + 轮询线程双线程架构正确（B5 修复），但 awaitRun 同步语义遗留仅测试在用，可择机删除

---

## 6. 嵌入建议（行动清单）

**主通道维持不变**：API Server（8642）+ Java BFF + SSE/轮询混合。理由：语言无关、Bearer 鉴权、durable FIFO（owner 重启 exactly-once 恢复，`session_api_turn.py:280-312`）、run 幂等/SSE/steer/stop 闭环齐全、B1-B5 已验证。

**P0（建议下一迭代）**
1. `/v1/runs` 投递带上 `Idempotency-Key`（用业务委托 ID）+ `session_id`（按用户/会话维度映射 Hermes session）+ `instructions`（角色 SOP）
2. Java `TERMINAL` 终态集合对齐 Hermes 真实枚举
3. steer 透传：对话消息在 run 运行中到达时调 `/steer` 而非排队新委托

**P1**
4. SSE 消费加 `Last-Event-ID` 续传与有限次重连退避（参考桌面端 reconnect-backoff 模式）
5. `approval.responded` 事件落库 → 前端审批状态闭环显示
6. subagent.start/complete 结构化落库（新表或 JSON 字段），时间线展示子任务树/成本
7. 审批卡片前端超时 ≥300s（对齐 `approvals.timeout`）

**P2（按需）**
8. `/v1/capabilities` 驱动前端能力开关；clarify 澄清问答启用；/api/jobs 把晨报/巡检交给 Hermes cron；会话浏览器（/api/sessions）

**长期选项**：若需要"全功能 Agent 管理面板"（profiles/cron/connectors/subagent roster/billing/skills/MCP 管理，即桌面端同款 258 方法），走 tui_gateway WebSocket JSON-RPC + 参考 `apps/shared/src/json-rpc-gateway.ts` 客户端实现；当前阶段不必。

---

## 附录：参考来源

**本地源码（file:line 级依据，源码根 `C:\Users\sunzh\AppData\Local\hermes\hermes-agent`）**
- `gateway/platforms/api_server.py` — 路由注册 3973-3978、路由表 1551-1593、鉴权 1389-1435、CORS 631-661、审批事件 84-102、sessions 2519-3274、jobs 3336-3465、并发 3553-3568、启动守卫 3906-3935
- `gateway/platforms/api_server_runs.py` — /v1/runs 全部 handler（路由表 284-292）、SSE 1251-1341、状态机 229-248、steer/stop/resolve-unknown
- `gateway/platforms/api_server_run_idempotency.py` — durable 幂等（TERMINAL_STATUSES:19）
- `gateway/platforms/api_server_openai_routes.py` — /v1/chat/completions、/v1/responses
- `gateway/platforms/api_server_room_grants.py`、`api_server_profile_prefix.py`、`gateway/session_api_turn.py`、`gateway/run_shutdown.py`
- `tools/approval.py`（871-879）、`tools/approval_gateway_wait.py`（request_id:32）
- `acp_adapter/`（server.py、gateway_server.py、events.py、permissions.py、edit_approval.py、elicitation.py）
- `apps/desktop/`（README/DESIGN/ENGINEERING、electron/local-gateway.ts ticket 体系 332-680）
- `apps/shared/src/json-rpc-gateway.ts`、`gateway-contract.generated.ts`（258 方法契约）
- `gateway/config.py`（217-242 平台枚举）、`hermes_cli/web_server_gateway.py`（43-49 端口表）

**本项目代码**
- `backend/.../common/client/HermesService.java`（6 端点调用）、`HermesDelegateService.java`（委托/轮询/事件落库）、`DecisionAdviceService.java`（审批闭环）
- `backend/src/main/resources/application.yml`（hermes 配置 96-101）、`database/11_hermes_run_event.sql`
- `frontend/src/views/ai/components/HermesCard.vue`、`frontend/src/views/ai/HermesPanel.vue`、`AgentAdviceCenter.vue`
- `docs/阶段一接口说明文档.md`（config.yaml 接线实测定稿）

**网络来源**
- [NousResearch/hermes-agent](https://github.com/nousresearch/hermes-agent) ｜ [Hermes Agent Docs](https://hermes-agent.nousresearch.com/docs) ｜ [官网](https://hermes-agent.nousresearch.com)
- [0xPier/Hermes-Agent-UI](https://github.com/0xPier/Hermes-Agent-UI) ｜ [nesquena/hermes-webui](https://github.com/nesquena/hermes-webui) ｜ [cnb.cool/hermesagent-cn](https://cnb.cool/hermesagent-cn)（hermes-agent-cn-mirror + hermes-webui-cn-mirror）
- 社区讨论：[Best webui for Hermes (Reddit)](https://www.reddit.com/r/hermesagent/comments/1ts21bm/best_webui_for_hermes) ｜ [issue #501](https://github.com/NousResearch/hermes-agent/issues/501)
