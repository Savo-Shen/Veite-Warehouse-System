<template>
  <div class="p-2">
    <!-- 查询条件 -->
    <div v-show="showSearch">
      <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="搜索" prop="keyword">
              <el-input v-model="queryParams.keyword" placeholder="商品名 / 标题 / 类目 / 品牌"
                        clearable style="width: 240px" @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="电商类目" prop="ecCategoryId">
              <el-select v-model="queryParams.ecCategoryId" placeholder="全部类目" clearable style="width: 220px">
                <el-option v-for="c in categoryList" :key="c.id"
                           :label="`${c.ecL1} › ${c.ecL2} (${c.productCount})`" :value="c.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
                <el-option v-for="s in STATUS_LIST" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="queryParams.onlyReady" @change="handleQuery">只看首发批次</el-checkbox>
              <el-tooltip content="售价齐、品牌齐、SKU 在 10 个以内，称重量最小，适合先跑通全流程" placement="top">
                <el-icon style="margin-left:4px;color:var(--el-color-info)"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
      </el-card>
    </div>

    <el-card shadow="hover" style="margin-top: 8px">
      <template #header>
        <el-row :gutter="10">
          <el-col :span="1.5">
            <el-button type="primary" plain icon="MagicStick" :disabled="!selection.length"
                       v-hasPermi="['wms:ecProduct:ai']" @click="openGenDialog">
              AI 生成标题{{ selection.length ? `（${selection.length}）` : '' }}
            </el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button plain icon="PriceTag" @click="catDialog = true"
                       v-hasPermi="['wms:ecProduct:edit']">平台类目 ID</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-alert type="warning" :closable="false" show-icon style="padding:4px 10px"
                      title="重量与包装尺寸必须实测，系统不做推算——两者直接决定运费" />
          </el-col>
          <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
        </el-row>
      </template>

      <el-table v-loading="loading" :data="list" @selection-change="onSelectionChange" row-key="id">
        <el-table-column type="selection" width="46" align="center" />
        <el-table-column label="商品" min-width="220">
          <template #default="{ row }">
            <div style="font-weight:600">{{ row.ecName }}</div>
            <div style="font-size:12px;color:var(--el-text-color-secondary)">
              {{ row.ecCategoryPath }}
            </div>
            <div v-if="row.brands" style="font-size:12px;color:var(--el-text-color-secondary)">
              {{ row.brands }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="电商标题" min-width="260">
          <template #default="{ row }">
            <span v-if="row.ecTitle">{{ row.ecTitle }}</span>
            <el-tag v-else type="warning" size="small" effect="plain">待生成</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="SKU" width="80" align="right">
          <template #default="{ row }">
            <span>{{ row.skuCount }}</span>
            <el-tag v-if="row.skuCount > 150" type="danger" size="small" effect="plain"
                    style="margin-left:4px">拆</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="就绪度" min-width="260">
          <template #default="{ row }">
            <el-tag v-if="row.noPriceCount" type="warning" size="small" effect="plain">缺售价 {{ row.noPriceCount }}</el-tag>
            <el-tag v-if="row.noCostCount" type="warning" size="small" effect="plain">缺成本价 {{ row.noCostCount }}</el-tag>
            <el-tag :type="row.weighedCount === row.skuCount ? 'success' : 'danger'" size="small" effect="plain">
              称重 {{ row.weighedCount }}/{{ row.skuCount }}
            </el-tag>
            <el-tag :type="row.packedCount === row.skuCount ? 'success' : 'danger'" size="small" effect="plain">
              包装 {{ row.packedCount }}/{{ row.skuCount }}
            </el-tag>
            <el-tag :type="row.mediaCount ? 'success' : 'danger'" size="small" effect="plain">
              图 {{ row.mediaCount }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="STATUS_TYPE[row.status] || 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100" align="right" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">打开</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum"
                  v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <!-- 商品明细抽屉 -->
    <el-drawer v-model="detailOpen" :title="current.ecName" size="72%" destroy-on-close>
      <div v-loading="detailLoading">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="电商类目">{{ current.ecCategoryPath }}</el-descriptions-item>
          <el-descriptions-item label="品牌">{{ current.brands || '—' }}</el-descriptions-item>
          <el-descriptions-item label="规格数">{{ current.skuCount }}</el-descriptions-item>
          <el-descriptions-item label="拼多多类目ID">
            <el-tag v-if="!current.pddCatId" type="warning" size="small" effect="plain">未回填</el-tag>
            <span v-else>{{ current.pddCatId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="淘宝类目ID">
            <el-tag v-if="!current.tbCatId" type="warning" size="small" effect="plain">未回填</el-tag>
            <span v-else>{{ current.tbCatId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="抖音类目ID">
            <el-tag v-if="!current.dyCatId" type="warning" size="small" effect="plain">未回填</el-tag>
            <span v-else>{{ current.dyCatId }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 标题与卖点 -->
        <el-card shadow="never" style="margin-top:12px">
          <template #header>
            <div style="display:flex;align-items:center;gap:8px">
              <span style="font-weight:600">电商标题与卖点</span>
              <el-button size="small" type="primary" plain icon="MagicStick" :loading="genLoading"
                         v-hasPermi="['wms:ecProduct:ai']" @click="genOne">AI 生成</el-button>
              <el-button size="small" icon="DocumentCopy" @click="copyTitle">复制标题</el-button>
              <div style="flex:1"></div>
              <el-button size="small" type="primary" v-hasPermi="['wms:ecProduct:edit']"
                         @click="saveTitle">保存</el-button>
            </div>
          </template>
          <el-form label-width="70px">
            <el-form-item label="标题">
              <el-input v-model="current.ecTitle" type="textarea" :rows="2"
                        maxlength="120" show-word-limit placeholder="30~60 字，可用 AI 生成后手工微调" />
            </el-form-item>
            <el-form-item label="卖点">
              <el-input v-model="current.sellingPoints" type="textarea" :rows="3"
                        maxlength="500" show-word-limit placeholder="一行一条" />
            </el-form-item>
            <el-form-item label="状态">
              <el-radio-group v-model="current.status" @change="saveStatus"
                              v-hasPermi="['wms:ecProduct:edit']">
                <el-radio-button v-for="s in STATUS_LIST" :key="s" :label="s">{{ s }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 商品属性 -->
        <el-card shadow="never" style="margin-top:12px">
          <template #header>
            <div style="display:flex;align-items:center;gap:8px">
              <span style="font-weight:600">商品属性</span>
              <span style="font-size:12px;color:var(--el-text-color-secondary)">型号解析 + 厂商公开样本，非实测</span>
              <div style="flex:1"></div>
              <el-button size="small" icon="DocumentCopy" @click="copyAttrs">复制属性</el-button>
            </div>
          </template>
          <el-descriptions v-if="attrPairs.length" :column="2" border size="small">
            <el-descriptions-item v-for="p in attrPairs" :key="p.k" :label="p.k">{{ p.v }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="暂无属性" :image-size="60" />
        </el-card>

        <!-- SKU 明细：可直接录入重量与包装尺寸 -->
        <el-card shadow="never" style="margin-top:12px">
          <template #header>
            <div style="display:flex;align-items:center;gap:8px">
              <span style="font-weight:600">规格明细（{{ skus.length }}）</span>
              <span style="font-size:12px;color:var(--el-color-warning)">
                净重/毛重/包装尺寸为实测项，填完点保存
              </span>
              <div style="flex:1"></div>
              <el-button size="small" icon="DocumentCopy" @click="copySkuTsv">复制规格表</el-button>
              <el-button size="small" type="primary" :loading="saveMeasureLoading"
                         v-hasPermi="['wms:ecProduct:edit']" @click="saveMeasures">保存实测</el-button>
            </div>
          </template>
          <el-table :data="pagedSkus" size="small" max-height="420" border>
            <el-table-column label="规格名称" prop="skuName" min-width="130" fixed />
            <el-table-column label="解析出的规格" min-width="200">
              <template #default="{ row }">
                <span style="color:var(--el-color-primary)">{{ specText(row.spec) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="品牌" prop="brandName" width="110" />
            <el-table-column label="单位" prop="unit" width="60" align="center" />
            <el-table-column label="成本价" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.costPrice" :controls="false" :precision="2"
                                 size="small" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="销售价" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.sellingPrice" :controls="false" :precision="2"
                                 size="small" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="净重kg" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.netWeight" :controls="false" :precision="3"
                                 size="small" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="毛重kg" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.grossWeight" :controls="false" :precision="3"
                                 size="small" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="包装长cm" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.packLength" :controls="false" :precision="1"
                                 size="small" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="包装宽cm" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.packWidth" :controls="false" :precision="1"
                                 size="small" style="width:100%" />
              </template>
            </el-table-column>
            <el-table-column label="包装高cm" width="100">
              <template #default="{ row }">
                <el-input-number v-model="row.packHeight" :controls="false" :precision="1"
                                 size="small" style="width:100%" />
              </template>
            </el-table-column>
          </el-table>
          <!-- SC气缸/SDA薄型气缸 各 292 个 SKU，每行 7 个输入框；不分页会一次渲染两千多个组件 -->
          <el-pagination v-if="skus.length > skuPageSize" small background layout="total, sizes, prev, pager, next"
                         :total="skus.length" v-model:current-page="skuPage"
                         v-model:page-size="skuPageSize" :page-sizes="[20, 50, 100]"
                         style="margin-top:8px;justify-content:flex-end" />
        </el-card>
      </div>
    </el-drawer>

    <!-- 批量生成标题 -->
    <el-dialog v-model="genDialog" title="AI 生成电商标题" width="520px">
      <el-form label-width="100px">
        <el-form-item label="选中商品">{{ selection.length }} 个</el-form-item>
        <el-form-item label="已有标题">
          <el-radio-group v-model="genForm.overwrite">
            <el-radio :label="false">跳过</el-radio>
            <el-radio :label="true">覆盖重写</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="额外要求">
          <el-input v-model="genForm.extraHint" type="textarea" :rows="2"
                    placeholder="选填，例如：突出耐高温、面向机床维修客户" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon
                  title="实测约 2 秒/个，任务在后台跑，关掉弹窗也会继续；每生成一个即刻入库" />
        <el-form-item v-if="genTask.taskId" label="进度">
          <div style="width:100%">
            <el-progress :percentage="genTask.percent" :status="genTask.state === 'failed' ? 'exception' : (genTask.state === 'done' ? 'success' : undefined)" />
            <div style="font-size:12px;color:var(--el-text-color-secondary);margin-top:4px">
              {{ genTask.finished }}/{{ genTask.total }} ·
              成功 {{ genTask.ok }} · 跳过 {{ genTask.skipped }} · 失败 {{ genTask.failed }}
              <span v-if="genTask.current"> · 正在处理 {{ genTask.current }}</span>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="genDialog = false">{{ genRunning ? '关闭（后台继续）' : '取消' }}</el-button>
        <el-button type="primary" :loading="genRunning" :disabled="genRunning" @click="doGenBatch">开始生成</el-button>
      </template>
    </el-dialog>

    <!-- 平台类目 ID 回填 -->
    <el-dialog v-model="catDialog" title="平台类目 ID" width="900px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:8px"
                title="到各平台商家后台搜到叶子类目后回填。20 条填完终身复用。拼多多：商品管理→发布商品→类目搜索；淘宝：卖家中心→发布宝贝；抖音：商家后台→商品→发布商品" />
      <el-table :data="categoryList" size="small" max-height="460" border>
        <el-table-column label="电商类目" min-width="180">
          <template #default="{ row }">{{ row.ecL1 }} › {{ row.ecL2 }}</template>
        </el-table-column>
        <el-table-column label="商品数" prop="productCount" width="80" align="right" />
        <el-table-column label="拼多多类目ID" width="150">
          <template #default="{ row }">
            <el-input v-model="row.pddCatId" size="small" placeholder="待填" />
          </template>
        </el-table-column>
        <el-table-column label="淘宝类目ID" width="150">
          <template #default="{ row }">
            <el-input v-model="row.tbCatId" size="small" placeholder="待填" />
          </template>
        </el-table-column>
        <el-table-column label="抖音类目ID" width="150">
          <template #default="{ row }">
            <el-input v-model="row.dyCatId" size="small" placeholder="待填" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="saveCategory(row)">保存</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup name="EcListing">
import {
  listEcProduct, getEcProductSkus, listEcCategory, updateEcProduct,
  updateEcProductStatus, updateEcCategory, saveEcMeasures, genEcTitle, getEcGenProgress
} from '@/api/wms/ecProduct'

const { proxy } = getCurrentInstance()

const STATUS_LIST = ['待整理', '待拍图', '可上架', '已上架']
const STATUS_TYPE = { 待整理: 'info', 待拍图: 'warning', 可上架: 'primary', 已上架: 'success' }

const loading = ref(false)
const showSearch = ref(true)
const list = ref([])
const total = ref(0)
const categoryList = ref([])
const selection = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: undefined,
  ecCategoryId: undefined,
  status: undefined,
  onlyReady: false
})

/** 上新列表 */
function getList() {
  loading.value = true
  listEcProduct(queryParams).then(res => {
    list.value = res.rows
    total.value = res.total
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.keyword = undefined
  queryParams.ecCategoryId = undefined
  queryParams.status = undefined
  queryParams.onlyReady = false
  handleQuery()
}

function onSelectionChange(rows) {
  selection.value = rows
}

/* ---------------- 明细抽屉 ---------------- */

const detailOpen = ref(false)
const detailLoading = ref(false)
const current = ref({})
const skus = ref([])
const skuSnapshot = ref(new Map())
const skuPage = ref(1)
const skuPageSize = ref(50)

const pagedSkus = computed(() => {
  const start = (skuPage.value - 1) * skuPageSize.value
  return skus.value.slice(start, start + skuPageSize.value)
})

/** 实测字段的指纹，用于判断某行是否被改过 */
function measureKey(s) {
  return [s.netWeight, s.grossWeight, s.packLength, s.packWidth, s.packHeight,
          s.costPrice, s.sellingPrice].map(v => v ?? '').join('|')
}

function openDetail(row) {
  current.value = { ...row }
  detailOpen.value = true
  detailLoading.value = true
  skuPage.value = 1
  getEcProductSkus(row.id).then(res => {
    skus.value = res.data || []
    // 存一份原始快照，保存时只提交改动过的行
    skuSnapshot.value = new Map(skus.value.map(s => [s.skuId, measureKey(s)]))
  }).finally(() => {
    detailLoading.value = false
  })
}

/** 商品属性平铺成键值对，跳过下划线开头的元信息 */
const attrPairs = computed(() => {
  const raw = current.value.attrs
  if (!raw) return []
  let obj
  try {
    obj = typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch (e) {
    return []
  }
  const out = []
  Object.keys(obj).forEach(k => {
    if (k.startsWith('_') || k === '可选规格数') return
    const v = obj[k]
    if (v && typeof v === 'object') {
      Object.keys(v).forEach(nk => out.push({ k: nk, v: v[nk] }))
    } else {
      out.push({ k, v })
    }
  })
  return out
})

/** SKU 的规格 JSON 转成一行可读文本，只展示规格性字段 */
const SPEC_KEYS = ['缸径', '行程', '插管外径', '螺纹规格', '外径', '内径', '接管口径',
  '公称通径', '电压', '夹持范围', '孔径', '颜色', '盘长', '量程', '接口', '接口规格']

function specText(raw) {
  if (!raw) return '—'
  let obj
  try {
    obj = typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch (e) {
    return '—'
  }
  const parts = SPEC_KEYS.filter(k => obj[k]).map(k => `${k} ${obj[k]}`)
  return parts.length ? parts.join(' · ') : '—'
}

function saveTitle() {
  updateEcProduct({
    id: current.value.id,
    ecTitle: current.value.ecTitle,
    sellingPoints: current.value.sellingPoints,
    remark: current.value.remark
  }).then(() => {
    proxy.$modal.msgSuccess('已保存')
    getList()
  })
}

function saveStatus(val) {
  updateEcProductStatus(current.value.id, val).then(() => {
    proxy.$modal.msgSuccess('状态已更新')
    getList()
  })
}

const saveMeasureLoading = ref(false)

function saveMeasures() {
  // 只提交改动过的行：一个商品最多 292 个 SKU，全量提交会白白产生几百条 UPDATE
  const dirty = skus.value.filter(s => skuSnapshot.value.get(s.skuId) !== measureKey(s))
  if (!dirty.length) {
    proxy.$modal.msgWarning('没有改动')
    return
  }
  const payload = dirty.map(s => ({
    skuId: s.skuId,
    netWeight: s.netWeight,
    grossWeight: s.grossWeight,
    packLength: s.packLength,
    packWidth: s.packWidth,
    packHeight: s.packHeight,
    costPrice: s.costPrice,
    sellingPrice: s.sellingPrice
  }))
  saveMeasureLoading.value = true
  saveEcMeasures(payload).then(res => {
    proxy.$modal.msgSuccess(`已保存 ${res.data} 条`)
    // 保存成功后刷新快照，避免重复提交
    dirty.forEach(s => skuSnapshot.value.set(s.skuId, measureKey(s)))
    getList()
  }).finally(() => {
    saveMeasureLoading.value = false
  })
}

/* ---------------- 复制 ---------------- */

function copy(text, label) {
  navigator.clipboard.writeText(text).then(() => proxy.$modal.msgSuccess(label + '已复制'))
}

function copyTitle() {
  copy(current.value.ecTitle || '', '标题')
}

function copyAttrs() {
  copy(attrPairs.value.map(p => `${p.k}：${p.v}`).join('\n'), '属性')
}

function copySkuTsv() {
  const head = '规格名称\t解析规格\t品牌\t单位\t成本价\t销售价\t净重kg\t毛重kg\t包装长cm\t包装宽cm\t包装高cm'
  const body = skus.value.map(s => [
    s.skuName, specText(s.spec), s.brandName || '', s.unit || '',
    s.costPrice ?? '', s.sellingPrice ?? '', s.netWeight ?? '', s.grossWeight ?? '',
    s.packLength ?? '', s.packWidth ?? '', s.packHeight ?? ''
  ].join('\t')).join('\n')
  copy(head + '\n' + body, '规格表')
}

/* ---------------- AI 生成标题 ---------------- */

const genDialog = ref(false)
const genLoading = ref(false)
const genRunning = ref(false)
const genForm = reactive({ overwrite: false, extraHint: '' })
const genTask = ref({ taskId: '', percent: 0, total: 0, finished: 0, ok: 0, skipped: 0, failed: 0, state: '', current: '' })
let genTimer = null

function openGenDialog() {
  genTask.value = { taskId: '', percent: 0, total: 0, finished: 0, ok: 0, skipped: 0, failed: 0, state: '', current: '' }
  genDialog.value = true
}

/** 提交任务后每 2 秒轮询一次进度，直到 done/failed */
function pollProgress(taskId) {
  clearInterval(genTimer)
  genTimer = setInterval(() => {
    getEcGenProgress(taskId).then(res => {
      genTask.value = res.data
      if (res.data.state !== 'running') {
        clearInterval(genTimer)
        genTimer = null
        genRunning.value = false
        const d = res.data
        proxy.$modal.msgSuccess(`完成：成功 ${d.ok}，跳过 ${d.skipped}，失败 ${d.failed}`)
        if (d.failed) {
          const msg = (d.results || []).filter(r => r.state === 'failed')
            .map(r => `${r.ecName}：${r.message}`).join('<br/>')
          proxy.$modal.alertError(msg)
        }
        getList()
      }
    }).catch(() => {
      clearInterval(genTimer)
      genTimer = null
      genRunning.value = false
    })
  }, 2000)
}

function doGenBatch() {
  genRunning.value = true
  genEcTitle({
    productIds: selection.value.map(r => r.id),
    overwrite: genForm.overwrite,
    extraHint: genForm.extraHint
  }).then(res => {
    genTask.value = res.data
    pollProgress(res.data.taskId)
  }).catch(() => {
    genRunning.value = false
  })
}

/** 抽屉里生成单个：量小，直接轮询到结束再回填 */
function genOne() {
  genLoading.value = true
  genEcTitle({ productIds: [current.value.id], overwrite: true }).then(res => {
    const taskId = res.data.taskId
    const timer = setInterval(() => {
      getEcGenProgress(taskId).then(p => {
        if (p.data.state === 'running') return
        clearInterval(timer)
        genLoading.value = false
        const r = (p.data.results || [])[0]
        if (r && r.state === 'ok') {
          current.value.ecTitle = r.title
          current.value.sellingPoints = r.sellingPoints
          proxy.$modal.msgSuccess('已生成，确认无误后点保存')
        } else {
          proxy.$modal.msgError(r ? r.message : '生成失败')
        }
        getList()
      }).catch(() => {
        clearInterval(timer)
        genLoading.value = false
      })
    }, 1500)
  }).catch(() => {
    genLoading.value = false
  })
}

onBeforeUnmount(() => clearInterval(genTimer))

/* ---------------- 平台类目 ID ---------------- */

const catDialog = ref(false)

function getCategories() {
  listEcCategory().then(res => {
    categoryList.value = res.data || []
  })
}

function saveCategory(row) {
  updateEcCategory({
    id: row.id,
    pddCatId: row.pddCatId,
    tbCatId: row.tbCatId,
    dyCatId: row.dyCatId
  }).then(() => proxy.$modal.msgSuccess('已保存'))
}

onMounted(() => {
  getCategories()
  getList()
})
</script>
