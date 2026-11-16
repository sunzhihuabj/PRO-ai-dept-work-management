# AI Agent 能力升级调研报告（v1.0）

| 项目 | 内容 |
| --- | --- |
| 调研日期 | 2026-11-16 |
| 调研范围 | 联网数据搜集 / 动态技能包 / 第三方 API 接入 / 竞品对标（豆包+WorkBuddy） |
| 技术基线 | Java 17 + Spring Boot 3.4 + Vue3 + MyBatis-Plus + MySQL + Redis(预留) + ai-gateway(8090) |
| 约束条件 | LLM 调用 ≤35 次/分钟 · 中国大陆部署 · 私有化自托管 · 国产化组件优先 |
| 交付结论 | 技术选型清单 + 分三阶段落地计划 + 测试验证标准 + 稳定性保障方案 |

---

## 一、本项目 AI Agent 现状盘点

### 1.1 架构总览

本项目 AI Agent 为**单体后端 + 独立 ai-gateway 的双层架构**：

```
┌─────────────────────────────────────────────────────────┐
│  Vue3 前端 (5173)                                        │
│  └─ AI 工作台对话 → POST /conversations/{id}/chat       │
└────────────────────┬────────────────────────────────────┘
                     │ Bearer Token
┌────────────────────▼────────────────────────────────────┐
│  Spring Boot 3.4 后端 (8081)                             │
│  ├─ AiChatService：消息落库 → 三级路由                   │
│  │   ├─ 流程意图正则匹配                                 │
│  │   ├─ SystemOpsService：LLM JSON 契约 → 工具调用      │
│  │   └─ 通用对话：CAP_DOC_SUMMARY 通道                   │
│  ├─ OpsToolRegistry：@PostConstruct 注册 13 版块 33 工具 │
│  │   每个工具含 name/params/minRole/executor/precheck    │
│  ├─ CapabilityHandler：27 个能力点 (AGT-01~27)           │
│  └─ AiGatewayClient：chat / embed 两接口 → 8090          │
└────────────────────┬────────────────────────────────────┘
                     │ HTTP
┌────────────────────▼────────────────────────────────────┐
│  ai-gateway (8090)                                       │
│  ├─ Provider 三实现：OpenAI兼容商用 / Ollama 本地 / Mock │
│  ├─ RateLimiter(RPM) · 重试+JSON 契约重生成 · 全链降级  │
│  ├─ AES-GCM 密钥加密 · 响应脱敏                         │
│  └─ 模型商经 sys_ai_provider_config 动态配置             │
└─────────────────────────────────────────────────────────┘
```

### 1.2 三项升级的可复用基础

| 升级方向 | 已有基础 | 明显缺口 |
| --- | --- | --- |
| **联网搜集** | 定向爬虫全套治理原语（会话/限频/重试/断点/去重/审计）；AGT-27 任务链路；OpsTool 触发通道 | 无通用网页搜索/搜索引擎 API 接入；采集仅限预配置平台 |
| **动态技能包** | CapabilityHandler SPI 注册范式；sys_capability 表；OpsSkillCards 版块卡；JSON 契约网关校验 | 新增能力需写 Java 实现并重启；无热加载/DSL；无沙箱隔离 |
| **第三方 API** | AiGatewayClient 统一外呼范式（超时/错误码收敛/脱敏）；tender_platform 连接器配置表模式可泛化；CryptoService AES-256-GCM | 无统一第三方 API 网关层；凭据管理散落；无签名/限流/重试组件 |

### 1.3 其他架构缺口

- 意图识别为**规则关键词版**，无 LLM planning/多轮工具调用循环（SystemOpsService 仅单轮 decide）
- 无 Redis（队列用 DB 轮询 2s + 乐观锁，TaskExecutor 注明预留）
- 无多模态与流式输出
- agent_capability_policy 实体映射表缺 DDL

---

## 二、联网数据搜集能力方案

### 2.1 总体架构

```
用户指令 → Agent Planner(LLM) → 路由分发
  │
  ├─ [检索类] WebSearchConnector
  │   ├─ 主选：博查 Bocha API (api.bocha.cn)
  │   ├─ 备选：SearXNG 自托管聚合
  │   └─ 统一 SearchResult DTO (title/url/snippet/pubDate/authorityLevel)
  │
  ├─ [爬取类] WebFetchConnector
  │   ├─ 静态：Jsoup（复用现有）
  │   ├─ 动态：自托管 Firecrawl（Docker Compose, 最低 2C4G）
  │   └─ Jina Reader 自托管镜像（ghcr.io/jina-ai/reader:oss）
  │
  ├─ [聚合校验] AggregationService
  │   ├─ SimHash 64位 近重复去重（阈值 3 汉明距离）
  │   ├─ JSON Schema strict 结构化提取
  │   ├─ 来源可信度分级：央媒/官网>P1 行业站>P2 转载
  │   └─ 加权多数投票交叉校验 → 冲突标注"待人工确认"
  │
  └─ [合规过滤] ContentGuard
      ├─ SSRF 防护：IP 白名单解析 + 内网段屏蔽 + 重定向逐跳校验
      ├─ 关键词库 + 分类模型敏感内容过滤
      └─ robots.txt 遵循 + ToS 合规声明
```

### 2.2 技术选型清单

| 能力 | 选型 | 选型理由 | 风险与约束 |
| --- | --- | --- | --- |
| **检索 API 主** | 博查 Bocha `api.bocha.cn` | 国内直连免代理；中文检索准确率 95%+；亚秒响应；与 35 次/分 LLM 限流下"低频高质量检索"匹配；已试点国央企私有化部署 [P1] | 商业化服务，需评估数据出域合规 |
| **检索 API 备** | 自托管 SearXNG | 零 API 费用；聚合 261+ 引擎 JSON API；数据中心 IP 常被 Google/DDG 验证码拦截 | 海外引擎不稳定，需国内引擎白名单配置 |
| **静态抓取** | Jsoup（复用） | 已在用，成熟稳定 | 无 |
| **动态渲染** | 自托管 Firecrawl（AGPL-3.0） | 内置 robots.txt 遵循、转 Markdown、支持 scrape/crawl/map/search | AGPL 许可需注意；最低 2C4G |
| **页面转 MD** | Jina Reader OSS 镜像 | Apache-2.0；托管版 r.jina.ai 免费 20 RPM | 自托管需评估 |
| **结构化提取** | JSON Schema strict 约束解码 | 国产模型（Qwen/GLM/DeepSeek/Kimi）均支持，减少重试 | 需维护 schema 版本 |
| **近重复去重** | SimHash 64 位 + 汉明距离 ≤3 | Google/Manku 经典算法；在线去重 O(1) | 语义相似但文本差异大的可能漏判 |
| **精确去重** | URL 规范化 + 业务字段（项目名+单位）哈希 | 精确匹配，零误判 | 不适用语义近重复 |
| **SSR 防护** | IP 段黑名单 + 白名单 pin 连接 | OWASP 推荐；屏蔽 127/8、10/8、172.16/12、192.168/16、169.254/16、100.64/10 及 IPv6 映射 [P0] | 需维护内网段配置表 |
| **合规过滤** | 关键词库 + TC260-003 分级分类 | 符合《生成式AI服务管理暂行办法》第七条要求 [P1] | 关键词库需持续运营 |

### 2.3 性能指标

| 指标 | 目标值 | 说明 |
| --- | --- | --- |
| 检索 P50 延迟 | ≤1.5s | Bocha API 实测 ~0.15s，含聚合处理预算 |
| 检索 P95 延迟 | ≤4s | 含超时降级到备选引擎 |
| 抓取 P95 延迟 | ≤3.4s | Firecrawl 自报；动态页面 ≤8s |
| Agent 全链路 P95 | ≤10s | 检索+抓取+提取+聚合 |
| 并发爬取 | 单站 1-2 QPS + 随机抖动 | 遵循 robots.txt 与反爬策略 |
| Top-K 结果 | 默认 10 条 | 实验 top-5 即达 99% 答题准确率 [P1] |

### 2.4 网络环境适配

| 环境 | 策略 |
| --- | --- |
| 国内专线 | Bocha 直连；SearXNG 聚合国内引擎 |
| 有代理出口 | 海外 API（Tavily/Exa/Serper）统一走 HTTP 代理出口 + 健康探测主备切换 |
| 内网离线 | 降级为 SearXNG 自托管 + 已缓存语料库 |
| 弱网 | 指数退避重试（1s/2s/4s + 抖动）+ 本地缓存命中优先 |

---

## 三、动态技能包安装能力方案

### 3.1 分层架构

```
┌─────────────────────────────────────────────────────┐
│  SkillHub（技能仓库）                                │
│  ├─ 存储：Nexus 私服 (Maven artifact + cosign 签名) │
│  ├─ 元数据：skill.yaml (id/semver/permissions/deps) │
│  └─ 分发：Maven-style 坐标 groupId:artifactId:ver  │
└──────────────────┬──────────────────────────────────┘
                   │ 下载+验签
┌──────────────────▼──────────────────────────────────┐
│  SkillManager（运行时管理器）                        │
│  ├─ 安装：下载 → 签名校验 → ClassLoader 创建 → 注册 │
│  ├─ 卸载：stop() → 资源回收 → ClassLoader 关闭      │
│  ├─ 升级：semver 版本比对 → 灰度发布 → 回滚         │
│  └─ 热加载：Spring 上下文隔离 + 事件总线通知         │
└──────────────────┬──────────────────────────────────┘
                   │ 执行（按信任等级分层）
┌──────────────────▼──────────────────────────────────┐
│  执行分层                                            │
│  ├─ 可信内部技能：PF4J PluginClassLoader → 直映     │
│  │   CapabilityHandler（API jar parent-first）       │
│  ├─ 不可信三方技能：                                 │
│  │   ├─ Windows：低权限 Job Object + 网络代理白名单  │
│  │   ├─ Linux：bubblewrap 或 systemd DynamicUser   │
│  │   └─ 最严：Chicory WASM 线性内存隔离              │
└─────────────────────────────────────────────────────┘
```

### 3.2 技能包封装格式

```
skill.zip
├── SKILL.md              # Anthropic Agent Skills 兼容元数据
│   └── YAML frontmatter: name, description, allowed-tools
├── skill.yaml            # 本项目扩展元数据
│   ├── id: com.company.skill.example
│   ├── version: 1.0.0        (semver)
│   ├── permissions:          # 声明式权限清单
│   │   ├── fs: [read:/data/, write:/tmp/out]
│   │   ├── network: [allow:bocha.cn, deny:*]
│   │   ├── exec: [deny:*]
│   │   └── db: [read:sys_user, write:sys_log]
│   ├── deps:                 # 依赖清单
│   │   └── com.example:lib:2.0.0
│   ├── runtime: java17 | wasm | native
│   └── trust: internal | thirdparty | untrusted
├── lib/                    # 自带依赖（shade relocation 隔离）
├── META-INF/
│   └── MANIFEST.MF         # 含 jarsigner/cosign 签名
└── src/                    # 可选：WASM 源或原生代码
```

### 3.3 技术选型清单

| 能力 | 选型 | 选型理由 | 风险与约束 |
| --- | --- | --- | --- |
| **技能格式** | SKILL.md + skill.yaml | 兼容 agentskills.io 生态（70+ 平台采纳），可复用 | 需扩展 YAML schema |
| **Java 动态加载** | PF4J 3.15 + pf4j-spring 0.10 | Spring Boot 3 最轻量成熟路线；独立 PluginClassLoader；生命周期完善 | DevTools 类加载器冲突；卸载需注意 Metaspace 泄漏 |
| **沙箱隔离** | 分层策略（见下表） | 内部技能走 PF4J、三方技能走 OS/进程级隔离 | PF4J 非安全边界，恶意 jar 可调用宿主 API |
| **WASM 备选** | Chicory（纯 Java WASM 运行时） | 双重沙箱（WASM 线性内存+JVM）；无 JNI；官方定位插件系统 | WASM 桥接有一次性成本 |
| **依赖解析** | 技能包自带 lib/ + shade relocation | 避免运行时联网；版本冲突零感知 | fat-jar 体积大；Apache Pinot 2026 年弃用 fat-jar 改 thin-jar [P1] |
| **签名校验** | cosign + jarsigner 双通道 | cosign 按 digest 拉取防 rug pull；jarsigner JAR 签名 | 需建立证书管理流程 |
| **版本管理** | semver + Maven-style release 不可变 | Maven Central 强制策略可作蓝本 | 需建立私服发布规范 |
| **权限分级** | 声明式清单 + requireConfirm 对齐 | 未声明即拒绝；高危操作二次确认 | 需建立权限清单模板库 |
| **安全审计** | 全链路留痕（安装验签/执行/网络/文件） | sys_oper_log 扩展 | 日志体积增长 |
| **恶意防护** | 描述哈希 pinning + 变更重审批 + 按服务器隔离 | MCPTox 测 45 个 MCP 服务器攻击成功率 72.8%，需防御 [P0] | 描述哈希需持续维护 |

### 3.4 沙箱隔离分层策略

| 信任等级 | 适用场景 | 隔离方案 | 安全强度 |
| --- | --- | --- | --- |
| **internal** | 本团队开发的业务技能 | PF4J 独立 ClassLoader + API jar parent-first | 低（防误用不防恶意） |
| **thirdparty** | 第三方可信合作伙伴技能 | Windows Job Object 低权限 + 网络代理白名单 + 文件系统隔离 | 中 |
| **untrusted** | 公开市场下载的未知技能 | Chicory WASM 线性内存隔离 + 子进程 OS 沙箱 | 高 |

### 3.5 安全风险要点

- **Tool Poisoning**：2025-04 Invariant Labs 命名攻击；2025-09 postmark-mcp 在 1.0.16 版加 BCC 外传 [P1]
- **MCP Top 10**：OWASP MCP03 工具描述投毒为当前最高频威胁 [P0]
- **缓解措施**：描述哈希 pinning、变更重审批、按服务器隔离、网关审计、技能包签名校验

---

## 四、第三方软件 API 接入能力方案

### 4.1 总体架构

```
┌──────────────────────────────────────────────────────────┐
│  Connector 统一抽象层                                     │
│  ├─ ConnectorManifest：id/name/version/authType/rateLimit │
│  ├─ Authenticator：HMAC-SHA256 / OAuth2 / JWT / 自定义   │
│  ├─ Signer：钉钉 / 飞书 / 企微 签名适配器                  │
│  ├─ RateLimiter：Resilience4j 令牌桶 + 429 感知重试       │
│  ├─ RetryPolicy：指数退避+抖动（≤3次，仅幂等错误）        │
│  ├─ ResponseMapper：JSON Schema 校验 + 字段归一化          │
│  └─ TimeBudget：Resilience4j TimeLimiter + 降级策略      │
└──────────────────┬───────────────────────────────────────┘
                   │ MCP 兼容
┌──────────────────▼───────────────────────────────────────┐
│  双通道架构                                               │
│  ├─ 对外：MCP Server（Spring AI @McpTool 注解）            │
│  │   → 第三方 API 自动注册为 MCP 工具 → LLM 自动选择调用  │
│  └─ 对内：MCP Client + 原生 HTTP 调用                    │
│      → 接 Coze/Dify/n8n 生态 + 自有业务系统               │
└──────────────────┬───────────────────────────────────────┘
                   │ 凭据存储
┌──────────────────▼───────────────────────────────────────┐
│  SecretManager                                           │
│  ├─ 云上：阿里云 KMS 信封加密（GenerateDataKey 本地加密）  │
│  ├─ 私有化：Jasypt + PBEWithHMACSHA512AndAES_256          │
│  ├─ 密钥轮换：1h~365d 自动 + 双密钥过渡                  │
│  └─ 日志脱敏：正则屏蔽 Bearer/密码                       │
└──────────────────────────────────────────────────────────┘
```

### 4.2 技术选型清单

| 能力 | 选型 | 选型理由 | 风险与约束 |
| --- | --- | --- | --- |
| **协议标准** | MCP 2026-07-28 规范 | 事实标准；Spring AI 官方维护 SDK；Streamable HTTP 取代 SSE [P0] | 规范迭代快，需跟进更新 |
| **MCP Java SDK** | Spring AI MCP Boot Starters | @McpTool 注解自动生成工具 schema；Streamable HTTP 支持 | 依赖 Spring AI 版本 |
| **密钥加密** | Jasypt + AES-GCM（私有化）/ 阿里云 KMS（云上） | 与现有 CryptoService 一致；Jasypt 支持环境变量主密码 | 主密码管理需流程保障 |
| **签名校验** | 适配器模式（钉钉/飞书/企微/通用 HMAC） | 各平台签名规范不同，适配器解耦 | 需维护签名适配矩阵 |
| **重试限流** | Resilience4j 2.x (resilience4j-spring-boot3) | Retry(ExponentialRandomBackoff) + CircuitBreaker(50%) + RateLimiter(令牌桶) [P1] | 需按各平台 QPS 预配置 |
| **429 处理** | 读取 Retry-After / x-ogw-ratelimit-reset 头 | 飞书/钉钉均返回限流时间头 [P0] | 需平台适配器逐平台实现 |
| **结果解析** | OpenAPI → LLM Tool Schema 自动转换 | Haystack OpenAPIServiceToFunctions / openapi-llm [P0] | 大响应需分页透传 |
| **超时隔离** | Resilience4j TimeLimiter + Bulkhead 线程池 | 第三方调用不阻塞主流程 | 线程池资源评估 |
| **回调处理** | 立即 ACK + 异步队列 | 企微 5s/飞书 1s 超时要求 [P0] | 需保证消息幂等（event_id） |

### 4.3 国内主流平台接入适配

| 平台 | 签名规范 | 限流规则 | 接入要点 |
| --- | --- | --- | --- |
| **钉钉** | HmacSHA256(timestamp+"\n"+secret)→Base64；验签时差<1h | 应用 QPS 20/60(AI版)；IP 维度 20s/1万次封 5min | accessToken 7200s 缓存+提前刷新 |
| **飞书** | SHA256(timestamp+nonce+encrypt_key+body)；X-Lark-Signature 校验 | 按 API×应用×租户：1000次/分,50次/秒；自定义机器人 100/分,5/秒 | 回调体 AES-256-CBC 加密 |
| **企微** | msg_signature=SHA1(token,timestamp,nonce,encrypt) | 官方 Java 加解密库 | 回调 5s 超时，需快速 ACK |
| **通用 REST** | HMAC-SHA256 / OAuth2 client_credentials / JWT | 按目标平台文档适配 | 标准化 Connector 模板 |

### 4.4 不同类型服务适配

| 服务类型 | 抽象方案 |
| --- | --- |
| REST API | Connector = 认证器 + 签名器 + 限流重试器 + 响应映射器 |
| GraphQL | 复用 HTTP 传输层 + GraphQL 查询构造器 + 结果扁平化 |
| Webhook | 接收端适配器（签名校验+事件解析+幂等去重）+ 事件路由分发 |
| 文件接口 | 流式上传/下载 + 临时存储 + 清理调度 |
| 数据库直连 | 只读连接池 + SQL 白名单 + 结果集分页 + 超时保护 |

---

## 五、竞品对标：豆包 vs WorkBuddy vs 本项目

### 5.1 WorkBuddy 确认

WorkBuddy 为**腾讯云计算 CodeBuddy 团队**产品，2026 年新品时间线：1/19 内部体验 → 2/6 内测 → **3/9 正式上线** → 5/27 国际版 → 6/5 企业版+Agent Suite+ClawPro 管控平台 → 7/18 独立 App → 10/29 Buddy One 办公套件 [P1]。定位"全场景职场 AI 智能体桌面工作台"，**MCP 协议**接入 GitHub/Jira/Confluence/Notion，OneID 一键接入 10+ 腾讯系产品。企业版含 7×24 数字员工、管理后台、WMA 云端沙箱 Runtime、公有云/VPC/专有云部署 [P0]。

### 5.2 能力对比矩阵

| 维度 | 豆包/Coze (as of 2026-11) | WorkBuddy (as of 2026-11) | 本项目（自评 [推断]） |
| --- | --- | --- | --- |
| 联网信息检索 | ★★★ 豆包搜索开放 API + 独家信源 + 多轮检索增强 | ★★★ 深度调研多源交叉验证 | ★★ 标讯垂直定向检索 |
| 数据爬取登记 | ★★ 搜索 API 无入库登记 | ★★ 无业务登记 | ★★★ 标讯采集 + AGT 编号登记入库 |
| 技能/插件扩展 | ★★★ MCP + 插件市场 + 技能 | ★★★ MCP + SkillHub 过万技能 | ★ 内置 AGT 能力点，无开放生态 |
| 第三方集成 | ★★★ 飞书/微信/企微等生态 | ★★★ 腾讯系 10+ 产品 + Jira 等 | ★★ 钉钉通知 + 自有业务系统 |
| 任务编排自动化 | ★★★ Coze 可视化工作流 + 定时 Trigger | ★★★ 多 Agent 并行 + 7×24 云端 | ★★ 有限（DB 队列轮询，无可视化编排） |
| 知识库 | ★★★ RAG + 方舟知识库 | ★★★ 资料库 + 腾讯网盘记忆 | ★★ 业务库内建 |
| 多模态 | ★★★ 图/音/视频生成 | ★★★ 文档/PPT/设计产物交付 | ★ 弱（仅 LLM 文本提取） |
| 权限与审计 | ★★ 企业后台 + 审计日志 | ★★★ 沙箱 + 全链路 Trace | ★★★ 自研可控、随业务定制 |
| 私有化部署 | ★★ HiAgent 私有化 / Coze 开源版 | ★★ 专有云企业版 | ★★★ 天然内网私有化 |

### 5.3 性能与规模

| 指标 | 豆包 | WorkBuddy | 本项目 |
| --- | --- | --- | --- |
| 基准测试 | SuperCLUE-Office 85.33 第 1 [P1] | SuperCLUE-Office 83.99 第 3 [P1] | [unverified] 需自建评测 |
| 用户规模 | MAU 4.19 亿（2026-09，同比+143.6%）[P1] | PC 月访问量 2097 万（2026-06）[P1] | 内部用户 ~50 人 |
| 响应延迟 | [INSUFFICIENT DATA] | [INSUFFICIENT DATA] | P95 <5s（对话）[推断] |

### 5.4 差异化机会与短板

**本项目差异化机会**：
1. **数据私有壁垒**——标讯、项目、审批数据全程内网闭环，规避 SaaS Agent 数据出域顾虑
2. **业务深度集成**——Agent 直接操作工单/审批/报表数据并结构化登记，竞品仅靠 MCP 外挂连接器
3. **行业垂直知识**——招投标/部门工作域 AGT 编号体系是通用产品无法复制的资产
4. **反向输出**——将自有业务能力发布为 MCP Server，接入豆包/WorkBuddy 生态

**必须补齐的短板**：
1. 开放插件/技能扩展机制（MCP 客户端+服务端双支持）
2. 可视化任务编排与定时自动化
3. 多模态文档处理（PDF/图片解析）
4. 规范化评测基线（对标 SuperCLUE 建立内部评测集）
5. LLM 基座升级（需接入豆包 2.1 Pro / 混元 Hy4 量级模型）

### 5.5 选型风险

| 风险 | 说明 | 应对 |
| --- | --- | --- |
| Coze 企业版暂停新售 | 2026-11-30 起产品整合 [P1] | 关注后续产品动向；私有化部署选 HiAgent |
| 豆包搜索 API 数据出域 | 商业化服务，企业数据合规评估 | 国内竞品备选；数据脱敏后调用 |
| WorkBuddy 绑定腾讯生态 | 接入腾讯系产品顺畅，其他生态一般 | 作为腾讯侧集成的互补方案 |

---

## 六、技术选型清单汇总

### 6.1 核心技术选型

| 类别 | 选型 | 备选 | 优先级 |
| --- | --- | --- | --- |
| **搜索 API** | 博查 Bocha | 自托管 SearXNG | P0 |
| **动态抓取** | 自托管 Firecrawl | Jina Reader OSS | P1 |
| **结构化提取** | JSON Schema strict | — | P0 |
| **去重** | SimHash + URL 规范化 | — | P0 |
| **技能包封装** | SKILL.md + skill.yaml | — | P0 |
| **Java 动态加载** | PF4J 3.15 | OSGi/Karaf | P1 |
| **WASM 沙箱** | Chicory | — | P2（三方技能） |
| **依赖管理** | 技能包自带 lib/ + shade relocation | Maven 私服解析 | P0 |
| **签名校验** | cosign + jarsigner | — | P0 |
| **第三方接入协议** | MCP 2026-07-28 Streamable HTTP | OpenAPI 直连 | P0 |
| **Java MCP SDK** | Spring AI MCP Boot Starters | — | P0 |
| **密钥加密** | Jasypt + AES-GCM | 阿里云 KMS | P0 |
| **弹性治理** | Resilience4j 2.x | — | P0 |
| **国内平台签名适配** | 适配器模式（钉钉/飞书/企微） | — | P0 |
| **SSR 防护** | IP 段黑名单 + 白名单 pin | — | P0 |

### 6.2 依赖引入清单

| 依赖 | 版本 | 用途 |
| --- | --- | --- |
| PF4J | 3.15 | 插件加载框架 |
| pf4j-spring | 0.10 | Spring Boot 3 集成 |
| Resilience4j (Boot3) | 2.x | 重试/熔断/限流/超时 |
| Spring AI MCP | latest | MCP Java SDK |
| Jasypt Spring Boot | latest | 配置加密 |
| Chicory | latest | WASM 运行时（可选） |
| cosign | — | 技能包签名工具 |

---

## 七、分三阶段落地实施计划

### 第一阶段：基础能力补齐（1-3 个月）

| 工作包 | 任务 | 产出 |
| --- | --- | --- |
| **WP1-1：联网搜集基础设施** | 接入 Bocha API；实现 WebSearchConnector + WebFetchConnector；集成 SimHash 去重 + JSON Schema 提取 | 联网搜索/抓取/提取三件套可用；新增 OpsTool：web.search / web.fetch |
| **WP1-2：安全底座** | 实现 SSRF 防护组件（IP 段黑名单+DNS rebinding 防护）；部署 ContentGuard（关键词库+分类模型）；扩展 sys_oper_log 记录联网操作 | 安全合规底座上线；新增系统管理：联网安全配置页 |
| **WP1-3：第三方 API 统一框架** | 实现 Connector 抽象层（Authenticator/Signer/RateLimiter/ResponseMapper/TimeBudget 五组件）；Jasypt 集成；Resilience4j 全链路 | 统一第三方 API 接入框架；首批接入钉钉/飞书/企微 |
| **WP1-4：MCP Server 初版** | Spring AI MCP Boot Starters 搭建；将自有 CapabilityHandler 自动注册为 MCP Tool | Agent 能力对外暴露为 MCP 服务；可供其他 Agent（豆包/WorkBuddy）调用 |
| **WP1-5：Agent 调度升级** | 引入 LLM Planning 多轮工具循环（SystemOpsService 从单轮改多轮）；Agent 任务可视化面板 | Agent 自主规划+多轮执行；用户可见执行轨迹 |

**验收标准**：
- [ ] 联网搜索 API 接入并覆盖 10+ 主流信源
- [ ] 至少 3 个第三方平台（钉钉/飞书/企微）通过统一 Connector 接入
- [ ] MCP Server 可被外部 Agent（如豆包 Coze）成功调用
- [ ] SSRF 防护拦截验证通过（覆盖 8 种内网段）
- [ ] Agent 多轮工具调用闭环验证通过

### 第二阶段：技能包生态与深度集成（4-6 个月）

| 工作包 | 任务 | 产出 |
| --- | --- | --- |
| **WP2-1：技能包运行时** | PF4J 集成；SkillManager 安装/卸载/升级/热加载；skill.yaml 解析；权限清单校验 | 技能包运行时框架 |
| **WP2-2：技能仓库** | Nexus 私服；技能包签名校验（cosign+jarsigner）；版本管理 UI（管理后台技能市场） | 技能仓库 + 管理界面 |
| **WP2-3：三方技能沙箱** | Windows Job Object + 网络代理白名单（三方技能隔离）；Chicory WASM 集成（不可信技能隔离） | 分层沙箱完整实现 |
| **WP2-4：可视化编排** | 工作流编排器（拖拽式）；定时 Trigger；Cron 调度；DAG 执行引擎 | 可视化任务编排器 |
| **WP2-5：知识库升级** | 向量库集成（pgvector / Milvus）；文档导入（PDF/DOCX/URL）；RAG 增强 | Agent 知识库能力可用 |
| **WP2-6：MCP Client** | MCP Client 对接 Coze/Dify/n8n 生态；OpenAPI→MCP 自动转换 | 可消费生态插件 |

**验收标准**：
- [ ] 技能包可热加载/卸载，无重启
- [ ] 三方技能在沙箱内运行，文件/网络隔离验证通过
- [ ] 可视化编排器可编排 ≥5 步的 Agent 工作流
- [ ] 知识库 RAG 检索准确率 ≥85%（内部评测集）
- [ ] MCP Client 成功消费 ≥3 个外部 MCP Server

### 第三阶段：生态化与生产加固（7-9 个月）

| 工作包 | 任务 | 产出 |
| --- | --- | --- |
| **WP3-1：评测基线** | 建立内部 Agent 评测集（对标 SuperCLUE/AgentBench）；自动化评测管线；回归测试门禁 | 评测基线 + 自动化门禁 |
| **WP3-2：运维监控** | Agent 调用全链路 Trace（Skill/Span）；技能包执行审计日志；SLO 仪表盘（P95 延迟/错误率/成本） | 可观测性体系 |
| **WP3-3：降级与容灾** | 多模型主备切换；降级策略（联网失败→本地缓存、LLM 失败→规则回退）；跨机房容灾 | 生产级降级方案 |
| **WP3-4：生态输出** | 将核心业务能力发布为 MCP Server；接入行业垂直生态；开放技能包开发指南 | 生态反哺 |
| **WP3-5：多模态** | PDF/图片/表格文档解析（Apache Tika + 多模态 LLM）；文档结构化提取 | 多模态文档处理 |

**验收标准**：
- [ ] Agent P95 响应延迟 ≤10s（联网场景）
- [ ] 技能包执行错误率 ≤0.1%
- [ ] 降级切换 RTO ≤30s，RPO ≤0
- [ ] 评测基线分 ≥80（对标 SuperCLUE-Office）
- [ ] 多模态文档解析成功率 ≥90%

---

## 八、测试与验证标准

### 8.1 单元测试

| 测试域 | 覆盖率目标 | 关键测试项 |
| --- | --- | --- |
| 联网搜集 | 行覆盖率 ≥80% | 搜索引擎 API Mock（正常/超时/限流/格式异常）、SimHash 去重正确性、SSRF 防护拦截（8 种内网段）、Robots.txt 遵循 |
| 技能包 | 行覆盖率 ≥85% | 安装/卸载/升级生命周期、ClassLoader 泄漏检测、权限清单校验（越权拒绝）、签名校验（篡改检测）、WASM 沙箱内存隔离 |
| 第三方 API | 行覆盖率 ≥80% | 各平台签名校验、429/Retry-After 处理、CircuitBreaker 熔断/恢复、密钥加密/解密/轮换、回调幂等 |

### 8.2 集成测试

| 测试域 | 关键场景 | 验证标准 |
| --- | --- | --- |
| 联网搜集端到端 | 用户指令→检索→抓取→提取→入库 | 全链路 P95 ≤10s；入库数据字段完整 |
| 技能包生命周期 | 安装→执行→卸载→重装 | 热加载无重启；卸载后 Metaspace 无增长 |
| 第三方 API 集成 | 钉钉/飞书/企微各 1 个完整调用 | 签名正确、限流正确、回调幂等 |
| MCP 互通 | 本项目 MCP Server 被 Coze/Dify 调用 | 工具发现+调用+结果返回全链路通过 |
| Agent 多轮规划 | 3 轮以上工具调用循环 | 规划正确性 + 工具选择准确率 |

### 8.3 安全测试

| 测试项 | 验证标准 |
| --- | --- |
| SSRF 攻击 | 8 种内网段 + IPv6 映射段全部被拦截 |
| 技能包投毒 | 恶意技能包（文件外传/命令执行）被权限清单拒绝 |
| MCP 工具描述投毒 | 描述哈希不匹配被拦截 |
| 密钥泄漏 | 日志/响应中无明文密钥/Bearer Token |
| SQL 注入 | 所有动态查询经 MyBatis-Plus 参数化 |
| 越权访问 | 低权限角色调用高权限接口返回 403 |

### 8.4 回归测试

- 全量回归测试（现有 126 用例）必须 100% 通过
- 新增功能必须配套单元测试 + 集成测试，覆盖率达目标
- 每次 Skill/依赖升级触发全量回归
- 评测基线每季度更新，分阶段跟踪

---

## 九、上线后稳定性保障方案

### 9.1 可观测性体系

| 维度 | 方案 |
| --- | --- |
| **指标监控** | Prometheus + Grafana：Agent 调用量/P95 延迟/错误率/LLM Token 消耗/技能包执行次数/联网成功率 |
| **全链路 Trace** | OpenTelemetry：用户指令 → Agent 规划 → 工具选择 → 外部调用 → 结果返回全 Span 追踪 |
| **日志审计** | ELK：联网操作日志（URL/抓取量/提取字段）、技能包执行日志（ID/版本/耗时/结果）、安全审计日志（安装/卸载/权限变更） |
| **告警规则** | 错误率 >5% → 告警；P95 >10s → 告警；技能包卸载失败 → 告警；密钥过期 → 提前 7 天告警 |

### 9.2 降级与容灾

| 场景 | 降级策略 |
| --- | --- |
| LLM 不可用 | 规则引擎回退 + 人工接管标记 |
| 联网 API 全部不可用 | 本地缓存语料 + 离线知识检索 |
| 第三方平台 429 | 指数退避重试 → 熔断降级 → 缓存值返回 |
| 技能包运行异常 | 沙箱隔离不影响主流程 → 标记不可用 → 自动重启 |
| ai-gateway 故障 | 直连备选模型商（Ollama 本地） |
| 单机房故障 | 跨机房主备切换（RTO ≤30s） |

### 9.3 变更管理

| 项目 | 规范 |
| --- | --- |
| 技能包版本 | semver + 不可变 release + cosign 签名 |
| 灰度发布 | 按用户组灰度（10% → 30% → 100%）；支持一键回滚 |
| 数据库变更 | Flyway 迁移脚本；双写过渡；回滚预案 |
| 依赖升级 | 分阶段灰度；全量回归门禁通过方可全量 |

### 9.4 SLO 目标

| 指标 | 目标 |
| --- | --- |
| Agent 对话 P95 延迟（本地） | ≤5s |
| Agent 对话 P95 延迟（联网） | ≤10s |
| 技能包执行成功率 | ≥99.9% |
| 联网采集成功率 | ≥95%（剔除源站不可达） |
| 系统可用性 | 99.9% |
| 安全事件响应时间 | ≤15min |

---

## 十、风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
| --- | --- | --- | --- |
| 博查 API 商业限制 | 中 | 高 | SearXNG 自托管兜底；数据脱敏后调用 |
| MCP 规范快速迭代 | 高 | 中 | Spring AI 官方跟进；锁定小版本；自动化适配测试 |
| PF4J ClassLoader 泄漏 | 中 | 中 | Metaspace 监控；定期重启；热卸载严格生命周期 |
| 技能包供应链攻击 | 中 | 高 | cosign 签名校验；描述哈希 pinning；沙箱隔离；最小权限 |
| LLM 基座模型能力不足 | 中 | 高 | 多模型主备；定期基准评测；必要时接入豆包/混元 |
| 35 次/分 LLM 限流 | 高 | 中 | 令牌桶全局闸门；本地缓存优先；离线批处理 |
| 扣子企业版暂停新售 | 低 | 低 | 关注后续动向；HiAgent 私有化部署备选 |

---

## Sources

- [博查 Bocha API](https://api.bocha.cn)
- [Bing Search API 退役公告](https://learn.microsoft.com/en-us/LIFECYCLE/announcements/bing-search-api-retirement)
- [Firecrawl Self-Hosted](https://aws.amazon.com/marketplace/pp/prodview-cj6pjxbjtrsna)
- [SearXNG 文档](https://docs.searxng.org/)
- [MCP 2026-07-28 规范](https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http)
- [Spring AI MCP](https://spring.io/blog/2025/09/16/spring-ai-mcp-intro-blog/)
- [Anthropic Agent Skills](https://www.anthropic.com/research/equipping-agents-for-the-real-world-with-agent-skills)
- [PF4J CHANGELOG](https://salsa.debian.org/java-team/libpf4j-java/-/blob/upstream/3.15.0+dfsg/CHANGELOG.md)
- [Chicory WASM](https://quarkus.io/blog/k8s-style-CEL-with-quarkus-chicory/)
- [Claude Code Sandboxing](https://www.anthropic.com/engineering/claude-code-sandboxing)
- [MCPTox 攻击论文](https://arxiv.org/abs/2508.14925)
- [WorkBuddy 企业版](https://cloud.tencent.com/document/product/1831/134329)
- [豆包工作插件文档](https://www.doubao.com/work/docs/zh-cn/articles/705018132598-plugins)
- [SuperCLUE-Office 榜单](https://m.weibo.cn/detail/5362754177206590)
- [钉钉开放平台限流](https://open.dingtalk.com/document/development/how-to-process-api-throttling-on-the-dingtalk-server)
- [飞书频控策略](https://feishu.apifox.cn/doc-1939846)
- [Resilience4j 实战](https://bbs.huaweicloud.com/blogs/519551)
- [TC260-003 生成式AI安全要求](https://whhlwdj.gov.cn/view/4178.html)
