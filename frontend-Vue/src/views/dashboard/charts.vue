<template>
  <div class="app-container home-dashboard" v-loading="loading">
    <section class="home-hero">
      <div>
        <small>今日经营</small>
        <h2>{{ money(summary.todayTurnover) }}</h2>
        <p>按今日已完成出库单统计营业额</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="router.push('/dashboard')">打开数据大屏</el-button>
      </div>
    </section>

    <el-row :gutter="14" class="metric-row">
      <el-col v-for="item in metricCards" :key="item.label" :xs="24" :sm="12" :md="6">
        <el-card shadow="never" class="metric-card">
          <div class="metric-icon" :class="item.type">{{ item.short }}</div>
          <div>
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="panel-card">
          <div class="card-title">近 {{ TREND_DAYS }} 天营业额</div>
          <StationBar
            height="310px"
            :chartData="turnoverChart"
            :setting="{ seriesName: '营业额', yName: '元' }"
            tooltip-unit="元"
            xName="日"
          />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card">
          <div class="card-title">待处理与风险</div>
          <div class="todo-grid">
            <div>
              <strong>{{ integer(summary.pendingReceiptOrders) }}</strong>
              <span>待入库单</span>
            </div>
            <div>
              <strong>{{ integer(summary.pendingShipmentOrders) }}</strong>
              <span>待出库单</span>
            </div>
            <div>
              <strong>{{ integer(summary.lowStockSkuCount) }}</strong>
              <span>低库存 SKU</span>
            </div>
            <div>
              <strong>{{ integer(summary.emptyStockSkuCount) }}</strong>
              <span>零库存 SKU</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="14">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card">
          <div class="card-title">库存快照</div>
          <div class="value-stack">
            <div>
              <span>当前库存</span>
              <strong>{{ integer(summary.totalStockQuantity) }} 件</strong>
            </div>
            <div>
              <span>按售价估值</span>
              <strong>{{ money(summary.stockSellingValue) }}</strong>
            </div>
            <div>
              <span>按成本估值</span>
              <strong>{{ money(summary.stockCostValue) }}</strong>
            </div>
            <div>
              <span>商品 / SKU</span>
              <strong>{{ integer(summary.itemCount) }} / {{ integer(summary.skuCount) }}</strong>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card">
          <div class="card-title">近 30 天出库 TOP</div>
          <div class="rank-list">
            <div v-for="(row, index) in topShipmentSku" :key="row.skuId" class="rank-row">
              <i>{{ index + 1 }}</i>
              <div>
                <strong>{{ row.itemName || '-' }}</strong>
                <span>{{ row.skuName || '-' }}</span>
              </div>
              <b>{{ integer(row.quantity) }}</b>
            </div>
            <el-empty v-if="!topShipmentSku.length" description="暂无出库数据" :image-size="56" />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card">
          <div class="card-title">低库存提醒</div>
          <div class="rank-list">
            <div v-for="row in lowStockSku" :key="row.skuId" class="rank-row warning">
              <i>!</i>
              <div>
                <strong>{{ row.itemName || '-' }}</strong>
                <span>{{ row.skuName || '-' }} · {{ row.locationCode || '-' }}</span>
              </div>
              <b>{{ integer(row.quantity) }}</b>
            </div>
            <el-empty v-if="!lowStockSku.length" description="暂无低库存" :image-size="56" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import StationBar from './components/StationBar.vue'
import moment from 'moment'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardOverview } from '@/api/wms/dashboard'

const TREND_DAYS = 14
const router = useRouter()
const loading = ref(false)
const summary = ref({})
const dailyTrend = ref([])
const topShipmentSku = ref([])
const lowStockSku = ref([])

const trendTotals = computed(() => dailyTrend.value.reduce((totals, row) => ({
  turnover: totals.turnover + Number(row.turnover || 0),
  receiptAmount: totals.receiptAmount + Number(row.receiptAmount || 0),
  shipmentOrders: totals.shipmentOrders + Number(row.shipmentOrders || 0),
  receiptOrders: totals.receiptOrders + Number(row.receiptOrders || 0),
  shipmentQuantity: totals.shipmentQuantity + Number(row.shipmentQuantity || 0),
  receiptQuantity: totals.receiptQuantity + Number(row.receiptQuantity || 0)
}), {
  turnover: 0,
  receiptAmount: 0,
  shipmentOrders: 0,
  receiptOrders: 0,
  shipmentQuantity: 0,
  receiptQuantity: 0
}))

const metricCards = computed(() => [
  { label: `近 ${TREND_DAYS} 天营业额`, value: money(trendTotals.value.turnover), short: '额', type: 'blue' },
  { label: `近 ${TREND_DAYS} 天出库`, value: `${integer(trendTotals.value.shipmentOrders)} 单 / ${integer(trendTotals.value.shipmentQuantity)} 件`, short: '出', type: 'green' },
  { label: `近 ${TREND_DAYS} 天入库`, value: `${integer(trendTotals.value.receiptOrders)} 单 / ${integer(trendTotals.value.receiptQuantity)} 件`, short: '入', type: 'purple' },
  { label: '待处理单据', value: `${integer(summary.value.pendingReceiptOrders)} 入 / ${integer(summary.value.pendingShipmentOrders)} 出`, short: '待', type: 'orange' }
])

const turnoverChart = computed(() => ({
  xData: dailyTrend.value.map(item => moment(item.day).format('MM-DD')),
  yData: dailyTrend.value.map(item => Number(item.turnover || 0).toFixed(2))
}))

function money(value) {
  const amount = Number(value || 0)
  return `￥${amount.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function integer(value) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function fillTrend(rows) {
  const map = new Map((rows || []).map(item => [moment(item.day).format('YYYY-MM-DD'), item]))
  const days = []
  for (let i = TREND_DAYS - 1; i >= 0; i--) {
    const day = moment().subtract(i, 'days').format('YYYY-MM-DD')
    days.push({
      day,
      receiptOrders: 0,
      shipmentOrders: 0,
      receiptQuantity: 0,
      shipmentQuantity: 0,
      receiptAmount: 0,
      turnover: 0,
      ...(map.get(day) || {})
    })
  }
  return days
}

async function loadDashboard() {
  loading.value = true
  try {
    const res = await getDashboardOverview()
    const data = res.data || {}
    summary.value = data.summary || {}
    dailyTrend.value = fillTrend(data.dailyTrend || [])
    topShipmentSku.value = data.topShipmentSku || []
    lowStockSku.value = data.lowStockSku || []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.home-dashboard {
  min-height: calc(100vh - 84px);
  padding: 14px;
  background: #f3f6fb;
}

.home-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  padding: 24px 28px;
  color: #fff;
  background: linear-gradient(135deg, #1677ff 0%, #16c8c8 100%);
  border-radius: 18px;
  box-shadow: 0 12px 28px rgba(22, 119, 255, .18);
}

.home-hero small {
  font-size: 15px;
  font-weight: 700;
  opacity: .86;
}

.home-hero h2 {
  margin: 8px 0;
  font-size: 42px;
  line-height: 1;
}

.home-hero p {
  margin: 0;
  opacity: .8;
}

.metric-row {
  margin-bottom: 14px;
}

.metric-card {
  margin-bottom: 14px;
  border: 0;
  border-radius: 16px;
}

.metric-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 104px;
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  flex: 0 0 auto;
  color: #fff;
  font-size: 22px;
  font-weight: 800;
  border-radius: 16px;
}

.metric-icon.blue { background: linear-gradient(135deg, #409eff, #1677ff); }
.metric-icon.green { background: linear-gradient(135deg, #13c2c2, #08a88a); }
.metric-icon.purple { background: linear-gradient(135deg, #7c5cff, #a66cff); }
.metric-icon.orange { background: linear-gradient(135deg, #ff9f43, #ffbf5f); }

.metric-card strong {
  display: block;
  color: #17233d;
  font-size: 24px;
  line-height: 1.25;
}

.metric-card span {
  color: #8a96a8;
}

.panel-card {
  margin-bottom: 14px;
  border: 0;
  border-radius: 16px;
}

.card-title {
  display: flex;
  align-items: center;
  height: 30px;
  color: #17233d;
  font-weight: 800;
}

.card-title::before {
  content: '';
  width: 5px;
  height: 70%;
  margin-right: 8px;
  background: #3671e8;
  border-radius: 99px;
}

.todo-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  min-height: 310px;
}

.todo-grid div,
.value-stack div {
  display: grid;
  align-content: center;
  gap: 6px;
  padding: 16px;
  background: #f7f9fd;
  border: 1px solid #edf1f7;
  border-radius: 14px;
}

.todo-grid strong,
.value-stack strong {
  color: #1677ff;
  font-size: 26px;
}

.todo-grid span,
.value-stack span,
.rank-row span {
  color: #8a96a8;
}

.value-stack {
  display: grid;
  gap: 12px;
  min-height: 310px;
}

.rank-list {
  height: 310px;
  overflow: auto;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px solid #edf1f7;
}

.rank-row i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  color: #1677ff;
  font-style: normal;
  font-weight: 800;
  background: #eaf3ff;
  border-radius: 50%;
}

.rank-row.warning i {
  color: #d46b08;
  background: #fff7e6;
}

.rank-row div {
  display: grid;
  flex: 1;
  min-width: 0;
}

.rank-row strong {
  overflow: hidden;
  color: #17233d;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-row b {
  color: #1677ff;
  font-size: 18px;
}

@media (max-width: 768px) {
  .home-hero {
    display: block;
    padding: 20px;
  }

  .home-hero h2 {
    font-size: 32px;
  }

  .hero-actions {
    margin-top: 16px;
  }
}
</style>
