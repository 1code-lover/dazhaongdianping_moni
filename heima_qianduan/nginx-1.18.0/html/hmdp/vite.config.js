import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  // 生产预览配置：对外通过已放行的 80 端口访问，静态资源 + API 反代到本机 8081
  preview: {
    port: 80,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      '/imgs': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/blogs': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('element-plus')) {
            return 'element-plus'
          }
          if (id.includes('vue') || id.includes('pinia')) {
            return 'vue-vendor'
          }
          if (id.includes('axios')) {
            return 'http-vendor'
          }
          return 'vendor'
        }
      }
    }
  }
})
