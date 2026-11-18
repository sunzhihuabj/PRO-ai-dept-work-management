# Hermes Studio · Hermes Agent 全功能 Web 控制台

自研的 Hermes Agent 全功能前端服务（BFF + Web 控制台）。架构思路参考 Ekko Studio（原 Hermes Studio / hermes-agent-ui，Vue3 前端 + Node BFF 代理 Hermes 网关），**代码为部门全新实现**（上游为 BSL-1.1 许可，未复制任何代码）。

## 链路拓扑

```
浏览器 → Hermes Studio BFF (Express :8650，登录鉴权 + 工作流存储 + 静态托管)
        → 透传代理（SSE 直通）→ Hermes API Server :8642（hermes gateway run 内嵌）
Hermes Agent → 模型：部门 ai-gateway :8090（OpenAI 兼容）
Hermes Agent → 工具：部门 backend :8081 /api/mcp（MCP + 审批化写工具）
```

- Hermes API Key 只存在服务端 `.env`，浏览器永不见
- 鉴权：Studio HMAC 令牌（24h），账号在 `server/.env`（默认 `admin / dept-studio-2026`）

## 功能页面（覆盖 8642 全量 API 面）

| 页面 | 路由 | 能力 |
|---|---|---|
| 工作台 | `/dashboard` | health/detailed、能力面网格、模型/会话/工具集统计、最近会话 |
| 聊天控制台 | `/chat` | 会话侧栏、POST /api/sessions/{id}/chat/stream 流式对话、工具事件芯片、**approval.request 审批卡**、Markdown 渲染 |
| 会话管理 | `/sessions` | 列表/按来源与标题过滤/消息抽屉/改名/分支 fork/模型锁/硬删除 |
| 委托任务 | `/runs` | POST /v1/runs（instructions/session_id/推理档位/Idempotency-Key）+ SSE 事件时间线 + 3s 轮询兜底 + **steer / stop / 审批** + 接管已有 run |
| 审批中心 | `/approvals` | 跨页审批池聚合（chat/run/workflow 来源）+ 手动接管 run_id + once/session/always/deny |
| 可视化工作流 | `/workflows` | AI 执行步骤（/v1/runs）+ 审批门（暂停等人工）+ 延时 的顺序编排，JSON 持久化（Studio 自有存储），运行日志 |
| 定时任务 | `/cron` | /api/jobs CRUD + 暂停/恢复/立即触发；**jobs_admin=false 时自动降级提示**（本机 0.22.5 实测为 false） |
| 模型与技能 | `/models` | /v1/models、/api/model/options（含未配置/强制刷新）、/v1/toolsets、/v1/skills |
| 设置 | `/settings` | 连接拓扑、服务端配置（key 脱敏）、运行要点备忘 |

## 三感能力（2026-11-18 [HS2]）

| 感官 | 实现 | 说明 |
|---|---|---|
| 👁 眼睛 | 聊天框上传/粘贴/拖拽图片 → canvas 降采样（≤1568px）→ Hermes 多模态部件 `{type:"image_url",image_url:{url:"data:image/..."}}` | 形状为 Hermes 原生 OpenAI vision 契约（api_server.py:_normalize_image_part）；E2E T11 实证 agent 收图后自动调用 terminal 工具分析 |
| 👂 耳朵 | 麦克风按钮 → Web Speech API（webkitSpeechRecognition，zh-CN，连续+中间结果） | Chrome/Edge 可用；不支持时按钮自动禁用；麦克风权限需浏览器允许 |
| 👄 嘴巴 | 每条回复 🔊 朗读按钮 + 全局"自动播报"开关（localStorage 持久化）→ SpeechSynthesis（zh-CN 声音优先，Markdown 剥离后朗读） | 无声音包环境自动降级提示；朗读中再点即停 |

注：模型"真视觉"取决于通道模型能力（当前 Qwen3.5-122B 走文字路径，图片经 Hermes 存档并触发工具分析）；切换 VL 模型通道后即获完整图像理解。

## 快速开始

```bash
cd hermes-studio
npm install

# 开发模式（BFF 8650 + Vite 8651 热更）
npm run dev

# 生产模式（构建 + BFF 同端口托管）
npm run build
npm start          # http://127.0.0.1:8650
```

前置：Hermes 网关在线（计划任务 `DepSvc-svc-hermes`，8642），`.env` 配置见 `.env.example`。

## 生产部署（服务化）

```bash
# 1) 构建产物
cd hermes-studio && npm install && npm run build

# 2) 注册开机自启计划任务（或重跑 restore-services.ps1 一键恢复全家桶）
powershell -ExecutionPolicy Bypass -File ..\deploy\restore-services.ps1
# → 注册并启动 DepSvc-svc-studio（svc-studio.cmd，日志 deploy/logs/studio-svc.log）
```

- 探活：`GET http://127.0.0.1:8650/api/studio/health`（公开，含 hermesOk 上游状态）
- 静态资源带 hash 长缓存（immutable 7d），index.html no-cache 发版即生效
- 优雅停机：SIGTERM/SIGINT 先关监听再退出（计划任务 stop 安全）
- 端口全景：3307 MySQL / 8081 backend / 8090 ai-gateway / 5173 部门前端 / 8642 Hermes / **8650 Studio** / 8651 Vite dev

## 测试

```bash
node scripts/e2e.mjs --fast   # 快筛（无 LLM 依赖，<10s）
node scripts/e2e.mjs          # 全量（含真实聊天/委托/审批流，数分钟）
# 报告输出 reports/hermes-studio/e2e-<时间戳>.md
```

## 全链路冒烟

```bash
node scripts/smoke.mjs
# 登录 → 建会话 → SSE 流式聊天（真实模型）→ 清理；学习成本：会话标题唯一、POST /api/sessions 嵌套 session.id
```

## 已知契约要点（源码级调研，详见 docs/Hermes全量接口与嵌入深度调研报告.md）

- `/v1/runs` 终态：`completed / failed / cancelled / interrupted`；状态保留 1h，SSE backlog 1000 条、断线可用 `Last-Event-ID` 续传（RunsView 已接入 sseReconnect）
- `Idempotency-Key` durable 幂等 24h：同 key 同体 202 重放原 run，异体 409
- 审批 `choices` 由 Hermes 按场景下发（once/session/always/deny 子集），超时 300s
- POST /api/sessions **标题唯一**（重复报 `invalid_title`），响应嵌套 `session.id`
- **chat/stream 事件名只在 SSE `event:` 行**（data JSON 无 event 字段，与 /v1/runs 不同）——解析需双源
- 能力自描述：`GET /v1/capabilities` 驱动前端降级（jobs_admin / audio_api / memory_write_api 等）
- 上游已知缺陷：`GET /v1/skills` 在 v0.22.5 镜像 500（`include_editorial` 参数不匹配，上游 bug），Models 页已容错

## 排障记录（开发期实录）

| 编号 | 问题 | 根因 | 修复 |
|---|---|---|---|
| F9 | Hermes 所有委托 run.failed（Clean EOF 截断 4 轮续写） | ai-gateway 仿真流式缺 `finish_reason:stop` 终止块，Hermes 判定流截断 | OpenAiCompatController 补 stopFrame（重编译生效） |
| F10 | 浏览器聊天 SSE 403（curl/API 测试全过） | BFF 转发浏览器 `Origin` 头 → Hermes CORS 白名单拒绝 | 代理层剥离 origin/referer/sec-fetch-* |
| 环境修复 | 模型通道 100% 空响应（response_chars=0） | NVIDIA glm-5.3-flash 夜间劣化波（软失败无法 failover） | 启用 moda(ModelScope Qwen3.5-122B) 为临时主通道 pri=10；恢复指令：`UPDATE sys_ai_provider_config SET enabled=0, priority=50 WHERE id=14` |

## 扩展方向

- SSE 断线续传（Last-Event-ID）+ 重连退避（参考 apps/shared json-rpc-gateway 模式）
- subagent 事件结构化展示（subagent.start/complete 已在 SSE 白名单）
- 需要桌面级全功能（profiles/connectors/billing 258 方法）时接 tui_gateway WS `/api/ws`
