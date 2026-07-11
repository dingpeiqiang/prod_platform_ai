import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  
  // 路径别名配置
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  
  // 开发服务器配置
  server: {
    host: '0.0.0.0',  // 允许外部访问
    port: 5173,
    open: false,  // 不自动打开浏览器
    strictPort: false,  // 端口被占用时自动尝试下一个
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        rewrite: (path) => path,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            if (proxyRes.headers['content-type']?.includes('text/event-stream')) {
              proxyRes.headers['cache-control'] = 'no-cache'
              proxyRes.headers['x-accel-buffering'] = 'no'
            }
          })
        }
      },
      '/ws': {
        target: 'ws://localhost:8000',
        ws: true,
        changeOrigin: true
      }
    },
    // 热更新配置
    hmr: {
      overlay: true  // 显示错误覆盖层
    }
  },
  
  // 构建配置
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,  // 生产环境不生成 sourcemap
    minify: 'terser',  // 使用 terser 压缩
    terserOptions: {
      compress: {
        drop_console: true,  // 移除 console.log
        drop_debugger: true  // 移除 debugger
      }
    },
    rollupOptions: {
      output: {
        // 分包策略
        manualChunks: {
          'vue-vendor': ['vue'],
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          'axios': ['axios']
        }
      }
    },
    chunkSizeWarningLimit: 1000  // chunk 大小警告限制（KB）
  },
  
  // 优化依赖预构建
  optimizeDeps: {
    include: [
      'vue',
      'element-plus',
      '@element-plus/icons-vue',
      'axios'
    ]
  }
})
