<template>
  <el-dialog
    v-model="visible"
    title="地图选点"
    width="800px"
    append-to-body
    destroy-on-close
    @opened="initMap"
    @closed="destroyMap"
  >
    <template v-if="configured">
      <el-autocomplete
        v-model="keyword"
        :fetch-suggestions="querySearch"
        placeholder="搜索地点，如：杭州市西湖区xx路xx号"
        clearable
        style="width: 100%; margin-bottom: 10px"
        @select="handleSelect"
      >
        <template #default="{ item }">
          <div>{{ item.name }}</div>
          <div style="font-size: 12px; color: #909399">{{ item.district }}{{ item.address }}</div>
        </template>
      </el-autocomplete>
      <div ref="mapRef" class="picker-map" v-loading="mapLoading"></div>
      <div class="picker-result">
        <template v-if="picked.longitude">
          <el-tag size="small" type="success">已选点</el-tag>
          <span class="addr">{{ picked.address || '（未解析出地址，可手动填写）' }}</span>
          <span class="lnglat">{{ picked.longitude }}, {{ picked.latitude }}</span>
        </template>
        <span v-else class="tip">点击地图或搜索地点进行选点</span>
      </div>
    </template>
    <el-empty v-else description="未配置高德地图 Key">
      <div style="color: #909399; font-size: 13px; line-height: 1.8">
        请管理员前往「基础资料 → <router-link :to="ENV_CONFIG_ROUTE" style="color: #409EFF">环境配置</router-link>」
        填写高德地图 Key（页面内有详细申请步骤），保存后刷新本页即可使用。
      </div>
    </el-empty>
    <template #footer>
      <el-button type="primary" :disabled="!picked.longitude" @click="confirm">确 定</el-button>
      <el-button @click="visible = false">取 消</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, nextTick } from 'vue'
import { loadAMap, checkAMapConfigured, ENV_CONFIG_ROUTE } from '@/utils/amap'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // 回显用的初始位置
  longitude: { type: [Number, String], default: null },
  latitude: { type: [Number, String], default: null },
  address: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: val => emit('update:modelValue', val)
})

const configured = ref(true)
const mapRef = ref(null)
const mapLoading = ref(false)
const keyword = ref('')
const picked = reactive({ longitude: null, latitude: null, address: '' })

let map = null
let marker = null
let geocoder = null
let autoComplete = null

async function initMap() {
  configured.value = await checkAMapConfigured()
  if (!configured.value) return
  await nextTick()
  mapLoading.value = true
  try {
    const AMap = await loadAMap()
    const hasInit = props.longitude && props.latitude
    map = new AMap.Map(mapRef.value, {
      zoom: hasInit ? 15 : 11,
      center: hasInit ? [Number(props.longitude), Number(props.latitude)] : undefined,
      viewMode: '2D'
    })
    map.addControl(new AMap.ToolBar())
    map.addControl(new AMap.Scale())
    geocoder = new AMap.Geocoder()
    autoComplete = new AMap.AutoComplete()
    keyword.value = props.address || ''
    if (hasInit) {
      setPoint(Number(props.longitude), Number(props.latitude), props.address)
    }
    map.on('click', e => {
      setPoint(e.lnglat.getLng(), e.lnglat.getLat())
    })
  } catch (e) {
    ElMessage.error(e.message || '地图加载失败')
  } finally {
    mapLoading.value = false
  }
}

function setPoint(lng, lat, address) {
  lng = Number(lng.toFixed(6))
  lat = Number(lat.toFixed(6))
  picked.longitude = lng
  picked.latitude = lat
  const AMap = window.AMap
  if (!marker) {
    marker = new AMap.Marker({ position: [lng, lat], draggable: true })
    marker.on('dragend', e => {
      const pos = e.target.getPosition()
      setPoint(pos.getLng(), pos.getLat())
    })
    map.add(marker)
  } else {
    marker.setPosition([lng, lat])
  }
  if (address) {
    picked.address = address
  } else {
    picked.address = ''
    // 逆地理编码补全地址
    geocoder && geocoder.getAddress([lng, lat], (status, result) => {
      if (status === 'complete' && result.regeocode) {
        picked.address = result.regeocode.formattedAddress
      }
    })
  }
}

function querySearch(query, cb) {
  if (!query || !autoComplete) {
    cb([])
    return
  }
  autoComplete.search(query, (status, result) => {
    if (status === 'complete' && result.tips) {
      cb(result.tips.filter(tip => tip.location).map(tip => ({ ...tip, value: tip.name })))
    } else {
      cb([])
    }
  })
}

function handleSelect(item) {
  if (!item.location) return
  const lng = item.location.lng
  const lat = item.location.lat
  const fullAddress = `${item.district || ''}${typeof item.address === 'string' ? item.address : ''}${item.name}`
  map.setZoomAndCenter(16, [lng, lat])
  setPoint(lng, lat, fullAddress)
}

function confirm() {
  emit('confirm', { longitude: picked.longitude, latitude: picked.latitude, address: picked.address })
  visible.value = false
}

function destroyMap() {
  if (map) {
    map.destroy()
    map = null
    marker = null
    geocoder = null
    autoComplete = null
  }
  picked.longitude = null
  picked.latitude = null
  picked.address = ''
  keyword.value = ''
}
</script>

<style scoped>
.picker-map {
  width: 100%;
  height: 420px;
  border-radius: 4px;
  overflow: hidden;
}
.picker-result {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 24px;
  font-size: 13px;
}
.picker-result .addr {
  color: #303133;
}
.picker-result .lnglat {
  color: #909399;
}
.picker-result .tip {
  color: #909399;
}
</style>
