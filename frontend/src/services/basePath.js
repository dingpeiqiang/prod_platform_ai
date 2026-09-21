/**
 * 部署前缀工具
 *
 * 前端可部署在子路径前缀下（如 /prod-ai/），通过 vite 的 base 控制
 * （构建时设 VITE_BASE_PATH=/prod-ai/，见 vite.config.js）。
 * import.meta.env.BASE_URL 在构建产物中即等于该 base（默认 '/'）。
 *
 * 统一在此拼接运行时请求前缀：静态资源由 vite base 自动处理，
 * 代码内的 /api、/ws 绝对路径必须经 joinBasePath 包裹才能在子路径下工作。
 */

const BASE_URL = import.meta.env.BASE_URL || '/'

/** 规范化前缀：确保以 / 开头、以 / 结尾（根路径返回 ''） */
export function normalizeBase(base = BASE_URL) {
  if (!base || base === '/') {
    return ''
  }
  return base.startsWith('/') ? base.replace(/\/+$/, '') : `/${base.replace(/\/+$/, '')}`
}

/** 拼接前缀与以 / 开头的路径，如 joinBasePath('/api/v1/x') → '/prod-ai/api/v1/x' */
export function joinBasePath(path) {
  const base = normalizeBase()
  if (!base || !path.startsWith('/')) {
    return path
  }
  return `${base}${path}`
}
