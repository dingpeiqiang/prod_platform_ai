import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig(({ mode }) => {
  const isProd = mode === 'production'
  // Commercial backend: Spring Boot backend-app (6174)
  const apiTarget = 'http://localhost:6174'
  const wsTarget = 'ws://localhost:6174'
  // FastAPI backend (LLM 配置管理等 Python 专属接口)
  const fastApiTarget = 'http://localhost:8000'

  return {
    plugins: [vue()],

    resolve: {
      alias: {
        '@': resolve(__dirname, 'src')
      },
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },

    cacheDir: './node_modules/.vite',

    server: {
      host: '0.0.0.0',
      port: 5173,
      open: false,
      strictPort: false,
      proxy: {
        '/api': {
          target: apiTarget,
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
          target: wsTarget,
          ws: true,
          changeOrigin: true
        }
      },
      hmr: {
        overlay: true
      },
      warmup: {
        clientFiles: [
          './index.html',
          './src/main.js',
          './src/App.vue'
        ]
      }
    },

    build: {
      outDir: 'dist',
      assetsDir: 'assets',
      sourcemap: isProd ? false : 'inline',
      minify: isProd ? 'esbuild' : false,
      chunkSizeWarningLimit: 1500,
      cache: true,
      reportCompressedSize: isProd,
      esbuild: {
        drop: isProd ? ['console', 'debugger'] : []
      },
      rollupOptions: {
        output: {
          manualChunks: {
            'vue-core': ['vue', 'pinia'],
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
            'vue-flow': ['@vue-flow/core', '@vue-flow/background', '@vue-flow/controls', '@vue-flow/minimap'],
            'codemirror': ['codemirror', '@codemirror/autocomplete', '@codemirror/commands', '@codemirror/lang-json', '@codemirror/language', '@codemirror/state', '@codemirror/theme-one-dark', '@codemirror/view'],
            'monaco': ['monaco-editor'],
            'ace': ['ace-builds'],
            'jsoneditor': ['jsoneditor'],
            'form-create': ['form-create-designer'],
            'axios': ['axios'],
            'marked': ['marked'],
            'lucide': ['lucide-vue-next'],
            'uuid': ['uuid']
          },
          chunkFileNames: 'assets/js/[name]-[hash].js',
          entryFileNames: 'assets/js/[name]-[hash].js',
          assetFileNames: 'assets/[ext]/[name]-[hash].[ext]'
        }
      }
    },

    optimizeDeps: {
      include: [
        'vue', 'pinia', 'element-plus', '@element-plus/icons-vue',
        '@vue-flow/core', '@vue-flow/background', '@vue-flow/controls', '@vue-flow/minimap',
        'axios', 'marked', 'lucide-vue-next', 'uuid',
        'codemirror', '@codemirror/autocomplete', '@codemirror/commands', '@codemirror/lang-json',
        '@codemirror/language', '@codemirror/state', '@codemirror/theme-one-dark', '@codemirror/view'
      ],
      exclude: [],
      esbuildOptions: {
        target: 'esnext'
      }
    }
  }
})
