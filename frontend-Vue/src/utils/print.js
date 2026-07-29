import printLockCss from 'vue-plugin-hiprint/dist/print-lock.css?raw'

/**
 * hiprint 打印窗口的样式。
 * 原来引的是 OSS 上的 print-lock.css，内网/断网环境会加载失败导致排版错乱，
 * 这里直接把 npm 包里自带的同一份样式内联进打印窗口。
 */
/**
 * 表格单元格补充样式：hiprint 默认单元格行高很挤，商品名称一换行两行就贴在一起，
 * 这里放开行距并给上下留白，同时让长文本按字断行不撑破列宽。
 */
export const PRINT_TABLE_CELL_CSS = `
.hiprint-printElement-tableTarget td,
.hiprint-printElement-tableTarget th {
  line-height: 1.6;
  padding-top: 3pt;
  padding-bottom: 3pt;
  word-break: break-all;
  white-space: normal;
  vertical-align: middle;
}
`

export function printStyleHandler() {
  return `<style media="print">${printLockCss}${PRINT_TABLE_CELL_CSS}</style>`
}

/**
 * 打印模板里的公司 logo 地址。
 * 走后端 /wms/image/logo（已在 security.excludes 中放行，无需 token），
 * 用 baseApi 前缀拼相对地址，避免写死某台机器的 host。
 */
export const PRINT_LOGO_URL = `${import.meta.env.VITE_APP_BASE_API}/wms/image/logo`
