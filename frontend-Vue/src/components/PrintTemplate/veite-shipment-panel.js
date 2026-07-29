import { PRINT_LOGO_URL } from '@/utils/print'

/**
 * 威特送货单打印模板。
 *
 * 布局按纸张尺寸动态生成，不再写死坐标：
 *  - panel 的 width/height 单位是 mm，元素 left/top/width/height 单位是 pt（1mm ≈ 2.8346pt）；
 *  - paperHeader 以上、paperFooter 以下的元素属于页眉/页脚，hiprint 会在每一页重复渲染并固定位置；
 *  - 中间只放商品表格，行数多时自动分页，不会再压到合计/签名那几行。
 */

const PT_PER_MM = 2.8346
const mm = (v) => Math.round(v * PT_PER_MM * 100) / 100

const COMPANY_NAME = '威特液压设备有限公司'
const COMPANY_ADDRESS = '福建省泉州市鲤城区义全街303号'
const COMPANY_TEL = 'TEL: 13959893950'
const BANK_INFO = '开户行：工商银行　　户名：余惠萍　　卡号：6222 0814 0800 0472 036'

/**
 * 可选纸张。width/height 单位 mm。
 * compact：矮版纸（针式联单），字号行距压缩。
 * autoCompletion：自动补空白行铺满表格区域（会把备注签名区顶到纸张底部，默认关）。
 * minRows：明细不足时补到几行空行，让单据看起来是画好格子的完整表。
 * 明细放不下时由 hiprint 自动拆页，每页重复表头页眉页脚，合计只出现在最后一页。
 */
export const SHIPMENT_PAPER_SIZES = [
  { key: 'a4', name: 'A4 (210×297mm)', width: 210, height: 297, compact: false, autoCompletion: false, minRows: 8 },
  { key: 'triple-241x140', name: '针式二等分 (241×140mm)', width: 241, height: 140, compact: true, autoCompletion: false, minRows: 5 }
]

export const DEFAULT_SHIPMENT_PAPER_SIZE = 'a4'

export function getShipmentPaperSize(key) {
  return SHIPMENT_PAPER_SIZES.find((it) => it.key === key)
    || SHIPMENT_PAPER_SIZES.find((it) => it.key === DEFAULT_SHIPMENT_PAPER_SIZE)
}

/** 商品表格列：宽度按整体宽度的百分比分配，保证列宽之和正好等于表格宽度 */
const TABLE_COLUMNS = [
  // summaryNumFormat 是小数位数，hiprint 内部写法是 `tableSummaryNumFormat || 2`，
  // 所以 0 位小数必须写成字符串 '0'，否则会被当成未设置而按 2 位输出
  { title: '序号', field: 'index', percent: 7, align: 'center', summaryText: '合计' },
  { title: '商品名称', field: 'itemName', percent: 31, align: 'left' },
  { title: '规格名称', field: 'skuName', percent: 28, align: 'left' },
  { title: '数量', field: 'quantity', percent: 12, align: 'center', summary: 'sum', summaryNumFormat: '0' },
  { title: '金额(元)', field: 'amount', percent: 22, align: 'right', summary: 'sum', summaryNumFormat: 2 }
]

function buildPanel(size) {
  const compact = size.compact
  const W = mm(size.width)
  const H = mm(size.height)
  const margin = mm(compact ? 4 : 8)
  const contentW = Math.round((W - margin * 2) * 100) / 100
  const right = margin + contentW

  const fs = compact
    ? { company: 15, title: 15, info: 10, table: 10, footer: 9.5 }
    : { company: 19, title: 16, info: 11, table: 11, footer: 10.5 }
  const rowH = compact ? 15 : 17
  // 表格正文行高，针式纸也要留够手写和复写的空间
  const bodyRowH = compact ? 20 : 24

  const elements = []
  const text = (options) => elements.push({ options, printElementType: { type: 'text' } })

  // ---------------- 页眉 ----------------
  let y = margin

  elements.push({
    options: {
      left: margin, top: y,
      width: mm(compact ? 30 : 38), height: mm(compact ? 12 : 16),
      src: PRINT_LOGO_URL, fit: 'contain'
    },
    printElementType: { title: '图片', type: 'image' }
  })

  text({
    left: margin, top: y, width: contentW, height: fs.company + 8,
    title: compact ? `${COMPANY_NAME}　送货单` : COMPANY_NAME,
    fontSize: fs.company, fontWeight: '600', letterSpacing: compact ? 0.5 : 1.5,
    textAlign: 'center', textContentVerticalAlign: 'middle'
  })
  y += fs.company + 10

  text({
    left: margin, top: y, width: contentW, height: fs.info + 4,
    title: `${COMPANY_ADDRESS}　　${COMPANY_TEL}`,
    fontSize: compact ? 8.5 : 9, textAlign: 'center', textContentVerticalAlign: 'middle'
  })
  y += fs.info + 6

  if (!compact) {
    text({
      left: margin, top: y, width: contentW, height: fs.title + 10,
      title: '送 货 单', fontFamily: 'SimSun', fontSize: fs.title, fontWeight: '600',
      letterSpacing: 6, textAlign: 'center', textContentVerticalAlign: 'middle'
    })
    y += fs.title + 12
  }

  // 单据信息：先单据（单号/日期/业务单号），再收货方（客户/电话/地址）
  const infoRows = compact
    ? [
      [
        { title: '送货单号', field: 'orderNo', span: 40 },
        { title: '日期', field: 'createTime', span: 26 },
        { title: '业务单号', field: 'bizOrderNo', span: 34 }
      ],
      [
        { title: '客户', field: 'merchantName', span: 40 },
        { title: '联系电话', field: 'merchantPhone', span: 26 },
        // 地址最长，放最后一格并给足宽度
        { title: '送货地址', field: 'merchantAddress', span: 34 }
      ]
    ]
    : [
      [
        { title: '送货单号', field: 'orderNo', span: 55 },
        { title: '日期', field: 'createTime', span: 45 }
      ],
      [
        { title: '客户', field: 'merchantName', span: 55 },
        { title: '业务单号', field: 'bizOrderNo', span: 45 }
      ],
      [
        { title: '联系电话', field: 'merchantPhone', span: 100 }
      ],
      [
        // 地址单独占整行，长地址不会被挤断
        { title: '送货地址', field: 'merchantAddress', span: 100 }
      ]
    ]

  infoRows.forEach((row) => {
    const totalSpan = row.reduce((sum, col) => sum + (col.span || 1), 0)
    let x = margin
    row.forEach((col) => {
      const colW = Math.round((contentW * (col.span || 1) / totalSpan) * 100) / 100
      text({
        left: x, top: y, width: colW - 4, height: rowH,
        title: col.title, field: col.field,
        fontSize: fs.info, textContentVerticalAlign: 'middle'
      })
      x = Math.round((x + colW) * 100) / 100
    })
    y += rowH
  })

  const paperHeader = Math.round((y + 4) * 100) / 100

  // 备注/签名区固定在纸张底部（paperFooter 以下即页脚区，每页重复）。
  // 让它跟着表格流动看起来更紧凑，但表格填满一页时签名会被挤到下一页，
  // 出现"最后一页只有签名、一行明细都没有"的孤儿页，所以这里固定。
  const footerRowCount = compact ? 2 : 3
  const footerH = (rowH + 2) * footerRowCount + (compact ? 4 : 8)
  const paperFooter = Math.round((H - margin - footerH) * 100) / 100

  // ---------------- 商品表格 ----------------
  const tableTop = paperHeader + 2
  // 设计高度只是个基准，实际渲染时表格按行数伸缩，后面的元素跟着位移
  const tableHeight = bodyRowH * (compact ? 5 : 8) + rowH + 4
  let used = 0
  const columns = TABLE_COLUMNS.map((col, idx) => {
    // 最后一列吃掉四舍五入的零头，保证列宽之和 === 表格宽度
    const width = idx === TABLE_COLUMNS.length - 1
      ? Math.round((contentW - used) * 100) / 100
      : Math.round(contentW * col.percent) / 100
    used = Math.round((used + width) * 100) / 100
    return {
      title: col.title,
      field: col.field,
      columnId: col.field,
      align: col.align,
      width,
      checked: true,
      fixed: false,
      rowspan: 1,
      colspan: 1,
      ...(col.summary
        ? { tableSummary: col.summary, tableSummaryAlign: col.align, tableSummaryNumFormat: col.summaryNumFormat }
        : {}),
      ...(col.summaryText ? { tableSummaryText: col.summaryText } : {})
    }
  })

  elements.push({
    options: {
      left: margin,
      top: tableTop,
      width: contentW,
      height: tableHeight,
      field: 'table',
      fontSize: fs.table,
      tableHeaderFontSize: fs.table,
      tableHeaderFontWeight: '600',
      tableHeaderRowHeight: rowH + 4,
      tableBodyRowHeight: bodyRowH,
      // 分页时表头每页重复，合计行只出现在最后一页
      tableFooterRepeat: 'last',
      autoCompletion: !!size.autoCompletion,
      columns: [columns]
    },
    printElementType: { title: '表格', type: 'table' }
  })

  // ---------------- 页脚：备注 / 签名 / 银行信息 ----------------
  let fy = paperFooter + (compact ? 2 : 4)

  text({
    left: margin, top: fy, width: contentW, height: rowH,
    title: '备注', field: 'remark', fontSize: fs.footer, textContentVerticalAlign: 'middle'
  })
  fy += rowH + 2

  const signCols = [
    { title: '制单人', field: 'createBy' },
    { title: '送货人', field: 'deliveryBy' },
    { title: '收货人签字', field: 'receiveBy' }
  ]
  const signW = Math.round((contentW / signCols.length) * 100) / 100
  signCols.forEach((col, idx) => {
    text({
      left: margin + signW * idx, top: fy, width: signW - 4, height: rowH,
      title: col.title, field: col.field, fontSize: fs.footer, textContentVerticalAlign: 'middle'
    })
  })
  fy += rowH + 2

  if (!compact) {
    text({
      left: margin, top: fy, width: contentW, height: rowH,
      title: BANK_INFO, fontSize: fs.footer - 1, textContentVerticalAlign: 'middle'
    })
  }

  return {
    index: 0,
    name: size.name,
    width: size.width,
    height: size.height,
    paperHeader,
    paperFooter,
    printElements: elements,
    paperNumberDisabled: false,
    paperNumberContinue: true,
    paperNumberFormat: '第${paperNo}页/共${paperCount}页',
    paperNumberLeft: Math.round(W - margin - mm(28)),
    paperNumberTop: Math.round(H - margin - (compact ? 8 : 10))
  }
}

/** 按纸张 key 生成打印模板 */
export function buildVeiteShipmentPanel(sizeKey) {
  return { panels: [buildPanel(getShipmentPaperSize(sizeKey))] }
}

export default buildVeiteShipmentPanel(DEFAULT_SHIPMENT_PAPER_SIZE)
