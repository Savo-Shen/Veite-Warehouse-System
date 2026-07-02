<template>
  <div class="app-container">
    <el-card v-if="!amapConfigured" class="mt0">
      <el-empty description="未配置高德地图 Key">
        <div style="color: #909399; font-size: 13px; line-height: 1.8">
          请管理员前往「基础资料 → <router-link :to="ENV_CONFIG_ROUTE" style="color: #409EFF">环境配置</router-link>」
          填写高德地图 Key（页面内有详细申请步骤），保存后回到本页即可使用。
        </div>
      </el-empty>
    </el-card>

    <el-card v-else :body-style="{ padding: 0, position: 'relative' }">
      <div ref="mapRef" class="overview-map" v-loading="loading"></div>

      <!-- 图层筛选面板 -->
      <div class="map-panel">
        <div class="panel-title">地图总览</div>
        <el-checkbox-group v-model="visibleTypes" class="panel-checks" @change="applyFilter">
          <el-checkbox label="warehouse">
            <span class="legend-item"><i class="mark warehouse-mark">仓</i>仓库（{{ counts.warehouse }}）</span>
          </el-checkbox>
          <el-checkbox label="1">
            <span class="legend-item"><i class="dot" style="background: #409EFF"></i>客户（{{ counts['1'] }}）</span>
          </el-checkbox>
          <el-checkbox label="2">
            <span class="legend-item"><i class="dot" style="background: #67C23A"></i>供应商（{{ counts['2'] }}）</span>
          </el-checkbox>
          <el-checkbox label="3">
            <span class="legend-item"><i class="dot" style="background: #E6A23C"></i>物流单位（{{ counts['3'] }}）</span>
          </el-checkbox>
        </el-checkbox-group>
        <div class="panel-actions">
          <el-button size="small" icon="Refresh" :loading="loading" @click="refreshData">刷新</el-button>
          <el-button size="small" icon="Aim" @click="fitView">全部可见</el-button>
        </div>
        <div class="panel-tip" v-if="unlocatedTip">{{ unlocatedTip }}</div>
      </div>
    </el-card>
  </div>
</template>

<script setup name="WmsMapOverview">
import { ref, reactive, computed, onMounted, onBeforeUnmount, getCurrentInstance } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listMerchantNoPage } from '@/api/wms/merchant'
import { listWarehouseNoPage } from '@/api/wms/warehouse'
import {
  loadAMap,
  checkAMapConfigured,
  ENV_CONFIG_ROUTE,
  MERCHANT_TYPE_COLORS,
  MERCHANT_TYPE_DEFAULT_COLOR
} from '@/utils/amap'
import {
  WAREHOUSE_COLOR,
  openMerchantInfoWindow,
  openWarehouseInfoWindow
} from '@/utils/mapMarkerInfo'

const { proxy } = getCurrentInstance()
const { merchant_type } = proxy.useDict('merchant_type')
const route = useRoute()

const amapConfigured = ref(true)
const loading = ref(false)
const mapRef = ref(null)
const visibleTypes = ref(['warehouse', '1', '2', '3'])
const counts = reactive({ warehouse: 0, 1: 0, 2: 0, 3: 0 })
const unlocated = reactive({ warehouse: 0, merchant: 0 })

let map = null
let infoWindow = null
// [{ type: 'warehouse' | '1' | '2' | '3', kind: 'warehouse' | 'merchant', id, marker, openInfo }]
let markerEntries = []

const unlocatedTip = computed(() => {
  const parts = []
  if (unlocated.warehouse > 0) parts.push(`${unlocated.warehouse} 个仓库`)
  if (unlocated.merchant > 0) parts.push(`${unlocated.merchant} 家来往单位`)
  if (!parts.length) return ''
  return `另有 ${parts.join('、')} 未标记位置，可在对应管理页编辑并「地图选点」`
})

onMounted(async () => {
  amapConfigured.value = await checkAMapConfigured()
  if (!amapConfigured.value) return
  loading.value = true
  try {
    const AMap = await loadAMap()
    map = new AMap.Map(mapRef.value, { zoom: 5, viewMode: '2D' })
    map.addControl(new AMap.ToolBar())
    map.addControl(new AMap.Scale())
    infoWindow = new AMap.InfoWindow({ offset: new AMap.Pixel(0, -16) })
    await refreshData()
    focusFromRoute()
  } catch (e) {
    ElMessage.error(e.message || '地图加载失败')
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (map) {
    map.destroy()
    map = null
    markerEntries = []
    infoWindow = null
  }
})

async function refreshData() {
  if (!map) return
  loading.value = true
  try {
    const [warehouseRes, merchantRes] = await Promise.all([
      listWarehouseNoPage({}),
      listMerchantNoPage({})
    ])
    const warehouses = warehouseRes.data || []
    const merchants = merchantRes.data || []

    map.remove(markerEntries.map(en => en.marker))
    markerEntries = []
    counts.warehouse = 0
    counts['1'] = 0
    counts['2'] = 0
    counts['3'] = 0

    const AMap = window.AMap

    unlocated.warehouse = warehouses.filter(w => w.longitude == null || w.latitude == null).length
    warehouses
      .filter(w => w.longitude != null && w.latitude != null)
      .forEach(w => {
        counts.warehouse++
        const marker = new AMap.Marker({
          position: [Number(w.longitude), Number(w.latitude)],
          title: w.warehouseName,
          anchor: 'center',
          zIndex: 20,
          content: `<div style="width:24px;height:24px;border-radius:6px;background:${WAREHOUSE_COLOR};border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4);cursor:pointer;color:#fff;font-size:12px;font-weight:bold;display:flex;align-items:center;justify-content:center">仓</div>`
        })
        const openInfo = () => openWarehouseInfoWindow(infoWindow, map, marker.getPosition(), w)
        marker.on('click', openInfo)
        markerEntries.push({ type: 'warehouse', kind: 'warehouse', id: w.id, marker, openInfo })
      })

    unlocated.merchant = merchants.filter(m => m.longitude == null || m.latitude == null).length
    merchants
      .filter(m => m.longitude != null && m.latitude != null)
      .forEach(m => {
        const typeKey = String(m.merchantType)
        if (counts[typeKey] !== undefined) counts[typeKey]++
        const color = MERCHANT_TYPE_COLORS[m.merchantType] || MERCHANT_TYPE_DEFAULT_COLOR
        const marker = new AMap.Marker({
          position: [Number(m.longitude), Number(m.latitude)],
          title: m.merchantName,
          anchor: 'center',
          zIndex: 10,
          content: `<div style="width:16px;height:16px;border-radius:50%;background:${color};border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4);cursor:pointer"></div>`
        })
        const openInfo = () => openMerchantInfoWindow(infoWindow, map, marker.getPosition(), m, color, typeLabel(m.merchantType))
        marker.on('click', openInfo)
        markerEntries.push({ type: typeKey, kind: 'merchant', id: m.id, marker, openInfo })
      })

    map.add(markerEntries.map(en => en.marker))
    applyFilter()
    fitView()
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  markerEntries.forEach(({ type, marker }) => {
    if (visibleTypes.value.includes(type)) {
      marker.show()
    } else {
      marker.hide()
    }
  })
}

function fitView() {
  if (!map) return
  const visible = markerEntries
    .filter(en => visibleTypes.value.includes(en.type))
    .map(en => en.marker)
  if (visible.length) {
    map.setFitView(visible, false, [80, 80, 80, 80])
  }
}

function typeLabel(type) {
  const dict = (merchant_type.value || []).find(d => String(d.value) === String(type))
  return dict ? dict.label : '未知类型'
}

/** 按路由参数（?merchantId= / ?warehouseId=）定位并打开气泡 */
function focusFromRoute() {
  const { merchantId, warehouseId } = route.query
  if (!merchantId && !warehouseId) return
  const entry = markerEntries.find(en =>
    (merchantId && en.kind === 'merchant' && String(en.id) === String(merchantId)) ||
    (warehouseId && en.kind === 'warehouse' && String(en.id) === String(warehouseId))
  )
  if (entry) {
    map.setZoomAndCenter(15, entry.marker.getPosition())
    entry.openInfo()
  } else {
    ElMessage.warning('该记录尚未标记位置，请先在编辑中「地图选点」')
  }
}
</script>

<style scoped>
.overview-map {
  width: 100%;
  height: calc(100vh - 160px);
  min-height: 480px;
}
.map-panel {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 10;
  background: rgba(255, 255, 255, 0.96);
  border-radius: 6px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  padding: 12px 16px;
  min-width: 190px;
}
.panel-title {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 8px;
}
.panel-checks {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.panel-checks .el-checkbox {
  height: 26px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.legend-item .dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.legend-item .mark.warehouse-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  background: #F56C6C;
  color: #fff;
  font-size: 10px;
  font-style: normal;
  font-weight: bold;
}
.panel-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
.panel-tip {
  margin-top: 10px;
  max-width: 200px;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
}
</style>
