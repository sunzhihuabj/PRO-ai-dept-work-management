# 基于AI的部门工作管理软件

> 消防系统集成部门工作管理平台 - 前后端一体化项目

## 项目简介

本项目是根据《基于AI的部门工作管理软件需求文档 V4.0》开发的部门工作管理平台，覆盖任务管理、项目管理、档案归档、文件审核、事件审批、绩效统计、AI助手等核心模块，深度融合精益项目管理（LPM）与全生命周期项目管理理念。

## 技术栈

| 端 | 技术 | 版本 |
|----|------|------|
| 后端 | Spring Boot + MyBatis-Plus + MySQL + Spring Security + JWT | 3.2.5 / 3.5.7 / 8.0 |
| 前端PC | Vue 3 + Element Plus + Pinia + Vue Router + Vite + TypeScript | 3.4 / 2.7 / 2.1 / 4 / 5 |
| AI服务 | Ollama + Qwen2.5 | 7b/14b |
| 文件存储 | 本地磁盘 / MinIO | - |

## 目录结构

```
基于AI的部门工作管理软件0812/
├── 02_项目开发需求文档.md          # 需求文档（核心输入）
├── database/                        # 数据库脚本
│   ├── 01_schema.sql                # 37张表DDL建表脚本
│   └── 02_seed_data.sql             # 种子数据（用户/角色/菜单/KPI等）
├── backend/                         # 后端Spring Boot工程
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/dept/mgmt/
│       │   ├── DeptMgmtApplication.java      # 启动类
│       │   ├── common/                       # 公共模块
│       │   │   ├── result/                    # 统一响应R/PageResult
│       │   │   └── exception/                 # 全局异常处理
│       │   ├── config/                        # 配置类
│       │   │   ├── MybatisPlusConfig.java    # 分页插件
│       │   │   └── MyMetaObjectHandler.java   # 自动填充
│       │   ├── security/                     # 安全模块
│       │   │   ├── JwtUtils.java             # JWT工具
│       │   │   ├── JwtAuthenticationFilter.java # JWT过滤器
│       │   │   ├── LoginUser.java             # 登录用户
│       │   │   └── SecurityConfig.java        # Security配置
│       │   ├── entity/                        # 实体类
│       │   ├── mapper/                        # Mapper接口
│       │   ├── dto/                           # 数据传输对象
│       │   ├── service/                       # 业务逻辑层
│       │   └── controller/                    # 控制器层
│       └── resources/
│           ├── application.yml                # 主配置
│           └── mapper/                        # MyBatis XML
├── miniapp/                         # 移动端 uni-app 工程（H5/微信小程序）
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── pages/                   # 登录/工作台/任务/事件/消息/我的
│       ├── api/                     # 接口封装（wx-login 绑定流程）
│       └── utils/request.js         # uni.request 统一封装
└── frontend/                        # 前端Vue3工程
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    └── src/
        ├── main.ts                            # 入口
        ├── App.vue                            # 根组件
        ├── api/                               # API接口封装
        │   ├── auth.ts                        # 认证
        │   ├── user.ts                        # 用户
        │   ├── project.ts                     # 项目
        │   ├── task.ts                        # 任务
        │   └── dashboard.ts                   # 工作台
        ├── store/                             # Pinia状态管理
        │   ├── user.ts                        # 用户Store
        │   └── app.ts                         # 应用Store
        ├── router/                            # 路由
        │   ├── index.ts                       # 路由配置
        │   └── permission.ts                  # 路由守卫
        ├── layout/                            # 布局
        │   └── AppLayout.vue                  # 主布局
        ├── views/                             # 页面
        │   ├── login/Login.vue                # 登录页
        │   ├── dashboard/Dashboard.vue        # 工作台
        │   ├── task/                          # 任务管理
        │   │   ├── TaskList.vue               # 任务列表
        │   │   └── TaskDetail.vue             # 任务详情
        │   ├── project/                      # 项目管理
        │   │   ├── ProjectList.vue            # 项目列表
        │   │   └── ProjectDetail.vue          # 项目详情
        │   ├── system/UserManagement.vue      # 用户管理
        │   └── error/404.vue                  # 404页
        ├── utils/request.ts                   # Axios封装
        └── styles/                            # 全局样式
```

## 快速开始

### 一键启动（推荐）

> 前提：本机已装 Node.js ≥ 18、JDK 21、Maven 3.6+，MySQL 8 运行于 localhost:3306（root/root）

1. **首次部署**：双击 `01_初始化数据库.bat`（建库 + 43 张表 + 种子数据）
2. **启动项目**：双击 `02_启动项目.bat`（自动弹窗启动后端 8081 + 前端 5173）
   - 或分别双击 `start-backend.bat` / `start-frontend.bat`
3. 浏览器访问 `http://localhost:5173`，默认账号 `sunzhihua / 123456`
4. Docker 方式（可选）：`docker compose up -d --build`

### 手工启动

```bash
# 1. 数据库初始化
mysql -u root -p < database/01_schema.sql
mysql -u root -p dept_mgmt < database/02_seed_data.sql

# 2. 后端（http://localhost:8081/api，文档 /api/swagger-ui.html）
cd backend && mvn clean package -DskipTests && java -jar target/dept-mgmt.jar

# 3. 前端（http://localhost:5173）
cd frontend && npm install && npm run dev

# 4. 移动端 H5 预览（http://localhost:5174，微信端需在 manifest.json 填 appid）
cd miniapp && npm install && npm run dev:h5
```

### 4. 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| sunzhihua | 123456 | 部门负责人 |
| lihailong | 123456 | 项目负责人 |
| zhangsibei | 123456 | 技改负责人 |
| yangshuang | 123456 | 技术员 |
| fubinbin | 123456 | 研发人员 |
| zhangyujing | 123456 | 档案管理员 |
| shizhiqiang | 123456 | 审核员 |

## 已完成功能（P0核心模块）

### 后端
- [x] **认证模块**：登录（JWT）、登出、获取用户信息、修改密码
- [x] **用户管理**：用户CRUD、分页查询、重置密码、状态切换
- [x] **项目管理**：**项目资料随创建提交**（多文件上传、按项目自动归位到服务器  目录）、项目详情页**资料明细**（类别/阶段/版本/上传人/下载）
- [x] **项目管理**：项目CRUD、自动生成编号、按类型生成阶段模板（整机厂19阶段/技改6阶段/研发5阶段）、阶段状态管理
- [x] **任务管理**：任务CRUD、分页查询、状态流转（待办→进行中→待审核→已完成→已关闭，含合法性校验）、工时录入与累加、任务日志记录、进度更新
- [x] **工作台**：个人待办（分组）、数据概览（任务/项目统计）、最新消息、快捷入口
- [x] **档案归档**：文件上传（类型/大小校验、自动版本号递增）、列表（项目/阶段/关键字过滤）、下载、归档、审核（通过/驳回+备注）、审核记录查询、file_version 版本同步与历史版本下载、文件分类树 CRUD、审核角色限定（档案管理员/审核员/部门负责人）
- [x] **任务资料**：任务详情页上传资料（**按任务名重命名**，全部归属所在项目，同任务同名自动递增版本 V1→V2…），列表/下载/**AI 差异分析**（对比上一版本内容，输出改动点/新增/删除/风险，需本地 Ollama）
- [x] **事件审批**：事件CRUD、待审批/处理中/已完成/已关闭/已驳回状态机、受理/驳回/转派/处理/完成/关闭操作、处理记录流水、我的待办/我发起的
- [x] **绩效管理**：指标CRUD、权重配置（总和校验=100）、按期自动生成草稿评分、±10分人工调整（必填原因）、发布锁定、按用户汇总报表（加权总分+等级分布）
- [x] **AI助手**：对接 Ollama（qwen2.5），日报/周报/会议纪要摘要接口
- [x] **系统管理扩展**：部门树 CRUD、@OperLog 操作日志切面（17+ 处写操作自动落库、异步零侵入）、消息中心（分页/未读数/批量已读）
- [x] **通知公告**：公告管理（草稿/发布）、发布自动触达全员站内消息
- [x] **协作增强**：消息未读角标（60s 自动刷新）、归档台账抽屉、任务池领取（从标准模板一键生成任务）
- [x] **协作扩展**：任务子任务拆解（勾选完成）、任务评论（仅本人可删）、阶段门禁审核、归档台账自动登记
- [x] **奖金管理**：配置CRUD（唯一键校验）、KPI等级联动系数（A=1.2 B=1.0 C=0.8 D=0.6，自动重算月度/年度金额）、月度/年度发放（年度防超发）、发放流水

### 前端
- [x] **登录页**：表单校验、记住我、默认账号提示
- [x] **主布局**：侧边栏菜单、顶部导航、用户下拉、面包屑
- [x] **路由守卫**：Token校验、白名单、自动获取用户信息
- [x] **工作台**：统计卡片、待办列表、快捷入口、消息列表
- [x] **任务管理**：列表（搜索/分页/状态流转操作）、详情（信息展示/状态流转/进度更新/工时录入/日志时间线）
- [x] **项目管理**：列表（搜索/分页/CRUD）、详情（项目信息/阶段步骤条/阶段列表/阶段状态流转）
- [x] **用户管理**：列表（搜索/分页）、新增/编辑弹窗、重置密码、状态切换、删除
- [x] **档案归档**：文件上传（拖拽/点击）、文件列表（关键字查询、归档操作）、文件详情（信息展示、审核操作、审核记录）
- [x] **事件审批**：事件列表（筛选/新建/编辑/删除）、事件详情（处理动作按角色显隐、处理记录时间线）
- [x] **绩效管理**：指标管理 CRUD、绩效评分（计算/调整/发布）、报表汇总
- [x] **精益改进**：提案管理（状态机+评审）、5Why/鱼骨图根因分析、经验教训库、A3报告（八段式+状态推进）、价值流记录（流程效率自动计算）、浪费登记（七大浪费类型+状态推进）
- [x] **AI助手**：摘要类型选择、内容输入、结果展示与复制

## 数据库表清单（37张）

| 模块 | 表名 | 说明 |
|------|------|------|
| 系统管理 | sys_user, sys_role, sys_user_role, sys_menu, sys_role_menu, sys_dept, sys_oper_log | 用户/角色/菜单/部门/日志 |
| 项目管理 | project, project_phase, project_member, project_station, milestone, phase_gate, deliverable_template, deliverable_checklist | 项目/阶段/成员/里程碑/门控/交付物 |
| 任务管理 | task, task_sub, task_log, task_comment, work_hour, task_pool | 任务/子任务/日志/评论/工时/任务池 |
| 文件归档 | file_info, file_version, file_category, archive_record | 文件/版本/分类/归档 |
| 审核事件 | audit_flow, audit_record, event_info, event_handle | 审核流程/记录/事件/处理 |
| 绩效管理 | kpi_indicator, kpi_score, kpi_weight | KPI指标/评分/权重 |
| 精益改进 | improvement_proposal, a3_report, root_cause_analysis, lessons_learned | 提案/A3/根因/经验 |
| 价值流 | value_stream_record, waste_record | 价值流记录/浪费记录 |
| 消息通知 | sys_notice, user_message | 系统通知/用户消息 |
| 奖金管理 | project_bonus, project_bonus_record | 项目奖金/发放记录 |

## API接口清单

| 模块 | 前缀 | 主要接口 |
|------|------|---------|
| 认证 | /api/v1/auth | 登录/登出/用户信息/修改密码 |
| 用户 | /api/v1/users | CRUD/重置密码/状态切换/下拉选项 |
| 项目 | /api/v1/projects | CRUD/阶段列表/阶段状态/项目成员(添加·移除)/里程碑(CRUD+标记完成·延期)/交付物清单(生成·绑定·检查) |
| 任务 | /api/v1/tasks | CRUD/状态流转/进度/工时/日志/我的待办 |
| 工作台 | /api/v1/dashboard | 待办/统计/消息/快捷入口 |
| 档案归档 | /api/v1/files | 上传（**phaseId 可选=项目级资料**，自动归位 项目/资料 目录）/列表（分页+关键字）/详情/下载/归档/审核/审核记录/版本列表/版本下载 |
| 文件分类 | /api/v1/file-categories | 分类树 CRUD（父分类、编码、排序、启停） |
| 事件审批 | /api/v1/events | CRUD/受理/驳回/转派/处理/完成/关闭/处理记录/我的待办 |
| 绩效管理 | /api/v1/kpi | 指标CRUD/权重配置/评分计算/调整/确认/报表/CSV导出 |
| 部门管理 | /api/v1/depts | 部门树 CRUD（含部门下用户/子部门删除保护） |
| 操作日志 | /api/v1/oper-logs | 分页查询（@OperLog 切面自动记录写操作，异步落库） |
| 消息中心 | /api/v1/messages | 我的消息分页/未读数/单条已读/全部已读 |
| 奖金管理 | /api/v1/bonus | 配置CRUD/KPI等级联动系数(A=1.2 B=1.0 C=0.8 D=0.6)/月度年度发放/防超发/发放记录 |
| 交付物管理 | /api/v1/deliverables | 模板查询/按项目类型一键生成清单(幂等)/手动添加/绑定档案文件/检查确认/完成度统计 |
| 精益改进 | /api/v1/lean | 提案CRUD+评审+状态机/根因分析(5Why/鱼骨图)/经验教训库/A3报告CRUD+推进/价值流记录(效率自动算)/浪费登记+状态推进 |
| 通知公告 | /api/v1/notices | 管理CRUD/发布(自动写全员站内消息)/已发布列表 |
| 任务扩展 | /api/v1/tasks | 子任务CRUD+勾选状态 / 评论发布·删除（仅本人） / 任务资料上传（按任务名重命名·归属项目·同名递增版本）/ AI 版本差异分析（Ollama） |
| 任务池领取 | /api/v1/gates/pool | 标准任务模板查询 / 领取生成任务（pullMode=1 拉动模式） |
| 阶段门禁 | /api/v1/gates | 门禁CRUD/审核通过·驳回/任务池(标准任务模板)查询 |
| 归档台账 | /api/v1/files/archive-records | 归档时自动登记台账（人/时间/类型），按项目·文件查询 |
| AI助手 | /api/v1/ai | 摘要生成（日报/周报/会议纪要/通用） |

## 后续迭代计划

- [x] 文件上传与归档模块（P0）—— 已完成
- [x] 文件审核流程（P1）—— 已完成
- [x] 事件审批模块（P1）—— 已完成
- [x] 绩效统计模块（P1）—— 已完成
- [x] AI助手模块（日报/周报/会议纪要摘要，P1）—— 已完成
- [x] 微信小程序（UniApp，P0）—— 已完成（H5/微信双端，微信授权登录+绑定，待办/任务/工时/事件/消息五件套）
- [x] 精益改进与价值流分析（P2）—— 已完成
- [x] 项目奖金管理（P2）—— 已完成

## 测试

后端单元测试（JUnit5 + Mockito）：



覆盖：任务状态机合法性（待办→进行中→待审核→已完成，终态拦截）、奖金金额计算（base×coef、×3 年度、唯一键校验 409）。当前 7/7 通过。

## 开发规范

- 后端遵循《需求文档》第14.4节代码规范
- 前端使用 Composition API + `<script setup>` 风格
- 数据库使用 InnoDB 引擎、utf8mb4 字符集、逻辑删除
- API遵循RESTful规范，统一响应结构 `{code, msg, data}`
- 密码使用BCrypt加密存储
- JWT Token有效期2小时

## 文档参考

详细需求请参阅 `02_项目开发需求文档.md`（V4.0版本，13922行）。
