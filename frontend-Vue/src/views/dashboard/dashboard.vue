<template>
  <div class="board-container" :class="{ embedded: !showWhich }" v-loading="loading">
    <div v-if="showWhich" class="back-btn" @click="backHome"><img src="@/assets/images/home.png">返回</div>
    <img v-else src="@/assets/images/fullscreen.png" alt="" class="fullscreen-img" @click="toDataBoard" >
    <div class="time-stamp">{{ nowTime }}</div>
    <div class="board-title"><span>仓库经营数据看板</span></div>

    <div class="board-content flex-between">
      <div class="content-left flex-column-between">
        <section class="panel inventory-overview">
          <div class="box-title">库存总览</div>
          <div class="overview-grid">
            <div v-for="item in overviewCards" :key="item.label" class="overview-card">
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="box-title">库存价值</div>
          <div class="value-list">
            <div>
              <span>按售价估值</span>
              <strong>{{ money(summary.stockSellingValue) }}</strong>
            </div>
            <div>
              <span>按成本估值</span>
              <strong>{{ money(summary.stockCostValue) }}</strong>
            </div>
          </div>
        </section>

        <section class="panel risk-panel">
          <div class="box-title">需要关注的库存</div>
          <div class="risk-list">
            <div v-for="row in lowStockSku" :key="row.skuId" class="risk-row">
              <div>
                <strong>{{ row.itemName || '-' }}</strong>
                <span>{{ row.skuName || '-' }} · {{ row.locationCode || '-' }}</span>
              </div>
              <b>{{ integer(row.quantity) }}</b>
            </div>
            <el-empty v-if="!lowStockSku.length" description="暂无低库存" :image-size="54" />
          </div>
        </section>
      </div>

      <div class="content-middle flex-column-between">
        <section class="hero-panel">
          <div class="hero-main">
            <small>今日营业额</small>
            <strong>{{ money(summary.todayTurnover) }}</strong>
            <span>按今日已完成出库单统计</span>
          </div>
          <div class="hero-side">
            <div>
              <span>今日出库</span>
              <strong>{{ integer(summary.todayShipmentOrders) }} 单 / {{ integer(summary.todayShipmentQuantity) }} 件</strong>
            </div>
            <div>
              <span>今日入库</span>
              <strong>{{ integer(summary.todayReceiptOrders) }} 单 / {{ integer(summary.todayReceiptQuantity) }} 件</strong>
            </div>
            <div>
              <span>今日入库金额</span>
              <strong>{{ money(summary.todayReceiptAmount) }}</strong>
            </div>
          </div>
        </section>

        <section class="panel trend-panel">
          <div class="box-title">近 14 天营业额趋势</div>
          <div class="box-content">
            <TrendLineChart
              :height="'100%'"
              yName="元"
              :xData="trendX"
              :yData="turnoverTrend"
            />
          </div>
        </section>
      </div>

      <div class="content-right flex-column-between">
        <section class="panel stat-panel">
          <div class="box-title">待处理单据</div>
          <div class="stat-grid">
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
        </section>

        <section class="panel">
          <div class="box-title">近 14 天出入库件数</div>
          <div class="mini-chart">
            <barChart
              :height="'100%'"
              :barColor="['#1c508e', '#1be5e7']"
              yName="件"
              :xData="trendX"
              :yData="shipmentQuantityTrend"
            />
          </div>
        </section>

        <section class="panel top-panel">
          <div class="box-title">近 30 天出库 TOP</div>
          <div class="top-list">
            <div v-for="(row, index) in topShipmentSku" :key="row.skuId" class="top-row">
              <i>{{ index + 1 }}</i>
              <div>
                <strong>{{ row.itemName || '-' }}</strong>
                <span>{{ row.skuName || '-' }}</span>
              </div>
              <b>{{ integer(row.quantity) }}</b>
            </div>
            <el-empty v-if="!topShipmentSku.length" description="暂无出库数据" :image-size="54" />
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import TrendLineChart from './components/dashboard/TrendLineChart.vue'
import barChart from './components/dashboard/BarChart.vue'
import moment from 'moment'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardOverview } from '@/api/wms/dashboard'

const router = useRouter()
const loading = ref(false)
const nowTime = ref()
const timer = ref()
const summary = ref({})
const dailyTrend = ref([])
const warehouseStock = ref([])
const topShipmentSku = ref([])
const lowStockSku = ref([])

const showWhich = computed(() => router.currentRoute.value.path === '/system/dashboard')
const trendX = computed(() => dailyTrend.value.map(item => moment(item.day).format('MM-DD')))
const turnoverTrend = computed(() => dailyTrend.value.map(item => Number(item.turnover || 0).toFixed(2)))
const shipmentQuantityTrend = computed(() => dailyTrend.value.map(item => Number(item.shipmentQuantity || 0).toFixed(0)))
const overviewCards = computed(() => [
  { label: '仓库数', value: integer(summary.value.warehouseCount) },
  { label: '库位数', value: integer(summary.value.locationCount) },
  { label: '商品数', value: integer(summary.value.itemCount) },
  { label: 'SKU 数', value: integer(summary.value.skuCount) },
  { label: '库存件数', value: integer(summary.value.totalStockQuantity) },
  { label: '低库存', value: integer(summary.value.lowStockSkuCount) }
])

function toDataBoard() {
  router.push('/system/dashboard')
}

function backHome() {
  router.push('/dashboard')
}

function getNowTime() {
  nowTime.value = moment().format('YYYY-MM-DD HH:mm:ss')
}

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
  for (let i = 13; i >= 0; i--) {
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
    warehouseStock.value = data.warehouseStock || []
    topShipmentSku.value = data.topShipmentSku || []
    lowStockSku.value = data.lowStockSku || []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getNowTime()
  timer.value = setInterval(getNowTime, 1000)
  loadDashboard()
})

onBeforeUnmount(() => {
  clearInterval(timer.value)
})
</script>

<style scoped>
.board-container {
  position: relative;
  width: 100%;
  height: 100vh;
  min-height: 100vh;
  color: #fff;
  background-image: url("../../assets/images/board-bg.png");
  background-size: 100% 100%;
  overflow: hidden;
}

.board-container.embedded {
  height: calc(100vh - 84px);
  min-height: calc(100vh - 84px);
}

.back-btn,
.fullscreen-img {
  position: absolute;
  left: 2.5%;
  top: 5%;
  height: 3%;
  cursor: pointer;
}

.back-btn {
  display: flex;
  align-items: center;
  color: #00d0ff;
  font-size: 17px;
}

.back-btn img {
  height: 100%;
  margin-right: 6px;
}

.time-stamp {
  position: absolute;
  right: 2.5%;
  top: 5%;
  color: #00d1ff;
  font-size: 20px;
}

.board-title {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 9%;
  color: transparent;
  font-size: 40px;
  font-weight: bold;
  letter-spacing: 8px;
  background-image: linear-gradient(to top, #2571e9, #00e7ff);
  background-clip: text;
  -webkit-background-clip: text;
}

.board-content {
  width: 95%;
  height: calc(100% - 10%);
  margin: 6px auto 0;
  gap: 12px;
}

.flex-column-between {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.content-left,
.content-right {
  width: 25%;
}

.content-middle {
  width: 50%;
}

.panel,
.hero-panel {
  background-image: url("../../assets/images/box-bg1.png");
  background-size: 100% 100%;
}

.panel {
  padding: 16px;
}

.content-left .panel:nth-child(1) { height: 28%; }
.content-left .panel:nth-child(2) { height: calc(22% - 12px); }
.content-left .panel:nth-child(3) { height: calc(50% - 12px); }
.content-right .panel:nth-child(1) { height: 25%; }
.content-right .panel:nth-child(2) { height: calc(32% - 12px); }
.content-right .panel:nth-child(3) { height: calc(43% - 12px); }

.hero-panel {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 18px;
  height: 34%;
  padding: 28px 34px;
}

.trend-panel {
  height: calc(66% - 12px);
  padding: 18px 30px;
}

.box-title {
  display: flex;
  align-items: center;
  height: 20px;
  margin-left: 4px;
  color: #01d1ff;
}

.box-title::before {
  content: " ";
  display: inline-block;
  width: 6px;
  height: 100%;
  margin-right: 6px;
  border-radius: 10px;
  background: linear-gradient(to bottom, #00d1ff, #2869e8);
}

.box-content {
  height: calc(100% - 20px);
  overflow: hidden;
  padding-top: 12px;
}

.overview-grid,
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  height: calc(100% - 28px);
  padding-top: 10px;
}

.overview-card,
.stat-grid div,
.value-list div {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
  padding: 8px;
  background: rgba(0, 209, 255, .08);
  border: 1px solid rgba(0, 209, 255, .16);
  border-radius: 12px;
}

.overview-card strong,
.stat-grid strong {
  color: #1be5e7;
  font-size: 23px;
  line-height: 1.1;
}

.overview-card span,
.stat-grid span,
.value-list span,
.hero-main span,
.hero-side span,
.risk-row span,
.top-row span {
  color: rgba(255, 255, 255, .72);
  font-size: 12px;
}

.value-list {
  display: grid;
  gap: 8px;
  height: calc(100% - 28px);
  padding-top: 10px;
}

.value-list strong {
  margin-top: 3px;
  color: #ffd166;
  font-size: 21px;
}

.hero-main {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.hero-main small {
  color: #00d1ff;
  font-size: 18px;
  font-weight: 700;
}

.hero-main strong {
  margin: 12px 0;
  color: #1be5e7;
  font-size: 58px;
  line-height: 1;
}

.hero-side {
  display: grid;
  gap: 12px;
}

.hero-side div {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 14px 18px;
  background: rgba(41, 126, 248, .12);
  border: 1px solid rgba(41, 126, 248, .22);
  border-radius: 14px;
}

.hero-side strong {
  margin-top: 6px;
  color: #fff;
  font-size: 20px;
}

.risk-list,
.top-list {
  height: calc(100% - 28px);
  padding-top: 12px;
  overflow: auto;
}

.risk-row,
.top-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid rgba(255, 255, 255, .08);
}

.risk-row div,
.top-row div {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 2px;
}

.risk-row strong,
.top-row strong {
  overflow: hidden;
  color: #fff;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.risk-row b {
  min-width: 42px;
  color: #ff9f43;
  font-size: 22px;
  text-align: right;
}

.top-row i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #041b3c;
  font-style: normal;
  font-weight: 800;
  background: #1be5e7;
  border-radius: 50%;
}

.top-row b {
  color: #ffd166;
  font-size: 20px;
}

.mini-chart {
  height: calc(100% - 24px);
  padding-top: 10px;
}

@media (max-width: 1200px) {
  .board-container {
    overflow: auto;
  }

  .board-content {
    display: block;
    height: auto;
  }

  .content-left,
  .content-middle,
  .content-right {
    width: 100%;
  }

  .panel,
  .hero-panel,
  .trend-panel {
    min-height: 280px;
    margin-bottom: 12px;
  }
}
</style>
