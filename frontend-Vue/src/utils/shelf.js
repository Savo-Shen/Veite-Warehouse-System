/**
 * 解析位置编码为货架坐标。
 * 约定格式：楼层-排列-格，例如 "2-B2-3" 表示 2楼 / B排 / 第2列 / 第3格。
 *  - 第1段：楼层（数字）
 *  - 第2段：排(字母) + 列(数字)，如 B2
 *  - 第3段：格（数字）
 * 解析失败返回 null（该位置不会出现在示意图上）。
 *
 * @param {string} code 位置编码
 * @returns {{floor:number,row:string,col:number,cell:number}|null}
 */
export function parseLocationCode(code) {
  if (!code) return null
  const parts = String(code).trim().split('-')
  if (parts.length < 3) return null
  const floor = parseInt(parts[0], 10)
  const m = /^([A-Za-z]+)(\d+)$/.exec(parts[1].trim())
  if (!m) return null
  const row = m[1].toUpperCase()
  const col = parseInt(m[2], 10)
  const cell = parseInt(parts[2], 10)
  if ([floor, col, cell].some(n => Number.isNaN(n))) return null
  return { floor, row, col, cell }
}

/** 位置是否能解析出货架坐标（决定是否显示示意图） */
export function hasShelfCoord(loc) {
  return !!(loc && parseLocationCode(loc.locationCode))
}
