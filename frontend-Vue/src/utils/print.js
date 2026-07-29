import printLockCss from 'vue-plugin-hiprint/dist/print-lock.css?raw'

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

/**
 * 单元格样式必须在【当前页面】也生效：
 * hiprint 是先在主文档里把表格渲染到临时容器量高度、再决定每页放几行，
 * 只在打印窗口里加样式的话，量的是没有内边距的矮行高，打印时行变高就会
 * 溢出到备注/签名上面。所以这里模块加载时就把同一份样式注入主文档。
 */
let cellCssInjected = false

export function ensurePrintTableCellCss() {
  if (cellCssInjected || typeof document === 'undefined') {
    return
  }
  const style = document.createElement('style')
  style.setAttribute('data-hiprint-cell', '')
  style.innerHTML = PRINT_TABLE_CELL_CSS
  document.head.appendChild(style)
  cellCssInjected = true
}

ensurePrintTableCellCss()

/**
 * hiprint 打印窗口的样式。
 * 原来引的是 OSS 上的 print-lock.css，内网/断网环境会加载失败导致排版错乱，
 * 这里直接把 npm 包里自带的同一份样式内联进打印窗口。
 */
export function printStyleHandler() {
  return `<style media="print">${printLockCss}</style><style>${PRINT_TABLE_CELL_CSS}</style>`
}

/**
 * 打印模板里的公司 logo 地址。
 * 走后端 /wms/image/logo（已在 security.excludes 中放行，无需 token），
 * 用 baseApi 前缀拼相对地址，避免写死某台机器的 host。
 */
export const PRINT_LOGO_URL = `${import.meta.env.VITE_APP_BASE_API}/wms/image/logo`

let logoCache

/**
 * 取 logo 的 base64。
 * hiprint 是把模板塞进隐藏 iframe 再调打印，图片来不及加载或接口不通时
 * 纸上会留一个空白方框；这里先在主页面把图取回来转成 data URI，
 * 拿不到就返回 null，模板直接不画这个元素，不会留空框。
 */
export function loadPrintLogo() {
  if (logoCache !== undefined) {
    return Promise.resolve(logoCache)
  }
  return fetch(PRINT_LOGO_URL)
    .then(res => (res.ok ? res.blob() : Promise.reject(new Error(`logo ${res.status}`))))
    .then(blob => new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result)
      reader.onerror = reject
      reader.readAsDataURL(blob)
    }))
    .then(dataUrl => {
      logoCache = dataUrl
      return dataUrl
    })
    .catch(err => {
      console.warn('打印 logo 加载失败，本次不打印 logo：', err)
      logoCache = null
      return null
    })
}
