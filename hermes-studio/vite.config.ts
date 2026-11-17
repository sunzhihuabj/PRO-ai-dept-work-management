import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// web/ 为前端源码根；构建产物输出到 web/dist，由 BFF(server/index.mjs) 静态托管
export default defineConfig({
  root: 'web',
  plugins: [vue()],
  server: {
    port: 8651,
    proxy: {
      // 开发期前端 → BFF(8650) → Hermes(8642)，与生产同链路
      '/api': { target: 'http://127.0.0.1:8650', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        // 重依赖拆分：element-plus / markdown 渲染链路独立分包，多页共享缓存
        manualChunks: {
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          'markdown-render': ['markdown-it', 'highlight.js']
        }
      }
    }
  }
})
