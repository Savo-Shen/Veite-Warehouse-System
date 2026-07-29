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

/** 毫米转 pt。hiprint 的打印偏移单位是 pt，外部也要用 */
export const mmToPt = mm

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
 * marginLeft / contentWidth / marginTop / marginBottom：版心(mm)。
 *   针式连续纸的可打印区不是以纸张居中的，而是从纸左边缘起算的固定宽度
 *   （80列针打一般是 8 英寸 ≈ 203mm），超过这个位置打印头够不到，右边就被截掉。
 *   左边 12.7mm 是走纸孔边条，但走纸孔本身只到离纸边约 8.4mm
 *   （孔心 6.35mm、孔径 4mm），实际用整张纸不撕边条，所以左边距取 9mm 就够，
 *   刚好避开孔位又不浪费版面。右边缘 202mm 卡在 203mm 打印极限内。
 */
export const SHIPMENT_PAPER_SIZES = [
  {
    key: 'a4', name: 'A4 (210×297mm)', width: 210, height: 297,
    marginLeft: 8, contentWidth: 194, marginTop: 8, marginBottom: 10,
    compact: false, autoCompletion: false, minRows: 8
  },
  {
    key: 'triple-241x140', name: '针式二等分 (241×140mm)', width: 241, height: 140,
    marginLeft: 9, contentWidth: 193, marginTop: 4, marginBottom: 11,
    compact: true, autoCompletion: false, minRows: 5
  }
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

function buildPanel(size, options) {
  const compact = size.compact
  // 取不到 logo（接口不通/未部署）时直接不画，避免纸上留一个空白方框
  const logo = options && options.logo
  const W = mm(size.width)
  const H = mm(size.height)
  // 版心：左边距 + 内容宽度（不是纸宽减两倍边距，针式纸的可打印区并不居中）
  const margin = mm(size.marginLeft)
  const marginY = mm(size.marginTop)
  const marginBottom = mm(size.marginBottom)
  const contentW = mm(size.contentWidth)

  const fs = compact
    ? { company: 15, title: 21, side: 10, info: 11, table: 11, footer: 11 }
    : { company: 18, title: 26, side: 11, info: 12, table: 12, footer: 11.5 }
  const rowH = compact ? 17 : 19
  // 表格正文行高，针式纸也要留够手写和复写的空间
  const bodyRowH = compact ? 20 : 24
  // 实际针打测试中，宋体常规字重的横画偏弱、容易断线。恢复 600 字重并整体放大，
  // 让复写联上的细横画有足够击打密度；仍保留宋体和疏排，避免黑体式的笔画拥挤。
  const printWeight = '600'

  const elements = []
  const text = (options) => elements.push({
    options: { fontFamily: 'SimSun', fontWeight: printWeight, ...options },
    printElementType: { type: 'text' }
  })
  const hline = (o) => elements.push({
    options: { ...o, borderWidth: 0.75 },
    printElementType: { title: '横线', type: 'hline' }
  })

  // ---------------- 页眉：左 logo / 中 公司名+大标题 / 右 地址电话 ----------------
  let y = marginY

  const logoW = mm(compact ? 44 : 48)
  const logoH = mm(compact ? 15 : 18)
  const sideW = mm(compact ? 63 : 70)
  const centerLeft = margin + logoW + mm(2)
  const centerW = Math.round((contentW - logoW - sideW - mm(4)) * 100) / 100

  if (logo) {
    elements.push({
      options: { left: margin, top: y, width: logoW, height: logoH, src: logo, fit: 'contain' },
      printElementType: { title: '图片', type: 'image' }
    })
  }

  // 公司名做疏排（拉开字距），和下面同样疏排的大标题成一套，不靠加粗撑场面
  text({
    left: centerLeft, top: y, width: centerW, height: fs.company + 6,
    title: COMPANY_NAME, fontSize: fs.company,
    letterSpacing: compact ? 2.5 : 3,
    textAlign: 'center', textContentVerticalAlign: 'middle'
  })

  text({
    left: centerLeft, top: y + fs.company + 9, width: centerW, height: fs.title + 8,
    title: '送 货 单', fontSize: fs.title,
    letterSpacing: compact ? 6 : 8, textAlign: 'center', textContentVerticalAlign: 'middle'
  })

  // 右上角：电话在上、地址在下。地址不再重复显示“地址”标签，
  // 给完整公司地址留足单行宽度，避免字号放大后换行。
  const sideLeft = Math.round((margin + contentW - sideW) * 100) / 100
  text({
    left: sideLeft, top: y + 3, width: sideW, height: fs.side + 5,
    title: `电　话：${COMPANY_TEL.replace('TEL: ', '')}`, fontSize: fs.side,
    textContentVerticalAlign: 'middle'
  })
  text({
    left: sideLeft, top: y + fs.side + 11, width: sideW, height: fs.side + 5,
    title: COMPANY_ADDRESS, fontSize: fs.side,
    textContentVerticalAlign: 'middle'
  })

  // 页眉整体高度取 logo 和中间标题块里更高的那个
  y += Math.max(logoH, fs.company + fs.title + 19) + (compact ? 4 : 8)

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
  // 底部三栏不是等分：最右侧用固定宽度的小块纵向放制单人、备注、页码，
  // 并锚在版心最右下角；送货人、收货人平分剩余空间。
  // 三项共用签名栏的高度，不再额外占一整行。
  const footerBandH = compact ? 39 : 42
  const footerMetaRowH = footerBandH / 3
  const footerH = footerBandH + (compact ? 0 : rowH + 3) + 4
  const paperFooter = Math.round((H - marginBottom - footerH) * 100) / 100

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
      fontWeight: printWeight,
      tableHeaderFontSize: fs.table,
      tableHeaderFontWeight: printWeight,
      fontFamily: 'SimSun',
      tableHeaderRowHeight: rowH + 4,
      tableBodyRowHeight: bodyRowH,
      // 分页时表头每页重复，合计行只出现在最后一页
      tableFooterRepeat: 'last',
      autoCompletion: !!size.autoCompletion,
      columns: [columns]
    },
    printElementType: { title: '表格', type: 'table' }
  })

  // ---------------- 页脚：送货人 / 收货人 / 制单人+备注+页码 ----------------
  let fy = paperFooter + (compact ? 2 : 4)

  const rightColW = mm(42)
  const signW = Math.round(((contentW - rightColW) / 2) * 100) / 100
  const signCols = [
    { title: '送货人', field: 'deliveryBy', line: true },
    { title: '收货人签字', field: 'receiveBy', line: true }
  ]
  signCols.forEach((col, idx) => {
    const left = Math.round((margin + signW * idx) * 100) / 100
    text({
      left, top: fy, width: signW - 6, height: rowH,
      title: col.title, field: col.field, fontSize: fs.footer, textContentVerticalAlign: 'top'
    })
    if (col.line) {
      hline({ left, top: fy + footerBandH - 4, width: signW - 10, height: 1 })
    }
  })

  const rightColLeft = Math.round((margin + signW * 2) * 100) / 100
  text({
    left: rightColLeft, top: fy, width: rightColW - 4, height: footerMetaRowH,
    title: '制单人', field: 'createBy', fontSize: fs.footer,
    textAlign: 'right', textContentVerticalAlign: 'middle'
  })
  text({
    left: rightColLeft, top: fy + footerMetaRowH, width: rightColW - 4, height: footerMetaRowH,
    title: '备注', field: 'remark', fontSize: fs.footer,
    textAlign: 'right', textContentVerticalAlign: 'middle'
  })
  const pageNoLeft = Math.round((margin + contentW - mm(28)) * 100) / 100
  const pageNoTop = Math.round((fy + footerMetaRowH * 2) * 100) / 100
  fy += footerBandH

  if (!compact) {
    text({
      left: margin, top: fy, width: contentW, height: rowH,
      title: BANK_INFO, fontSize: fs.footer - 1, textContentVerticalAlign: 'middle'
    })
    fy += rowH + 3
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
    // 页码放在最右栏第三行，与制单人、备注纵向排列
    paperNumberLeft: pageNoLeft,
    paperNumberTop: pageNoTop
  }
}

/**
 * 按纸张 key 生成打印模板
 * @param sizeKey 纸张 key
 * @param options { logo } logo 的 base64，缺省则不打印 logo
 */
export function buildVeiteShipmentPanel(sizeKey, options) {
  return { panels: [buildPanel(getShipmentPaperSize(sizeKey), options)] }
}
