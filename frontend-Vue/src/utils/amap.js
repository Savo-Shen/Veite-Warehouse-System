/**
 * 高德地图 JS API 2.0 加载器
 *
 * Key 的读取优先级：
 *   1. 系统参数（sys_config）：wms.amap.key / wms.amap.securityCode
 *      在「基础资料 → 环境配置」页面填写，改完刷新页面即可生效，无需重新打包
 *   2. 构建期环境变量（兜底）：VITE_AMAP_KEY / VITE_AMAP_SECURITY_CODE
 *
 * Key 申请地址：https://console.amap.com/dev/key/app（类型必须是「Web端(JS API)」）
 */
import { getConfigKey } from '@/api/system/config'

export const AMAP_KEY_CONFIG = 'wms.amap.key'
export const AMAP_SECURITY_CONFIG = 'wms.amap.securityCode'
export const ENV_CONFIG_ROUTE = '/basic/envConfig'
export const MAP_OVERVIEW_ROUTE = '/wms/map'

const ENV_KEY = import.meta.env.VITE_AMAP_KEY
const ENV_SECURITY_CODE = import.meta.env.VITE_AMAP_SECURITY_CODE

// 一次性加载常用插件，避免各页面重复按需加载
const PLUGINS = [
  'AMap.Geocoder',
  'AMap.AutoComplete',
  'AMap.PlaceSearch',
  'AMap.ToolBar',
  'AMap.Scale'
]

let configPromise = null
let loadPromise = null
let loadedKey = null

function fetchConfigValue(key) {
  return getConfigKey(key)
    .then(res => (res.msg || '').trim())
    .catch(() => '')
}

/** 获取地图配置 { key, securityCode }（带缓存） */
export function getAMapConfig() {
  if (!configPromise) {
    configPromise = Promise.all([
      fetchConfigValue(AMAP_KEY_CONFIG),
      fetchConfigValue(AMAP_SECURITY_CONFIG)
    ]).then(([key, securityCode]) => ({
      key: key || ENV_KEY || '',
      securityCode: securityCode || ENV_SECURITY_CODE || ''
    }))
  }
  return configPromise
}

/** 清除配置缓存（在环境配置页保存后调用） */
export function resetAMapConfigCache() {
  configPromise = null
}

/** SDK 已用某个 Key 加载后，本页内换 Key 需要刷新页面才能生效 */
export function getLoadedKey() {
  return loadedKey
}

/** 是否已配置高德 Key */
export async function checkAMapConfigured() {
  const cfg = await getAMapConfig()
  return !!cfg.key
}

/** 加载高德地图 SDK，返回 Promise<AMap> */
export async function loadAMap() {
  const cfg = await getAMapConfig()
  if (!cfg.key) {
    throw new Error('未配置高德地图 Key，请在「基础资料 → 环境配置」中填写')
  }
  if (window.AMap) {
    return window.AMap
  }
  if (!loadPromise) {
    if (cfg.securityCode) {
      window._AMapSecurityConfig = { securityJsCode: cfg.securityCode }
    }
    loadPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.type = 'text/javascript'
      script.async = true
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${cfg.key}&plugin=${PLUGINS.join(',')}`
      script.onload = () => {
        if (window.AMap) {
          loadedKey = cfg.key
          resolve(window.AMap)
        } else {
          reject(new Error('高德地图 SDK 加载失败'))
        }
      }
      script.onerror = () => {
        loadPromise = null
        reject(new Error('高德地图 SDK 加载失败，请检查网络或 Key 是否有效'))
      }
      document.head.appendChild(script)
    })
  }
  return loadPromise
}

/** 来往单位类型 → 标记颜色（与 merchant_type 字典的 list_class 保持一致） */
export const MERCHANT_TYPE_COLORS = {
  1: '#409EFF', // 客户
  2: '#67C23A', // 供应商
  3: '#E6A23C'  // 物流单位
}

export const MERCHANT_TYPE_DEFAULT_COLOR = '#909399'
