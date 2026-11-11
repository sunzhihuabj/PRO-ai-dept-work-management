# 前端 UI 优化前后对比说明 V1.0

> 日期：2026-11-11 ｜ 配套文档：《前端UI审核问题清单-V1.0.md》《前端UI优化验证测试报告-V1.0.md》

---

## 一、核心指标对比

| 指标 | 优化前 | 优化后 | 变化 |
|---|---|---|---|
| element-plus JS 包体 | 1,088 KB | 466 KB | **-57.2%** |
| element-plus JS gzip | 341 KB | 150 KB | **-56.0%** |
| vendor 拆分 | 无 | vue-vendor 112 KB（gzip 43.9 KB） | 新增 |
| 全站 CSS | 未单独度量 | 91.2 KB（gzip 13.5 KB） | 基线 |
| vue-tsc 类型检查 | — | 0 错误 | 通过 |
| 生产构建 | 通过 | 通过 | 通过 |
| dev 冒烟控制台错误 | — | 0 error | 通过 |

> 构建命令 `npm run build`（vite build + vue-tsc），数据取自构建输出原始报告。

---

## 二、修复项逐条对比

### 1. 功能与布局（P0）

| # | 页面/位置 | 优化前 | 优化后 |
|---|---|---|---|
| 1 | AppLayout keep-alive | 无 key，4 个详情页切换显示旧数据 | `:key="$route.fullPath"`，详情页按路径隔离缓存 |
| 2 | Login `.login-box` | width:420px，360px 溢出 | `min(420px, calc(100vw - 32px))` |
| 3 | Dashboard 栅格 | 固定 span=6 / 16-8 | `:xs="12" :sm="6"` / `:xs="24" :lg="16"/:lg="8"` |
| 4 | ArchiveManagement 栅格 | 4 卡固定 span=6 | `:xs="12" :sm="6"` |
| 5 | DeliveryManagement 栅格 | 6 卡固定 span=8 | `:xs="24" :sm="8"` |
| 6 | ProjectDetail 栅格 | 双栏固定 span=12 | `:xs="24" :lg="12"` |
| 7 | 全局弹层 | 无移动端兜底 | ≤768px dialog 92vw / drawer 92vw；≤480px messagebox 92vw |

### 2. 交互与可访问性（P1）

| # | 项目 | 优化前 | 优化后 |
|---|---|---|---|
| 8 | 表单对话框 | 50 个弹窗点击遮罩即关闭，表单数据易丢失 | 全部注入 `:close-on-click-modal="false"`（24 文件） |
| 9 | 侧边栏折叠按钮 | 纯装饰 span，键盘不可达 | role=button + tabindex + Enter 键 + aria-label/title |
| 10 | 档案管理统计卡 | 仅鼠标点击 | clickable + role + tabindex + Enter 键 |
| 11 | Dashboard 待办/动态 | 仅鼠标点击 | 同上，键盘可达 |
| 12 | 焦点可见性 | 无 focus-visible 样式 | 全局 `:focus-visible` outline |
| 13 | 次要文本对比度 | #909399（2.8:1，不达 AA） | #767676（≥4.5:1，达 WCAG AA） |
| 14 | 菜单分组标题 | rgba(0,0,0,.4) 过淡 | rgba .55 |

### 3. 视觉一致性（P1）

| # | 项目 | 优化前 | 优化后 |
|---|---|---|---|
| 15 | 留白来源 | page-container 与 app-main 双重 padding | page-container padding 归零，app-main 唯一来源 |
| 16 | search-bar / toolbar | 各页 12/16/20px 混用 | 全局统一 margin-bottom:16px |
| 17 | 分页器 | 各页自定义间距 | 全局 margin-top:16px；≤768px 隐藏 sizes/jumper |
| 18 | stat-card | 全站 hover 位移但并非都可点击 | 游标与 hover 收敛至 `.clickable` 修饰 |
| 19 | Dashboard 卡片 | 自绘结构 | 统一全局 stat-card（渐变图标+数值+标签） |

### 4. 性能与工程（P2）

| # | 项目 | 优化前 | 优化后 |
|---|---|---|---|
| 20 | Element Plus 引入 | 全量 import + 全量 CSS | 按需引入 + 编程式 API 样式副作用 + el-config-provider(zhCn) |
| 21 | 图标注册 | 全量注册（拟删） | 保留并注释原因：Dashboard 快捷入口后端下发图标名动态渲染 |
| 22 | Vite 分包 | 单 chunk 超限告警 | vue-vendor 手动分包；element-plus 保持默认（sideEffects 仅样式，tree-shaking 有效）；chunkSizeWarningLimit 1500 |
| 23 | favicon | 引用缺失文件（404） | 新增 `/favicon.svg` |
| 24 | 滚动条样式 | reset/index 双份 | 保留 index.scss 一份 |
| 25 | scoped 重复样式 | 47 处与全局重复 | 全部清理，保留合法局部变体（TaskDetail card-header） |
| 26 | Dashboard 数据加载 | 无 onActivated / 无错误提示 | onActivated 刷新 + ElMessage.error 兜底 |

---

## 三、修改文件清单（本次优化涉及）

| 层级 | 文件 | 变更 |
|---|---|---|
| 布局 | frontend/src/layout/AppLayout.vue | keep-alive key、折叠按钮可访问性、菜单标题对比度 |
| 入口 | frontend/src/main.ts | 按需引入改造 |
| 入口 | frontend/src/App.vue | el-config-provider 包裹 |
| 全局样式 | frontend/src/styles/index.scss | 设计规范统一 + 响应式兜底 + focus-visible |
| 全局样式 | frontend/src/styles/reset.scss | 滚动条去重 |
| 登录 | frontend/src/views/login/Login.vue | 360px 适配 |
| 页面 | dashboard/index.vue | 重写对齐规范 |
| 页面 | archive、delivery、project/detail 等 | 栅格响应式 + 对话框属性 + 样式去重 |
| 页面 | 24 个含表单弹窗的视图 | close-on-click-modal 批量注入 |
| 构建 | frontend/vite.config.ts | manualChunks + chunkSizeWarningLimit |
| 资源 | frontend/public/favicon.svg | 新增 |
| 资源 | frontend/index.html | favicon 引用 |

---

## 四、未纳入本次范围的事项

1. **Sass legacy-js-api 弃用警告**：既有构建链问题，升级 dart-sass/修改 vite css.preprocessorOptions 属独立技术债，避免与 UI 优化混杂，建议单独立项。
2. **数据库遗留 unzhihua 账号行**：数据层问题，建议后端清理种子外的历史数据。
3. **lihailong/123456 等其他种子账号**：本次仅验证 sunzhihua 主账号路径。
