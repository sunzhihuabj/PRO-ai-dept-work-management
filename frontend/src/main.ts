import { createApp } from 'vue'
import { createPinia } from 'pinia'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElLoading } from 'element-plus'

import App from './App.vue'
import router from './router'
import './router/permission'
import './styles/index.scss'

// 编程式调用的反馈类组件样式（不经过模板编译解析，需手动引入样式副作用）
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'

const app = createApp(App)

// 图标全局注册：Dashboard 快捷入口由后端返回图标名动态渲染，需要全量注册
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
// 注册 v-loading 指令（按需引入模式下不再由全量安装提供）
app.use(ElLoading)
app.mount('#app')
