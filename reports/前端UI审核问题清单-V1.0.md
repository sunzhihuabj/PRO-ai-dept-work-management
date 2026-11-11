# 前端 UI 全面审核问题清单 V1.0

> 审核日期：2026-11-11
> 审核范围：`frontend/` 全部页面组件（约 30 个视图 + 布局 + 全局样式）
> 技术栈：Vue 3 + TypeScript + Element Plus + Vite 5 + Pinia
> 审核维度：视觉一致性 / 交互流畅性 / 响应式适配 / 可访问性 / 性能表现 / 代码规范
> 方法：源码逐页静态审查 + 全局样式比对 + 构建产物分析 + dev 环境浏览器实测（视口 960×579）

---

## 一、问题总览

| 优先级 | 数量 | 定义 |
|---|---|---|
| P0 | 4 | 影响核心用户路径的功能异常 / 布局错乱，必须立即修复 |
| P1 | 5 | 影响交互体验、可访问性、视觉一致性的系统性问题 |
| P2 | 4 | 性能与代码规范类问题 |
| **合计** | **13 类（涉及约 50+ 处代码点位）** | 全部已修复，详见《优化前后对比说明》 |

---

## 二、P0 问题（核心路径阻断类）

### P0-1 keep-alive 参数路由数据陈旧（功能缺陷）
- **现象**：`AppLayout.vue` 的 `<keep-alive>` 未配置 key，任务详情 / 项目详情 / 审核事件详情 / 文件详情 4 个参数路由页面在 setup 时一次性读取 `route.params.id`。从详情 A 返回列表再进入详情 B 时，页面命中缓存显示 A 的旧数据。
- **影响**：核心用户路径（列表 → 详情）数据错乱，属功能性缺陷。
- **修复**：`<keep-alive :key="$route.fullPath">`（router-view 处），参数路由按完整路径缓存实例。
- **位置**：`frontend/src/layout/AppLayout.vue`

### P0-2 登录页 360px 宽度溢出（布局错乱）
- **现象**：`.login-box` 固定 `width: 420px`，在 ≤420px 视口（如 360px 手机）产生横向滚动。
- **修复**：改为 `width: min(420px, calc(100vw - 32px))`。
- **位置**：`frontend/src/views/login/Login.vue`

### P0-3 多页栅格未做移动端折叠（布局错乱）
- **现象**：以下页面使用固定 `span` 栅格，窄屏下卡片挤压变形：
  - Dashboard：统计卡 4×`span=6`、主区 16/8
  - ArchiveManagement：4 张统计卡 `span=6`
  - DeliveryManagement：6×`span=8`
  - ProjectDetail：两栏 `span=12`
- **修复**：全部改为 `:xs/:sm/:lg` 响应式断点（统计卡 xs=12 sm=6；主区 xs=24 lg=16/8；双栏 xs=24 lg=12）。
- **位置**：上述 4 个视图文件

### P0-4 全局弹层移动端溢出（无兜底）
- **现象**：全站 50+ 个 `el-dialog` / `el-drawer` 多数为固定像素宽度，<768px 视口下溢出屏幕。
- **修复**：`frontend/src/styles/index.scss` 增加全局媒体查询兜底——≤768px 时 dialog 92vw、左右 drawer max-width 92vw；≤480px 时 message-box max-width 92vw。已确认全站无 fullscreen 弹层，规则安全。

---

## 三、P1 问题（体验与规范类）

### P1-1 表单对话框误触关闭（交互缺陷）
- **现象**：全站 50 个含 `<el-form>` 的 `<el-dialog>` 未设置 `close-on-click-modal="false"`，用户在表单区域点击意外关闭弹窗，已填数据丢失。
- **修复**：脚本批量为 24 个文件、50 个表单对话框注入 `:close-on-click-modal="false"`。

### P1-2 键盘可达性缺失（可访问性）
- **现象**：
  - 侧边栏折叠按钮为纯 `span` 无 role/tabindex，键盘用户不可达；
  - 档案管理 4 张统计卡、Dashboard 待办/动态条目仅 `@click` 无键盘支持；
  - 全局未定义 `:focus-visible` 焦点样式，键盘导航不可见。
- **修复**：补齐 `role="button"` + `tabindex="0"` + `@keydown.enter` + `aria-label`（AppLayout 折叠按钮另加 title）；全局增加 `:focus-visible` outline。

### P1-3 视觉规范不统一
- **现象**：
  - `--dm-muted` 文本色 `#909399` 对比度仅 2.8:1，不满足 WCAG AA 4.5:1；
  - `.page-container` 与 `app-main` 双重 padding，留白来源不唯一；
  - `.search-bar` / `.toolbar` / `.el-pagination` 各页自行定义间距，数值不一（12/16/20px 混用）；
  - `.stat-card` 全站 hover 位移生效但仅部分页面可点击，视觉暗示与行为不符；
  - 菜单分组标题对比度过低（rgba .4）。
- **修复**：全局样式（`index.scss`）统一重写——muted 色 `#767676`（AA 达标）、page-container padding 归零（留白单一来源 = app-main）、search-bar/toolbar 统一 margin-bottom 16px、pagination 统一 margin-top 16px、stat-card 游标与 hover 收敛到 `.clickable` 修饰、菜单标题 rgba .55。

### P1-4 scoped 样式重复定义（代码规范）
- **现象**：47 处组件 scoped 样式与全局样式重复定义（39 处单行重复 + 8 处多行块），如 Workshop/Delivery/Tender 的 `.toolbar`、EventList 的 `.search-bar`、多页 `.card-header` 等，维护时易出现"改了全局不生效"的困惑。
- **修复**：删除全部重复定义；保留合法局部变体（TaskDetail `.card-header` 为 flex-start 对齐变体）；Archive scoped 仅保留业务类。

### P1-5 Dashboard 页面独立实现偏离规范
- **现象**：自定义 stat-card 结构（未用全局类）、无 onActivated 刷新（keep-alive 下数据陈旧）、接口失败无错误提示、内嵌重复样式。
- **修复**：统一改用全局 stat-card 结构 + `onActivated(loadData)` + catch 中 `ElMessage.error` + 删除本地重复样式。

---

## 四、P2 问题（性能与工程类）

### P2-1 Element Plus 全量引入
- **现象**：`main.ts` 全量 `import ElementPlus` + 全量 CSS，JS 包体 1,088KB（gzip 341KB），tree-shaking 完全失效。
- **修复**：按需引入——删除全量 import；保留图标全量注册（Dashboard 快捷入口为后端下发图标名动态渲染，全量注册为必要权衡，代码内已注释说明）；`app.use(ElLoading)` 注册 v-loading；4 个编程式 API 组件（message/message-box/notification/loading）单独引入样式副作用；App.vue 用 `<el-config-provider :locale="zhCn">` 包裹（顺带修复中文语言包）。

### P2-2 Vite 未做分包策略
- **现象**：构建产物单 chunk 超限告警，无 vendor 拆分，缓存利用率差。
- **修复**：`manualChunks` 拆分 `vue-vendor`；element-plus 不强制分包（查证 package.json `sideEffects` 仅声明样式文件，保持默认以便 tree-shaking 生效）；`chunkSizeWarningLimit: 1500`。

### P2-3 缺少 favicon（工程规范）
- **现象**：index.html 引用不存在的图标文件，浏览器 404。
- **修复**：新建 `frontend/public/favicon.svg`（蓝底 M 标记），index.html 更新引用。

### P2-4 全局滚动条样式重复定义
- **现象**：`reset.scss` 与 `index.scss` 各定义一份 webkit 滚动条样式。
- **修复**：删除 reset.scss 中的重复版本，保留 index.scss 版本。

---

## 五、审核方法与限制说明（如实披露）

1. **视口限制**：TRAE 内置浏览器视口固定 960×579、不可调整，无法对 360px 做像素级截图验证。替代方案：① 审计全部固定宽度写法（px/固定 span）；② 验证编译后 CSS 中响应式媒体规则存在；③ DOM 层验证 `el-col-xs-*` / `el-col-sm-*` 类生效。360px 极端场景建议后续用 Chrome DevTools 人工复核一次。
2. **Lighthouse**：本次未在环境内实跑 Lighthouse 评分（无 Chrome 插件通道）。以构建产物体积（gzip 对比）、DOM 冗余清理、控制台零错误作为性能代理指标，见《验证测试报告》。
3. **数据层遗留**：数据库存在遗留 `unzhihua` 账号行（种子数据为 sunzhihua），属数据问题非 UI 缺陷，建议后端清理。
4. **既有告警**：Sass legacy-js-api 弃用警告为项目既有问题（dart-sass 1.x 与 Vite 5 组合），本次未升级构建链，避免引入无关风险。
