/**
 * 地图标记信息气泡的公共构建方法
 * 供「地图总览」与「往来单位」页面的地图模式共用
 */
import { listByIds } from '@/api/system/oss'

export const WAREHOUSE_COLOR = '#F56C6C'

export function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
}

function infoLine(label, val) {
  return val ? `<div style="margin-top:4px;color:#606266"><span style="color:#909399">${label}：</span>${escapeHtml(val)}</div>` : ''
}

// imageIds 字符串 → 图片 URL 数组（带缓存，OSS ID 不变则不重复请求）
const ossUrlCache = new Map()

export async function fetchOssUrls(imageIds) {
  if (!imageIds) return []
  if (ossUrlCache.has(imageIds)) return ossUrlCache.get(imageIds)
  try {
    const res = await listByIds(imageIds)
    const urls = (res.data || []).map(item => item.url).filter(Boolean)
    ossUrlCache.set(imageIds, urls)
    return urls
  } catch (e) {
    return []
  }
}

function imageRowHtml(imageUrls) {
  if (!imageUrls || !imageUrls.length) return ''
  const imgs = imageUrls
    .map(u => `<a href="${escapeHtml(u)}" target="_blank" title="点击查看大图"><img src="${escapeHtml(u)}" style="width:64px;height:64px;object-fit:cover;border-radius:4px;border:1px solid #ebeef5"/></a>`)
    .join('')
  return `<div style="margin-top:8px;display:flex;gap:6px;flex-wrap:wrap;max-width:280px">${imgs}</div>`
}

/** 往来单位信息气泡（imageUrls 可选，传入则在下方显示图片） */
export function buildMerchantInfoHtml(m, color, typeLabelText, imageUrls) {
  return `
    <div style="font-size:13px;line-height:1.6;max-width:300px;padding:4px 2px">
      <div style="font-weight:bold;font-size:14px;color:#303133">
        <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${color};margin-right:6px"></span>${escapeHtml(m.merchantName)}
        <span style="font-weight:normal;font-size:12px;color:${color};margin-left:6px">${escapeHtml(typeLabelText)}</span>
      </div>
      ${infoLine('编号', m.merchantCode)}
      ${infoLine('地址', m.address)}
      ${infoLine('联系人', m.contactPerson)}
      ${infoLine('电话', m.mobile || m.tel)}
      ${imageRowHtml(imageUrls)}
    </div>`
}

/** 仓库信息气泡 */
export function buildWarehouseInfoHtml(w) {
  return `
    <div style="font-size:13px;line-height:1.6;max-width:300px;padding:4px 2px">
      <div style="font-weight:bold;font-size:14px;color:#303133">
        <span style="display:inline-block;width:12px;height:12px;border-radius:3px;background:${WAREHOUSE_COLOR};margin-right:6px"></span>${escapeHtml(w.warehouseName)}
        <span style="font-weight:normal;font-size:12px;color:${WAREHOUSE_COLOR};margin-left:6px">仓库</span>
      </div>
      ${infoLine('编号', w.warehouseCode)}
      ${infoLine('地址', w.address)}
      ${infoLine('备注', w.remark)}
    </div>`
}

// 每次打开气泡递增序号，防止上一个标记的图片异步加载完后覆盖当前气泡
function nextSeq(infoWindow) {
  infoWindow.__seq = (infoWindow.__seq || 0) + 1
  return infoWindow.__seq
}

/**
 * 打开往来单位气泡：先展示基本信息，若有图片则异步补充
 */
export async function openMerchantInfoWindow(infoWindow, map, position, m, color, typeLabelText) {
  const seq = nextSeq(infoWindow)
  infoWindow.setContent(buildMerchantInfoHtml(m, color, typeLabelText))
  infoWindow.open(map, position)
  if (m.imageIds) {
    const urls = await fetchOssUrls(m.imageIds)
    if (urls.length && infoWindow.__seq === seq && infoWindow.getIsOpen()) {
      infoWindow.setContent(buildMerchantInfoHtml(m, color, typeLabelText, urls))
    }
  }
}

/** 打开仓库气泡 */
export function openWarehouseInfoWindow(infoWindow, map, position, w) {
  nextSeq(infoWindow)
  infoWindow.setContent(buildWarehouseInfoHtml(w))
  infoWindow.open(map, position)
}
