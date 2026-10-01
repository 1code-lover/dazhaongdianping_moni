/**
 * 用户定位工具
 * 优先级：localStorage 缓存(1h) → 浏览器定位(仅安全上下文) → 默认杭州市中心
 * 注意：http 非安全上下文下 navigator.geolocation 不可用，静默降级
 */

// 默认定位：杭州武林广场（演示商户均位于杭州）
export const DEFAULT_LOCATION = { x: 120.1653, y: 30.2765, source: 'default' }

const CACHE_KEY = 'hmdp-location'
const CACHE_TTL = 60 * 60 * 1000 // 1小时

/**
 * 获取用户定位（异步）
 * @returns {Promise<{x: number, y: number, source: string}>} source: gps / cache / default
 */
export function getLocation() {
  // 1.读缓存
  try {
    const cached = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null')
    if (cached && Date.now() - cached.time < CACHE_TTL) {
      return Promise.resolve({ ...cached.loc, source: 'cache' })
    }
  } catch (e) { /* 缓存损坏忽略 */ }

  // 2.浏览器定位（仅 HTTPS/localhost 等安全上下文可用）
  if (window.isSecureContext && navigator.geolocation) {
    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const loc = { x: pos.coords.longitude, y: pos.coords.latitude, source: 'gps' }
          try {
            localStorage.setItem(CACHE_KEY, JSON.stringify({ time: Date.now(), loc }))
          } catch (e) { /* 隐私模式忽略 */ }
          resolve(loc)
        },
        () => resolve({ ...DEFAULT_LOCATION }),   // 拒绝/超时 → 默认
        { timeout: 5000, maximumAge: 300000 }
      )
    })
  }

  // 3.降级默认
  return Promise.resolve({ ...DEFAULT_LOCATION })
}

/**
 * 距离格式化（后端单位为千米）
 * @param {number} km 千米
 * @returns {string} 如 "350米" / "3.2km"
 */
export function formatDistance(km) {
  if (km === null || km === undefined || isNaN(km)) return ''
  if (km < 1) return `${Math.round(km * 1000)}米`
  return `${km.toFixed(1)}km`
}
