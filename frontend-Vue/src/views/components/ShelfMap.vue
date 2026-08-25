<template>
  <div class="shelf-map" :class="{ 'is-single': single }">
    <!-- 工具栏 -->
    <div v-if="!single && floors.length" class="toolbar">
      <el-radio-group v-model="currentFloor" size="small">
        <el-radio-button v-for="f in floors" :key="f" :label="f">{{ f }} 楼</el-radio-button>
      </el-radio-group>
      <div class="spacer"></div>
      <span class="tip">中键拖动旋转 · 左键拖动平移 · 滚轮缩放 · 方向键/WASD 调角度</span>
      <el-button size="small" icon="RefreshRight" @click="resetView">重置视角</el-button>
      <template v-if="editable">
        <el-tag v-if="editing" type="warning" effect="plain" size="small">拖动调位置，选中后拖橙色点缩放、蓝色「转」点旋转</el-tag>
        <el-button v-if="!editing" size="small" icon="Rank" @click="editing = true">编辑布局</el-button>
        <template v-else>
          <el-button size="small" type="primary" icon="Check" :loading="saving" @click="saveLayout">保存布局/视角</el-button>
          <el-button size="small" icon="OfficeBuilding" @click="addCustomObject('office')">办公室</el-button>
          <el-button size="small" icon="Box" @click="addCustomObject('pack')">打包台</el-button>
          <el-button size="small" icon="Guide" @click="addCustomObject('aisle')">通道</el-button>
          <el-button size="small" type="danger" :disabled="!selectedObjectId" @click="deleteSelectedObject">删除模块</el-button>
          <el-button size="small" icon="RefreshLeft" @click="autoArrange">自动排列</el-button>
          <el-button size="small" @click="cancelEdit">取消</el-button>
        </template>
      </template>
    </div>

    <div v-if="editing && selectedTarget" class="scale-panel">
      <span class="scale-title">{{ selectedTarget.title }}</span>
      <label>
        宽
        <el-input-number
          size="small"
          controls-position="right"
          :min="selectedTarget.minW"
          :max="selectedTarget.maxW"
          :step="selectedTarget.step"
          :model-value="selectedTarget.w"
          @update:modelValue="updateSelectedSize('w', $event)"
        />
      </label>
      <label>
        高
        <el-input-number
          size="small"
          controls-position="right"
          :min="selectedTarget.minH"
          :max="selectedTarget.maxH"
          :step="selectedTarget.step"
          :model-value="selectedTarget.h"
          @update:modelValue="updateSelectedSize('h', $event)"
        />
      </label>
      <label>
        深
        <el-input-number
          size="small"
          controls-position="right"
          :min="selectedTarget.minD"
          :max="selectedTarget.maxD"
          :step="selectedTarget.step"
          :model-value="selectedTarget.d"
          @update:modelValue="updateSelectedSize('d', $event)"
        />
      </label>
      <label>
        转°
        <el-input-number
          size="small"
          controls-position="right"
          :min="0"
          :max="359"
          :step="15"
          :model-value="selectedTarget.rot"
          @update:modelValue="setSelectedRotation($event)"
        />
      </label>
      <el-button size="small" @click="resetSelectedSize">重置尺寸</el-button>
    </div>

    <!-- 3D 场景 -->
    <div
      v-if="racks.length"
      ref="sceneEl"
      class="scene"
      :class="{ orbiting, panning }"
      :style="sceneStyle"
      tabindex="0"
      @mousedown="onSceneDown"
      @wheel.prevent="onWheel"
      @keydown="onKey"
    >
      <svg class="scene-svg" :style="svgPanStyle" :viewBox="svgViewBox" preserveAspectRatio="xMidYMid meet">
        <polygon class="floor" :points="floorPolygon" />
        <line
          v-for="(line, idx) in floorGrid"
          :key="'grid' + idx"
          class="floor-line"
          :x1="line.x1"
          :y1="line.y1"
          :x2="line.x2"
          :y2="line.y2"
        />
        <polygon
          v-for="face in renderFaces"
          :key="face.key"
          :class="face.className"
          :points="face.points"
          :fill="face.fill"
          :stroke="face.stroke"
          @mousedown.stop="onFaceDown($event, face)"
          @mouseenter.stop="face.loc && emit('cell-hover', face.loc)"
          @click.stop="onFaceClick(face)"
        >
          <title v-if="face.loc">{{ cellTitle(face.loc) }}</title>
          <title v-else-if="face.object">{{ face.object.name }}</title>
        </polygon>
        <g v-for="label in svgLabels" :key="label.key" class="svg-label">
          <text :x="label.x" :y="label.y" text-anchor="middle">{{ label.text }}</text>
          <text :x="label.x" :y="label.y + 14" text-anchor="middle" class="sub">{{ label.sub }}</text>
        </g>
        <g
          v-for="handle in resizeHandles"
          :key="handle.key"
          class="resize-handle"
          :class="'handle-' + handle.field"
          @mousedown.stop="onResizeHandleDown($event, handle)"
        >
          <line
            v-if="handle.anchor"
            class="resize-guide"
            :x1="handle.anchor.x"
            :y1="handle.anchor.y"
            :x2="handle.x"
            :y2="handle.y"
          />
          <circle :cx="handle.x" :cy="handle.y" r="7" />
          <text :x="handle.x" :y="handle.y - 12" text-anchor="middle">{{ handle.label }}</text>
        </g>
        <text
          v-for="label in cellLabels"
          :key="label.key"
          class="cell-label"
          :class="{ highlight: label.highlight }"
          :x="label.x"
          :y="label.y"
          text-anchor="middle"
        >{{ label.text }}</text>
      </svg>
    </div>
    <el-empty v-else :image-size="60" description="该仓库暂无可显示的货架（位置编码需形如 1-A0-1）" />

    <div v-if="!single && racks.length" class="hint">
      绿色=货位，<span style="color:#f56c6c">红色闪烁</span>=当前位置；中键拖动旋转、左键拖动平移、滚轮缩放{{ editable ? '；「编辑布局」可拖动货架排布' : '' }}
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useWmsStore } from '@/store/modules/wms'
import { parseLocationCode } from '@/utils/shelf'
import { saveShelfLayout } from '@/api/wms/warehouse'

const props = defineProps({
  warehouseId: { type: [Number, String], default: undefined },
  highlightId: { type: [Number, String], default: undefined },
  single: { type: Boolean, default: false },
  editable: { type: Boolean, default: false },
  locations: { type: Array, default: undefined },
})
const emit = defineEmits(['cell-hover', 'cell-click'])

const CELL_W = 54, CELL_H = 36, GAP = 8, DEPTH = 124, SLOT_DEPTH = 72
const DEF = { phi: 74, yaw: -38, scale: 1 }
const VIEW_MIN = 56
const VIEW_MAX = 88

// 视角
const phi = ref(DEF.phi)    // 俯仰角 rotateX
const yaw = ref(DEF.yaw)    // 水平旋转 rotateY
const scale = ref(DEF.scale)
const panX = ref(0)         // 左键平移
const panY = ref(0)

const sceneEl = ref(null)

const allLocations = computed(() => {
  const src = props.locations || useWmsStore().locationList || []
  return src
    .map(l => ({ ...l, coord: parseLocationCode(l.locationCode) }))
    .filter(l => l.coord && (props.warehouseId == null || String(l.warehouseId) === String(props.warehouseId)))
})

const highlightLoc = computed(() => allLocations.value.find(l => String(l.id) === String(props.highlightId)))
const floors = computed(() => [...new Set(allLocations.value.map(l => l.coord.floor))].sort((a, b) => a - b))

const currentFloor = ref(undefined)
watch(floors, (fs) => {
  if (props.single && highlightLoc.value) { currentFloor.value = highlightLoc.value.coord.floor; return }
  if (currentFloor.value == null || !fs.includes(currentFloor.value)) currentFloor.value = fs[0]
}, { immediate: true })
watch(highlightLoc, (h) => { if (h) currentFloor.value = h.coord.floor })
watch(currentFloor, () => {
  selectedObjectId.value = null
  selectedRackRow.value = null
})

// 布局 + 视角持久化
const layout = ref({})
function loadLayout() {
  const w = useWmsStore().warehouseMap?.get(props.warehouseId)
  try { layout.value = w && w.shelfLayout ? JSON.parse(w.shelfLayout) : {} }
  catch { layout.value = {} }
  const v = layout.value && layout.value._view
  phi.value = v && typeof v.phi === 'number' ? clamp(v.phi, VIEW_MIN, VIEW_MAX) : DEF.phi
  yaw.value = v && typeof v.yaw === 'number' ? v.yaw : DEF.yaw
  scale.value = v && typeof v.scale === 'number' ? v.scale : DEF.scale
  panX.value = 0
  panY.value = 0
}
watch(() => props.warehouseId, loadLayout, { immediate: true })

const customObjects = computed(() => {
  const list = Array.isArray(layout.value?._objects) ? layout.value._objects : []
  return list.filter(o => Number(o.floor) === Number(currentFloor.value))
})

const objectPresets = {
  office: { name: '办公室', w: 180, d: 130, h: 70, color: '#4f8fd9' },
  pack: { name: '打包台', w: 150, d: 88, h: 34, color: '#d9904f' },
  aisle: { name: '通道', w: 220, d: 56, h: 8, color: '#9aa8b8' },
}
const selectedObjectId = ref(null)
const selectedRackRow = ref(null)

const selectedObject = computed(() => customObjects.value.find(o => String(o.id) === String(selectedObjectId.value)))
const selectedRack = computed(() => racks.value.find(r => String(r.row) === String(selectedRackRow.value)))
const selectedTarget = computed(() => {
  if (selectedObject.value) {
    const obj = selectedObject.value
    return {
      type: 'object',
      title: `${obj.name || '自定义模块'}尺寸`,
      w: Number(obj.w) || 120,
      h: Number(obj.h) || 50,
      d: Number(obj.d) || 80,
      minW: 30,
      minH: 4,
      minD: 20,
      maxW: Infinity,
      maxH: Infinity,
      maxD: Infinity,
      step: 10,
      rot: Number(obj.rot) || 0,
    }
  }
  if (selectedRack.value) {
    const rack = selectedRack.value
    return {
      type: 'rack',
      title: `${rack.row}排货架缩放(%)`,
      w: Math.round(rack.scaleX * 100),
      h: Math.round(rack.scaleY * 100),
      d: Math.round(rack.scaleZ * 100),
      minW: 20,
      minH: 20,
      minD: 20,
      maxW: Infinity,
      maxH: Infinity,
      maxD: Infinity,
      step: 5,
      rot: Number(layout.value?.[currentFloor.value]?.[rack.row]?.rot) || 0,
    }
  }
  return null
})

function objectTypeName(type) {
  return ({ office: '办公室', pack: '打包台', aisle: '通道' })[type] || '自定义模块'
}

function addCustomObject(type) {
  const preset = objectPresets[type] || objectPresets.office
  const id = `obj_${Date.now()}_${Math.random().toString(16).slice(2, 6)}`
  const rs = racks.value
  const maxX = rs.length ? Math.max(...rs.map(r => r.pos.x + r.w)) : 0
  const obj = {
    id,
    type,
    name: preset.name,
    floor: currentFloor.value,
    x: Math.round(maxX + 120),
    z: 20,
    w: preset.w,
    d: preset.d,
    h: preset.h,
    color: preset.color,
  }
  if (!Array.isArray(layout.value._objects)) layout.value._objects = []
  layout.value._objects = [...layout.value._objects, obj]
  selectedObjectId.value = id
  selectedRackRow.value = null
}

function deleteSelectedObject() {
  if (!selectedObjectId.value || !Array.isArray(layout.value._objects)) return
  layout.value._objects = layout.value._objects.filter(o => String(o.id) !== String(selectedObjectId.value))
  selectedObjectId.value = null
}

function updateSelectedSize(field, value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return
  if (selectedObject.value) {
    const key = field === 'd' ? 'd' : field
    const list = Array.isArray(layout.value._objects) ? layout.value._objects : []
    layout.value._objects = list.map(o => String(o.id) === String(selectedObject.value.id)
      ? { ...o, [key]: Math.round(n) }
      : o)
    return
  }
  if (!selectedRack.value) return
  const key = field === 'w' ? 'sx' : (field === 'h' ? 'sy' : 'sd')
  ensureSlot(currentFloor.value, selectedRack.value.row)
  layout.value[currentFloor.value][selectedRack.value.row] = {
    ...layout.value[currentFloor.value][selectedRack.value.row],
    [key]: +(n / 100).toFixed(2),
  }
}

function resetSelectedSize() {
  if (selectedObject.value) {
    const preset = objectPresets[selectedObject.value.type] || objectPresets.office
    const list = Array.isArray(layout.value._objects) ? layout.value._objects : []
    layout.value._objects = list.map(o => String(o.id) === String(selectedObject.value.id)
      ? { ...o, w: preset.w, h: preset.h, d: preset.d }
      : o)
    return
  }
  if (!selectedRack.value) return
  ensureSlot(currentFloor.value, selectedRack.value.row)
  layout.value[currentFloor.value][selectedRack.value.row] = {
    ...layout.value[currentFloor.value][selectedRack.value.row],
    sx: 1,
    sy: 1,
    sd: 1,
  }
}

function buildRacks(floor, rowFilter) {
  const byRow = new Map()
  allLocations.value.filter(l => l.coord.floor === floor).forEach(l => {
    if (rowFilter && l.coord.row !== rowFilter) return
    if (!byRow.has(l.coord.row)) byRow.set(l.coord.row, [])
    byRow.get(l.coord.row).push(l)
  })
  const saved = (layout.value && layout.value[floor]) || {}
  let auto = 0
  return [...byRow.entries()].sort((a, b) => String(a[0]).localeCompare(String(b[0]))).map(([row, cells]) => {
    const cols = cells.map(c => c.coord.col), cellNos = cells.map(c => c.coord.cell)
    const minCol = Math.min(...cols), maxCol = Math.max(...cols)
    const minCell = Math.min(...cellNos), maxCell = Math.max(...cellNos)
    const nCol = maxCol - minCol + 1, nCell = maxCell - minCell + 1
    const baseW = nCol * CELL_W + (nCol + 1) * GAP
    const baseH = nCell * CELL_H + (nCell + 1) * GAP
    const savedRow = saved[row] || {}
    const scaleX = Math.max(0.2, Number(savedRow.sx) || 1)
    const scaleY = Math.max(0.2, Number(savedRow.sy) || 1)
    const scaleZ = Math.max(0.2, Number(savedRow.sd) || 1)
    const rot = Number(savedRow.rot) || 0
    const w = baseW * scaleX
    const h = baseH * scaleY
    const depth = DEPTH * scaleZ
    const defaultPos = { x: (auto++) * (w + 150), y: 0 }
    const pos = {
      x: typeof savedRow.x === 'number' ? savedRow.x : defaultPos.x,
      y: typeof savedRow.y === 'number' ? savedRow.y : defaultPos.y,
    }
    return {
      row,
      cells,
      minCol,
      maxCol,
      minCell,
      maxCell,
      nCol,
      nCell,
      baseW,
      baseH,
      w,
      h,
      depth,
      scaleX,
      scaleY,
      scaleZ,
      rot,
      cellW: CELL_W * scaleX,
      cellH: CELL_H * scaleY,
      gapX: GAP * scaleX,
      gapY: GAP * scaleY,
      slotDepth: SLOT_DEPTH * scaleZ,
      pos,
    }
  })
}

const racks = computed(() => {
  if (currentFloor.value == null) return []
  const rowFilter = props.single && highlightLoc.value ? highlightLoc.value.coord.row : null
  return buildRacks(currentFloor.value, rowFilter)
})

// ---- SVG 真 3D 投影 ----
function project3d(p) {
  const Y = yaw.value * Math.PI / 180
  const view = clamp(phi.value, VIEW_MIN, VIEW_MAX)
  const groundDepth = 0.24 + ((VIEW_MAX - view) / (VIEW_MAX - VIEW_MIN)) * 0.58
  const rx = p.x * Math.cos(Y) - p.z * Math.sin(Y)
  const rz = p.x * Math.sin(Y) + p.z * Math.cos(Y)
  return {
    x: rx * scale.value,
    y: (-rz * groundDepth - p.y * 0.92) * scale.value,
    depth: rz,
  }
}

// 绕竖直(Y)轴旋转一个世界坐标点（围绕 pivot 的 x/z）
function rotateYpt(pt, pivot, deg) {
  if (!deg) return pt
  const a = deg * Math.PI / 180, cos = Math.cos(a), sin = Math.sin(a)
  const dx = pt.x - pivot.x, dz = pt.z - pivot.z
  return { x: pivot.x + dx * cos - dz * sin, y: pt.y, z: pivot.z + dx * sin + dz * cos }
}

function cuboid(id, x, y, z, w, h, d, kind, loc = null, object = null, rack = null, xform = null) {
  const p = {
    a: { x, y, z },
    b: { x: x + w, y, z },
    c: { x: x + w, y: y + h, z },
    d: { x, y: y + h, z },
    e: { x, y, z: z + d },
    f: { x: x + w, y, z: z + d },
    g: { x: x + w, y: y + h, z: z + d },
    h: { x, y: y + h, z: z + d },
  }
  if (xform) { for (const k in p) p[k] = xform(p[k]) }
  const colors = faceColors(kind, loc, object)
  return [
    face(`${id}-back`, [p.e, p.h, p.g, p.f], colors.back, kind, loc, object, rack),
    face(`${id}-left`, [p.a, p.d, p.h, p.e], colors.left, kind, loc, object, rack),
    face(`${id}-right`, [p.b, p.f, p.g, p.c], colors.right, kind, loc, object, rack),
    face(`${id}-top`, [p.d, p.c, p.g, p.h], colors.top, kind, loc, object, rack),
    face(`${id}-front`, [p.a, p.b, p.c, p.d], colors.front, kind, loc, object, rack),
  ]
}

function face(key, points, fill, kind, loc, object, rack) {
  const projected = points.map(project3d)
  const avgDepth = projected.reduce((sum, p) => sum + p.depth, 0) / projected.length
  const avgHeight = points.reduce((sum, p) => sum + p.y, 0) / points.length
  return {
    key,
    rawPoints: projected,
    raw: points,
    depth: avgDepth - avgHeight * 0.25,
    fill,
    stroke: kind === 'slot' ? '#5fa66a' : (kind === 'object' ? '#4b5563' : '#60758f'),
    className: [
      kind === 'slot' ? 'svg-slot-face' : (kind === 'object' ? 'svg-object-face' : 'svg-rack-face'),
      loc && String(loc.id) === String(props.highlightId) ? 'is-highlight' : '',
      object && String(object.id) === String(selectedObjectId.value) ? 'is-selected' : '',
      rack && String(rack.row) === String(selectedRackRow.value) ? 'is-selected' : '',
    ].filter(Boolean).join(' '),
    loc,
    object,
    rack,
  }
}

function faceColors(kind, loc, object = null) {
  const highlight = loc && String(loc.id) === String(props.highlightId)
  if (kind === 'slot' && highlight) {
    return { front: '#f56c6c', top: '#ff9b9b', right: '#df5a5a', left: '#cf4a4a', back: '#bf4141', bottom: '#a93a3a' }
  }
  if (kind === 'slot') {
    return { front: '#dff4df', top: '#f4fff4', right: '#9dd59f', left: '#86c58b', back: '#b6dfb8', bottom: '#6ea976' }
  }
  if (kind === 'object') {
    const base = object?.color || '#4f8fd9'
    return shadeSet(base)
  }
  return { front: '#8298b5', top: '#c8d7ea', right: '#8fa5c1', left: '#748aa7', back: '#6c809c', bottom: '#5f748d' }
}

function shadeSet(color) {
  return {
    front: color,
    top: mixColor(color, '#ffffff', 0.34),
    right: mixColor(color, '#000000', 0.12),
    left: mixColor(color, '#000000', 0.2),
    back: mixColor(color, '#000000', 0.28),
    bottom: mixColor(color, '#000000', 0.35),
  }
}

function mixColor(hex, mix, amount) {
  const a = hexToRgb(hex)
  const b = hexToRgb(mix)
  const c = a.map((v, i) => Math.round(v + (b[i] - v) * amount))
  return `rgb(${c[0]}, ${c[1]}, ${c[2]})`
}

function hexToRgb(hex) {
  const v = String(hex || '#4f8fd9').replace('#', '')
  const full = v.length === 3 ? v.split('').map(s => s + s).join('') : v.padEnd(6, '0').slice(0, 6)
  return [0, 2, 4].map(i => parseInt(full.slice(i, i + 2), 16))
}

const rawScene = computed(() => {
  const faces = []
  const labels = []
  const cellTexts = []
  const handles = []
  const rs = racks.value
  const pad = 120
  let minX = -pad, maxX = pad, minZ = -pad, maxZ = DEPTH + pad
  rs.forEach(r => {
    const x = r.pos.x
    const z = r.pos.y
    const rot = r.rot || 0
    const pivot = { x: x + r.w / 2, z: z + r.depth / 2 }
    const xf = (pt) => rotateYpt(pt, pivot, rot)
    minX = Math.min(minX, x - pad)
    maxX = Math.max(maxX, x + r.w + pad)
    minZ = Math.min(minZ, z - pad)
    maxZ = Math.max(maxZ, z + r.depth + pad)

    faces.push(...cuboid(`rack-${r.row}-back`, x, 0, z + r.depth - 7, r.w, r.h, 7, 'rack', null, null, r, xf))
    const post = 8
    faces.push(...cuboid(`rack-${r.row}-fl`, x - post / 2, 0, z - post / 2, post, r.h + 8, post, 'rack', null, null, r, xf))
    faces.push(...cuboid(`rack-${r.row}-fr`, x + r.w - post / 2, 0, z - post / 2, post, r.h + 8, post, 'rack', null, null, r, xf))
    faces.push(...cuboid(`rack-${r.row}-bl`, x - post / 2, 0, z + r.depth - post / 2, post, r.h + 8, post, 'rack', null, null, r, xf))
    faces.push(...cuboid(`rack-${r.row}-br`, x + r.w - post / 2, 0, z + r.depth - post / 2, post, r.h + 8, post, 'rack', null, null, r, xf))

    for (let n = 0; n <= r.nCell; n++) {
      const boardY = Math.max(0, r.gapY / 2 + n * (r.cellH + r.gapY) - 4)
      faces.push(...cuboid(`rack-${r.row}-board-${n}`, x, boardY, z, r.w, 6, r.depth, 'rack', null, null, r, xf))
    }

    r.cells.forEach(loc => {
      const col = loc.coord.col - r.minCol
      // 货架编号自上而下递增：格号越小位置越高，所以最大格号排在最底层
      const layer = r.maxCell - loc.coord.cell
      const sx = x + r.gapX + col * (r.cellW + r.gapX)
      const sy = r.gapY + layer * (r.cellH + r.gapY)
      const sz = z + Math.min(18 * r.scaleZ, r.depth * 0.22)
      faces.push(...cuboid(`slot-${loc.id}`, sx, sy, sz, r.cellW, r.cellH, r.slotDepth, 'slot', loc, null, null, xf))
      const center = project3d(xf({ x: sx + r.cellW / 2, y: sy + r.cellH / 2, z: sz - 2 }))
      cellTexts.push({
        key: `txt-${loc.id}`,
        raw: center,
        text: `${loc.coord.col}-${loc.coord.cell}`,
        highlight: String(loc.id) === String(props.highlightId),
      })
    })

    const label = project3d(xf({ x: x + r.w / 2, y: r.h + 34, z: z + r.depth / 2 }))
    labels.push({ key: `label-${r.row}`, raw: label, text: `${r.row}排`, sub: `${r.nCol}列 / ${r.nCell}层` })
  })

  customObjects.value.forEach(obj => {
    const x = Number(obj.x) || 0
    const z = Number(obj.z) || 0
    const w = Number(obj.w) || 120
    const d = Number(obj.d) || 80
    const h = Number(obj.h) || 50
    const rot = Number(obj.rot) || 0
    const pivot = { x: x + w / 2, z: z + d / 2 }
    const xf = (pt) => rotateYpt(pt, pivot, rot)
    minX = Math.min(minX, x - pad)
    maxX = Math.max(maxX, x + w + pad)
    minZ = Math.min(minZ, z - pad)
    maxZ = Math.max(maxZ, z + d + pad)
    faces.push(...cuboid(`object-${obj.id}`, x, 0, z, w, h, d, 'object', null, obj, null, xf))
    const label = project3d(xf({ x: x + w / 2, y: h + 22, z: z + d / 2 }))
    labels.push({ key: `object-label-${obj.id}`, raw: label, text: obj.name || '自定义模块', sub: objectTypeName(obj.type) })
  })

  if (editing.value && selectedTarget.value) {
    let target = null
    if (selectedObject.value) {
      const obj = selectedObject.value
      target = {
        id: obj.id,
        type: 'object',
        x: Number(obj.x) || 0,
        z: Number(obj.z) || 0,
        w: Number(obj.w) || 120,
        h: Number(obj.h) || 50,
        d: Number(obj.d) || 80,
      }
    } else if (selectedRack.value) {
      const rack = selectedRack.value
      target = {
        id: rack.row,
        type: 'rack',
        x: rack.pos.x,
        z: rack.pos.y,
        w: rack.w,
        h: rack.h,
        d: rack.depth,
        rot: Number(layout.value?.[currentFloor.value]?.[rack.row]?.rot) || 0,
      }
    }
    if (target) {
      const rot = target.rot || 0
      const center = { x: target.x + target.w / 2, y: target.h / 2, z: target.z + target.d / 2 }
      const pivot = { x: center.x, z: center.z }
      const xf = (p) => rotateYpt(p, pivot, rot)
      const makeHandle = (field, label, point, kind = 'resize') => ({
        key: `resize-${target.type}-${target.id}-${field}`,
        field,
        kind,
        label,
        raw: project3d(xf(point)),
        anchorRaw: project3d(xf(center)),
      })
      handles.push(
        makeHandle('w', '宽', { x: target.x + target.w + 18, y: target.h / 2, z: target.z + target.d / 2 }),
        makeHandle('d', '深', { x: target.x + target.w / 2, y: target.h / 2, z: target.z + target.d + 18 }),
        makeHandle('h', '高', { x: target.x + target.w / 2, y: target.h + 24, z: target.z + target.d / 2 }),
        makeHandle('rot', '转', { x: target.x + target.w / 2, y: target.h + 64, z: target.z + target.d / 2 }, 'rotate'),
      )
    }
  }

  const floorPoints = [
    { x: minX, y: 0, z: minZ },
    { x: maxX, y: 0, z: minZ },
    { x: maxX, y: 0, z: maxZ },
    { x: minX, y: 0, z: maxZ },
  ].map(project3d)
  const grid = []
  for (let x = Math.ceil(minX / 80) * 80; x <= maxX; x += 80) {
    const a = project3d({ x, y: 0, z: minZ })
    const b = project3d({ x, y: 0, z: maxZ })
    grid.push({ a, b })
  }
  for (let z = Math.ceil(minZ / 80) * 80; z <= maxZ; z += 80) {
    const a = project3d({ x: minX, y: 0, z })
    const b = project3d({ x: maxX, y: 0, z })
    grid.push({ a, b })
  }

  const all = [
    ...faces.flatMap(f => f.rawPoints),
    ...floorPoints,
    ...grid.flatMap(l => [l.a, l.b]),
    ...labels.map(l => l.raw),
    ...cellTexts.map(l => l.raw),
    ...handles.flatMap(h => [h.raw, h.anchorRaw]),
  ]
  const minPX = Math.min(...all.map(p => p.x)) - 80
  const maxPX = Math.max(...all.map(p => p.x)) + 80
  const minPY = Math.min(...all.map(p => p.y)) - 80
  const maxPY = Math.max(...all.map(p => p.y)) + 80
  const ox = -minPX
  const oy = -minPY
  return {
    width: Math.max(600, maxPX - minPX),
    height: Math.max(props.single ? 190 : 340, maxPY - minPY),
    faces: faces.sort((a, b) => b.depth - a.depth).map(f => ({ ...f, points: pointsString(f.rawPoints, ox, oy) })),
    floor: pointsString(floorPoints, ox, oy),
    grid: grid.map(l => ({ x1: l.a.x + ox, y1: l.a.y + oy, x2: l.b.x + ox, y2: l.b.y + oy })),
    labels: labels.map(l => ({ ...l, x: l.raw.x + ox, y: l.raw.y + oy })),
    cellTexts: cellTexts.map(l => ({ ...l, x: l.raw.x + ox, y: l.raw.y + oy })),
    handles: handles.map(h => ({ ...h, x: h.raw.x + ox, y: h.raw.y + oy, anchor: { x: h.anchorRaw.x + ox, y: h.anchorRaw.y + oy } })),
  }
})

function pointsString(points, ox, oy) {
  return points.map(p => `${(p.x + ox).toFixed(1)},${(p.y + oy).toFixed(1)}`).join(' ')
}

const svgViewBox = computed(() => `0 0 ${rawScene.value.width.toFixed(1)} ${rawScene.value.height.toFixed(1)}`)
const renderFaces = computed(() => rawScene.value.faces)
const floorPolygon = computed(() => rawScene.value.floor)
const floorGrid = computed(() => rawScene.value.grid)
const svgLabels = computed(() => rawScene.value.labels)
const cellLabels = computed(() => rawScene.value.cellTexts)
const resizeHandles = computed(() => rawScene.value.handles)

// ---- 居中 & 场景尺寸（正交投影）----
function proj(fx, fz) {
  const Y = yaw.value * Math.PI / 180, P = phi.value * Math.PI / 180
  return { sx: Math.cos(Y) * fx + Math.sin(Y) * fz, sy: Math.sin(Y) * Math.sin(P) * fx - Math.cos(Y) * Math.sin(P) * fz }
}
const bounds = computed(() => {
  const rs = racks.value
  if (!rs.length) return { cx: 0, cy: 0, height: props.single ? 180 : 300 }
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity, maxH = 0
  rs.forEach(r => {
    minX = Math.min(minX, r.pos.x); minY = Math.min(minY, r.pos.y)
    maxX = Math.max(maxX, r.pos.x + r.w); maxY = Math.max(maxY, r.pos.y + DEPTH + 80)
    maxH = Math.max(maxH, r.h)
  })
  const cx = (minX + maxX) / 2, cy = (minY + maxY) / 2
  const W = maxX - minX, H = maxY - minY
  const corners = [[-W / 2, -H / 2], [W / 2, -H / 2], [-W / 2, H / 2], [W / 2, H / 2]].map(([x, y]) => proj(x, y))
  const sys = corners.map(c => c.sy)
  const projH = (Math.max(...sys) - Math.min(...sys)) * scale.value + maxH * Math.cos(phi.value * Math.PI / 180) * scale.value
  const pad = props.single ? 60 : 120
  return { cx, cy, height: Math.max(projH + pad, props.single ? 170 : 300) }
})

// ---- 样式 ----
const sceneStyle = computed(() => ({ height: rawScene.value.height + 'px' }))
const worldStyle = computed(() => ({
  transform: `scale(${scale.value}) rotateX(${phi.value}deg) rotateY(${yaw.value}deg)`,
  left: '50%', top: '50%',
}))
// 标签始终面向相机
const labelStyle = computed(() => ({ transform: `rotateY(${-yaw.value}deg) rotateX(${-phi.value}deg)` }))

const rackStyle = (rack) => ({ transform: `translate3d(${rack.pos.x - bounds.value.cx}px, 0, ${rack.pos.y - bounds.value.cy}px)` })
const boxStyle = (rack) => ({ width: rack.w + 'px', height: rack.h + 'px', transform: `translateY(${-rack.h}px)` })

// 6 面（盒子左下后角为原点，向 +x 宽、+z 深、-y(屏幕上) 高度已由 box 容器处理）
const faceFront = (r) => ({ width: r.w + 'px', height: r.h + 'px', transform: `translateZ(${DEPTH}px)` })
const faceBack = (r) => ({ width: r.w + 'px', height: r.h + 'px', transform: `translateZ(0px)` })
const faceLeft = (r) => ({ width: DEPTH + 'px', height: r.h + 'px', transform: `rotateY(-90deg) translateZ(0px)`, transformOrigin: 'left' })
const faceRight = (r) => ({ width: DEPTH + 'px', height: r.h + 'px', transform: `rotateY(90deg) translateZ(${-r.w}px)`, transformOrigin: 'right', left: (r.w - DEPTH) + 'px' })
const faceTop = (r) => ({ width: r.w + 'px', height: DEPTH + 'px', transform: `rotateX(90deg) translateZ(${DEPTH}px)`, transformOrigin: 'top' })
const faceBottom = (r) => ({ width: r.w + 'px', height: DEPTH + 'px', transform: `rotateX(-90deg)`, transformOrigin: 'top', top: (r.h) + 'px' })
const sideLines = (rack) => Array.from({ length: Math.max(1, rack.nCell - 1) }, (_, i) => i + 1)
const topLines = (rack) => Array.from({ length: Math.max(1, rack.nCol - 1) }, (_, i) => i + 1)
const sideLineStyle = (n, rack) => ({ top: `${(n / rack.nCell) * 100}%` })
const topLineStyle = (n, rack) => ({ left: `${(n / rack.nCol) * 100}%` })
const rackCols = (rack) => Array.from({ length: rack.nCol }, (_, i) => rack.minCol + i)
const rackLayers = (rack) => Array.from({ length: rack.nCell }, (_, i) => rack.minCell + i)
const shelfBoards = (rack) => Array.from({ length: rack.nCell + 1 }, (_, i) => i)
const uprights = (rack) => Array.from({ length: rack.nCol + 1 }, (_, i) => i)
const frameStyle = (rack) => ({ width: rack.w + 'px', height: rack.h + 'px' })
const shelfBoardStyle = (n, rack) => ({ top: `${(n / rack.nCell) * 100}%` })
const uprightStyle = (n, rack) => ({ left: `${(n / rack.nCol) * 100}%` })
const columnAxisStyle = (rack) => ({ gridTemplateColumns: `repeat(${rack.nCol}, ${CELL_W}px)`, gap: GAP + 'px', padding: `0 ${GAP}px` })
const layerAxisStyle = (rack) => ({ gridTemplateRows: `repeat(${rack.nCell}, ${CELL_H}px)`, gap: GAP + 'px', padding: `${GAP}px 0` })

const groundStyle = computed(() => ({ transform: `rotateX(90deg)` }))

const gridStyle = (rack) => ({
  width: rack.w + 'px',
  height: rack.h + 'px'
})
const slotStyle = (rack, loc) => {
  const col = loc.coord.col - rack.minCol
  const layer = loc.coord.cell - rack.minCell
  return {
    width: CELL_W + 'px',
    height: CELL_H + 'px',
    left: (GAP + col * (CELL_W + GAP)) + 'px',
    top: (GAP + layer * (CELL_H + GAP)) + 'px',
    '--slot-depth': SLOT_DEPTH + 'px',
  }
}
const cellTitle = (loc) => {
  const c = loc.coord
  return `${loc.locationCode || ''}：${c.floor}楼 / ${c.row}排 / ${c.col}列 / ${c.cell}层 ${loc.locationName || ''}`.trim()
}

// ---- 视角：中键旋转 / 左键平移 / 滚轮缩放 / 键盘 ----
const orbiting = ref(false)
const panning = ref(false)
const svgPanStyle = computed(() => ({ transform: `translate(${panX.value}px, ${panY.value}px)` }))
let orbitStart = null
let panStart = null
function onSceneDown(e) {
  sceneEl.value?.focus()
  if (e.button === 1) {
    // 中键：旋转视角
    e.preventDefault()
    orbiting.value = true
    orbitStart = { mx: e.clientX, my: e.clientY, yaw: yaw.value, phi: phi.value }
    window.addEventListener('mousemove', onOrbit)
    window.addEventListener('mouseup', onOrbitUp)
  } else if (e.button === 0) {
    // 左键：平移（货架/模块的拖动由各自的 face 处理，不会冒泡到这里）
    e.preventDefault()
    panning.value = true
    panStart = { mx: e.clientX, my: e.clientY, x: panX.value, y: panY.value }
    window.addEventListener('mousemove', onPan)
    window.addEventListener('mouseup', onPanUp)
  }
}
function onPan(e) {
  if (!panStart) return
  // 限制平移范围：最多拖到场景一半多一点，避免内容被拖出视野
  const el = sceneEl.value
  const limX = el ? el.clientWidth * 0.6 : 600
  const limY = el ? el.clientHeight * 0.6 : 400
  panX.value = clamp(panStart.x + (e.clientX - panStart.mx), -limX, limX)
  panY.value = clamp(panStart.y + (e.clientY - panStart.my), -limY, limY)
}
function onPanUp() { panning.value = false; panStart = null; window.removeEventListener('mousemove', onPan); window.removeEventListener('mouseup', onPanUp) }
function onOrbit(e) {
  if (!orbitStart) return
  yaw.value = Math.round(orbitStart.yaw + (e.clientX - orbitStart.mx) * 0.5)
  phi.value = clamp(Math.round(orbitStart.phi - (e.clientY - orbitStart.my) * 0.4), VIEW_MIN, VIEW_MAX)
}
function onOrbitUp() { orbiting.value = false; orbitStart = null; window.removeEventListener('mousemove', onOrbit); window.removeEventListener('mouseup', onOrbitUp) }
function onWheel(e) { scale.value = clamp(+(scale.value - e.deltaY * 0.001).toFixed(2), 0.4, 2.2) }
function onKey(e) {
  const k = e.key
  if (k === 'ArrowLeft' || k.toLowerCase() === 'a') yaw.value -= 5
  else if (k === 'ArrowRight' || k.toLowerCase() === 'd') yaw.value += 5
  else if (k === 'ArrowUp' || k.toLowerCase() === 'w') phi.value = clamp(phi.value + 4, VIEW_MIN, VIEW_MAX)
  else if (k === 'ArrowDown' || k.toLowerCase() === 's') phi.value = clamp(phi.value - 4, VIEW_MIN, VIEW_MAX)
  else if (k === '+' || k === '=') scale.value = clamp(+(scale.value + 0.1).toFixed(2), 0.4, 2.2)
  else if (k === '-') scale.value = clamp(+(scale.value - 0.1).toFixed(2), 0.4, 2.2)
  else if (k === '0') resetView()
  else return
  e.preventDefault()
}
function clamp(v, a, b) { return Math.min(b, Math.max(a, v)) }
function resetView() { phi.value = DEF.phi; yaw.value = DEF.yaw; scale.value = DEF.scale; panX.value = 0; panY.value = 0 }

// ---- 拖拽货架 ----
const editing = ref(false)
const saving = ref(false)
const dragRow = ref(null)
const dragObjectId = ref(null)
let dragStart = null
let resizeStart = null
function screenToFloor(dx, dy) {
  const Y = yaw.value * Math.PI / 180
  const view = clamp(phi.value, VIEW_MIN, VIEW_MAX)
  const groundDepth = 0.24 + ((VIEW_MAX - view) / (VIEW_MAX - VIEW_MIN)) * 0.58
  const s = scale.value || 1
  const sx = dx / s, sy = dy / s
  const floorSy = -sy / groundDepth
  return { fx: Math.cos(Y) * sx + Math.sin(Y) * floorSy, fz: -Math.sin(Y) * sx + Math.cos(Y) * floorSy }
}
function onFaceClick(face) {
  if (face.loc) emit('cell-click', face.loc)
  if (face.object) {
    selectedObjectId.value = face.object.id
    selectedRackRow.value = null
  } else if (face.rack && editing.value) {
    selectedRackRow.value = face.rack.row
    selectedObjectId.value = null
  }
}
function onFaceDown(e, face) {
  if (!editing.value || face.loc || (!face.object && !face.rack)) return
  e.preventDefault()
  if (face.object) {
    selectedObjectId.value = face.object.id
    selectedRackRow.value = null
    dragObjectId.value = face.object.id
    dragStart = { mx: e.clientX, my: e.clientY, x: Number(face.object.x) || 0, z: Number(face.object.z) || 0, objectId: face.object.id }
  } else {
    selectedRackRow.value = face.rack.row
    selectedObjectId.value = null
    dragRow.value = face.rack.row
    dragStart = { mx: e.clientX, my: e.clientY, x: face.rack.pos.x, y: face.rack.pos.y, row: face.rack.row }
  }
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', onUp)
}
function onRackDown(e, rack) {
  if (!editing.value) return
  e.preventDefault(); e.stopPropagation()
  dragRow.value = rack.row
  dragStart = { mx: e.clientX, my: e.clientY, x: rack.pos.x, y: rack.pos.y, row: rack.row }
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', onUp)
}
function onDrag(e) {
  if (!dragStart) return
  const { fx, fz } = screenToFloor(e.clientX - dragStart.mx, e.clientY - dragStart.my)
  if (dragStart.objectId) {
    const list = Array.isArray(layout.value._objects) ? layout.value._objects : []
    layout.value._objects = list.map(o => String(o.id) === String(dragStart.objectId)
      ? { ...o, x: Math.round(dragStart.x + fx), z: Math.round(dragStart.z + fz) }
      : o)
    return
  }
  ensureSlot(currentFloor.value, dragStart.row)
  layout.value[currentFloor.value][dragStart.row] = {
    ...layout.value[currentFloor.value][dragStart.row],
    x: Math.round(dragStart.x + fx),
    y: Math.round(dragStart.y + fz),
  }
}
function onUp() { dragRow.value = null; dragObjectId.value = null; dragStart = null; window.removeEventListener('mousemove', onDrag); window.removeEventListener('mouseup', onUp) }

function onResizeHandleDown(e, handle) {
  if (!editing.value || !selectedTarget.value) return
  if (handle.field === 'rot') { onRotateHandleDown(e); return }
  e.preventDefault()
  const target = selectedTarget.value
  resizeStart = {
    mx: e.clientX,
    my: e.clientY,
    field: handle.field,
    type: target.type,
    w: target.type === 'rack' ? selectedRack.value.w : target.w,
    h: target.type === 'rack' ? selectedRack.value.h : target.h,
    d: target.type === 'rack' ? selectedRack.value.depth : target.d,
    baseW: selectedRack.value?.baseW || 1,
    baseH: selectedRack.value?.baseH || 1,
    minW: target.minW,
    maxW: target.maxW,
    minH: target.minH,
    maxH: target.maxH,
    minD: target.minD,
    maxD: target.maxD,
  }
  window.addEventListener('mousemove', onResizeMove)
  window.addEventListener('mouseup', onResizeUp)
}

function onResizeMove(e) {
  if (!resizeStart) return
  const { fx, fz } = screenToFloor(e.clientX - resizeStart.mx, e.clientY - resizeStart.my)
  const heightDelta = -(e.clientY - resizeStart.my) / ((scale.value || 1) * 0.92)
  if (resizeStart.type === 'rack') {
    if (!selectedRack.value) return
    let percent
    if (resizeStart.field === 'w') percent = (resizeStart.w + fx) / resizeStart.baseW * 100
    else if (resizeStart.field === 'd') percent = (resizeStart.d + fz) / DEPTH * 100
    else percent = (resizeStart.h + heightDelta) / resizeStart.baseH * 100
    const min = resizeStart.field === 'w' ? resizeStart.minW : (resizeStart.field === 'd' ? resizeStart.minD : resizeStart.minH)
    const max = resizeStart.field === 'w' ? resizeStart.maxW : (resizeStart.field === 'd' ? resizeStart.maxD : resizeStart.maxH)
    updateSelectedSize(resizeStart.field, Math.round(clamp(percent, min, max)))
    return
  }
  if (resizeStart.field === 'w') updateSelectedSize('w', clamp(Math.round(resizeStart.w + fx), resizeStart.minW, resizeStart.maxW))
  else if (resizeStart.field === 'd') updateSelectedSize('d', clamp(Math.round(resizeStart.d + fz), resizeStart.minD, resizeStart.maxD))
  else updateSelectedSize('h', clamp(Math.round(resizeStart.h + heightDelta), resizeStart.minH, resizeStart.maxH))
}

function onResizeUp() {
  resizeStart = null
  window.removeEventListener('mousemove', onResizeMove)
  window.removeEventListener('mouseup', onResizeUp)
}

// ---- 旋转（绕竖直轴）----
let rotateStart = null
function currentRot() {
  if (selectedObject.value) return Number(selectedObject.value.rot) || 0
  if (selectedRack.value) return Number(layout.value?.[currentFloor.value]?.[selectedRack.value.row]?.rot) || 0
  return 0
}
function setSelectedRotation(deg) {
  const d = ((Math.round(Number(deg) || 0) % 360) + 360) % 360
  if (selectedObject.value) {
    const list = Array.isArray(layout.value._objects) ? layout.value._objects : []
    layout.value._objects = list.map(o => String(o.id) === String(selectedObject.value.id) ? { ...o, rot: d } : o)
    return
  }
  if (!selectedRack.value) return
  ensureSlot(currentFloor.value, selectedRack.value.row)
  layout.value[currentFloor.value][selectedRack.value.row] = {
    ...layout.value[currentFloor.value][selectedRack.value.row],
    rot: d,
  }
}
function onRotateHandleDown(e) {
  if (!editing.value || !selectedTarget.value) return
  e.preventDefault()
  rotateStart = { mx: e.clientX, rot: currentRot() }
  window.addEventListener('mousemove', onRotateMove)
  window.addEventListener('mouseup', onRotateUp)
}
function onRotateMove(e) {
  if (!rotateStart) return
  let deg = rotateStart.rot + (e.clientX - rotateStart.mx) * 0.8
  if (e.shiftKey) deg = Math.round(deg / 15) * 15  // 按住 Shift 吸附到 15°
  setSelectedRotation(deg)
}
function onRotateUp() {
  rotateStart = null
  window.removeEventListener('mousemove', onRotateMove)
  window.removeEventListener('mouseup', onRotateUp)
}

function ensureSlot(floor, row) {
  if (!layout.value[floor]) layout.value[floor] = {}
  if (!layout.value[floor][row]) layout.value[floor][row] = { x: 0, y: 0 }
}
function autoArrange() {
  const lay = { ...layout.value }
  floors.value.forEach(f => {
    const rs = buildRacks(f, null)
    lay[f] = {}; let x = 0
    rs.forEach(r => {
      const old = layout.value?.[f]?.[r.row] || {}
      lay[f][r.row] = { ...old, x, y: 0 }
      x += r.w + 90
    })
  })
  layout.value = lay
}
async function saveLayout() {
  saving.value = true
  try {
    layout.value._view = { phi: phi.value, yaw: yaw.value, scale: scale.value }
    const shelfLayout = JSON.stringify(layout.value)
    commitLocalShelfLayout(shelfLayout)
    editing.value = false
    await saveShelfLayout({ id: props.warehouseId, shelfLayout })
    await useWmsStore().getWarehouseList()
  } finally { saving.value = false }
}
function cancelEdit() { loadLayout(); editing.value = false }

function commitLocalShelfLayout(shelfLayout) {
  const store = useWmsStore()
  const sameId = (id) => String(id) === String(props.warehouseId)
  store.warehouseList = (store.warehouseList || []).map(w => sameId(w.id) ? { ...w, shelfLayout } : w)
  const map = new Map(store.warehouseMap || [])
  const key = [...map.keys()].find(sameId) ?? props.warehouseId
  map.set(key, { ...(map.get(key) || {}), id: key, shelfLayout })
  store.warehouseMap = map
}
onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onOrbit)
  window.removeEventListener('mouseup', onOrbitUp)
  window.removeEventListener('mousemove', onDrag)
  window.removeEventListener('mouseup', onUp)
  window.removeEventListener('mousemove', onResizeMove)
  window.removeEventListener('mouseup', onResizeUp)
  window.removeEventListener('mousemove', onRotateMove)
  window.removeEventListener('mouseup', onRotateUp)
  window.removeEventListener('mousemove', onPan)
  window.removeEventListener('mouseup', onPanUp)
})
</script>

<style scoped>
.shelf-map { width: 100%; }
.toolbar { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; flex-wrap: wrap; }
.toolbar .spacer { flex: 1; }
.toolbar .tip { font-size: 12px; color: #909399; }
.scale-panel {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: -2px 0 10px;
  padding: 8px 10px;
  background: #f7f9fc;
  border: 1px solid #dce5f1;
  border-radius: 6px;
}
.scale-title {
  color: #26364a;
  font-size: 13px;
  font-weight: 700;
}
.scale-panel label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #52627a;
  font-size: 12px;
}
.scale-panel :deep(.el-input-number--small) {
  width: 96px;
}

.scene {
  position: relative; width: 100%; overflow: hidden;
  background: radial-gradient(120% 120% at 50% 10%, #fbfdff, #e7edf5 80%);
  border: 1px solid #e4e7ed; border-radius: 8px;
  perspective: 1600px; perspective-origin: 50% 45%;
  cursor: grab; outline: none; user-select: none;
  box-shadow: inset 0 14px 40px rgba(82, 109, 145, .08);
}
.scene:focus-visible { box-shadow: 0 0 0 2px rgba(64, 158, 255, .2), inset 0 14px 40px rgba(82, 109, 145, .08); }
.scene.orbiting { cursor: move; }
.scene.panning { cursor: grabbing; }
.scene-svg {
  display: block;
  width: 100%;
  height: 100%;
}
.floor {
  fill: #e7edf5;
  stroke: #a9b8cc;
  stroke-width: 1.5;
}
.floor-line {
  stroke: rgba(95, 116, 141, .28);
  stroke-width: 1;
}
.svg-rack-face {
  stroke-width: 1.3;
  vector-effect: non-scaling-stroke;
  cursor: pointer;
  transition: filter .15s;
}
.svg-rack-face:hover {
  filter: brightness(1.05) drop-shadow(0 4px 5px rgba(37, 62, 92, .2));
}
.svg-rack-face.is-selected {
  stroke: #f59e0b;
  stroke-width: 2.4;
}
.svg-object-face {
  stroke-width: 1.2;
  vector-effect: non-scaling-stroke;
  cursor: pointer;
  transition: filter .15s;
}
.svg-object-face:hover {
  filter: brightness(1.08) drop-shadow(0 4px 5px rgba(37, 62, 92, .25));
}
.svg-object-face.is-selected {
  stroke: #f59e0b;
  stroke-width: 2.4;
}
.svg-slot-face {
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
  cursor: pointer;
  transition: filter .15s, opacity .15s;
}
.svg-slot-face:hover {
  filter: brightness(1.08) drop-shadow(0 3px 4px rgba(37, 62, 92, .28));
}
.svg-slot-face.is-highlight {
  stroke: #d93636;
  stroke-width: 1.8;
}
.svg-label text {
  fill: #1f2937;
  font-size: 16px;
  font-weight: 700;
  paint-order: stroke;
  stroke: rgba(255,255,255,.9);
  stroke-width: 4px;
  stroke-linejoin: round;
}
.svg-label .sub {
  fill: #64748b;
  font-size: 12px;
  font-weight: 600;
}
.cell-label {
  fill: #275d30;
  font-size: 11px;
  font-weight: 700;
  pointer-events: none;
  paint-order: stroke;
  stroke: rgba(244,255,244,.82);
  stroke-width: 3px;
  stroke-linejoin: round;
}
.cell-label.highlight {
  fill: #fff;
  stroke: rgba(180, 40, 40, .85);
}
.resize-handle {
  cursor: grab;
  pointer-events: auto;
}
.resize-handle:active {
  cursor: grabbing;
}
.resize-guide {
  stroke: rgba(245, 158, 11, .65);
  stroke-width: 1.4;
  stroke-dasharray: 4 3;
  vector-effect: non-scaling-stroke;
}
.resize-handle circle {
  fill: #fff7ed;
  stroke: #f59e0b;
  stroke-width: 2.2;
  filter: drop-shadow(0 3px 5px rgba(37, 62, 92, .28));
  vector-effect: non-scaling-stroke;
}
.resize-handle.handle-rot circle {
  fill: #eef4ff;
  stroke: #409eff;
}
.resize-handle.handle-rot text {
  fill: #1d6fd0;
}
.resize-handle.handle-rot .resize-guide {
  stroke: rgba(64, 158, 255, .6);
}
.resize-handle text {
  fill: #92400e;
  font-size: 11px;
  font-weight: 700;
  paint-order: stroke;
  stroke: rgba(255,255,255,.9);
  stroke-width: 3px;
  stroke-linejoin: round;
  pointer-events: none;
}
.world { position: absolute; transform-style: preserve-3d; transform-origin: 0 0; }
.ground {
  position: absolute; left: -1100px; top: -760px; width: 2600px; height: 1700px;
  transform-origin: 0 0;
  background-color: #eef3f8;
  background-image:
    linear-gradient(rgba(121, 146, 176, .2) 1px, transparent 1px),
    linear-gradient(90deg, rgba(121, 146, 176, .2) 1px, transparent 1px);
  background-size: 44px 44px;
  border: 1px solid rgba(121, 146, 176, .28);
  box-shadow: inset 0 0 80px rgba(99, 122, 151, .12);
  opacity: .92;
}
.rack { position: absolute; transform-style: preserve-3d; }
.rack.editing { cursor: grab; }
.rack.dragging { cursor: grabbing; }
.rack.editing:hover .face.front { outline: 2px dashed #409EFF; outline-offset: -2px; }
.rack-label {
  position: absolute;
  left: 4px;
  bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  white-space: nowrap;
  color: #26364a;
  text-shadow: 0 1px 2px #fff;
  transform-style: preserve-3d;
}
.rack-label strong {
  font-size: 15px;
  line-height: 1;
}
.rack-label span {
  font-size: 11px;
  color: #64748b;
}
.box {
  position: relative; transform-style: preserve-3d;
  filter: drop-shadow(20px 24px 18px rgba(49, 71, 101, .28));
}
.face {
  position: absolute; left: 0; top: 0; backface-visibility: hidden;
  box-sizing: border-box;
}
.face.front {
  background:
    linear-gradient(90deg, rgba(255,255,255,.55), transparent 18px, transparent calc(100% - 18px), rgba(74,100,132,.08)),
    #e8f0f9;
  border: 5px solid #7489a5;
  overflow: visible;
  box-shadow:
    inset 0 0 0 2px rgba(255,255,255,.45),
    inset 0 -18px 22px rgba(76,100,130,.1);
}
.face.back {
  background:
    repeating-linear-gradient(90deg, rgba(255,255,255,.13) 0 2px, transparent 2px 54px),
    linear-gradient(135deg, #a9bad1, #8399b5);
  border: 5px solid #7186a2;
}
.face.left, .face.right {
  background:
    repeating-linear-gradient(90deg, rgba(255,255,255,.12) 0 2px, transparent 2px 32px),
    linear-gradient(180deg, #b6c6dc, #8ea3bf 68%, #6f86a3);
  border: 5px solid #7186a2;
  overflow: hidden;
}
.face.left span, .face.right span {
  position: absolute; left: 0; right: 0;
  height: 5px;
  background: linear-gradient(180deg, rgba(255,255,255,.8), rgba(77,96,120,.45));
  transform: translateY(-2px);
}
.face.top {
  background:
    repeating-linear-gradient(90deg, rgba(255,255,255,.28) 0 2px, transparent 2px 57px),
    linear-gradient(135deg, #dbe6f4, #a9bfd9);
  border: 5px solid #7186a2;
  overflow: hidden;
}
.face.top span {
  position: absolute; top: 0; bottom: 0;
  width: 5px;
  background: linear-gradient(90deg, rgba(255,255,255,.7), rgba(79,100,126,.35));
  transform: translateX(-2px);
}
.face.bottom { background: #748aa7; }
.rack-frame {
  position: absolute;
  left: 0;
  top: 0;
  pointer-events: none;
  z-index: 1;
}
.shelf-board,
.upright {
  position: absolute;
  background: linear-gradient(180deg, #eef4fb, #647b98);
  box-shadow: 0 1px 2px rgba(36, 52, 74, .25);
}
.shelf-board {
  left: -1px;
  right: -1px;
  height: 5px;
  transform: translateY(-2px);
}
.upright {
  top: -1px;
  bottom: -1px;
  width: 5px;
  transform: translateX(-2px);
}
.column-axis,
.layer-axis {
  position: absolute;
  display: grid;
  z-index: 4;
  pointer-events: none;
}
.column-axis {
  left: 0;
  right: 0;
  top: -24px;
  height: 18px;
}
.column-axis span,
.layer-axis span {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #475569;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
  text-shadow: 0 1px 2px #fff;
}
.layer-axis {
  top: 0;
  bottom: 0;
  left: -35px;
  width: 30px;
}
.slots {
  position: relative;
  z-index: 2;
  box-sizing: border-box;
  transform-style: preserve-3d;
}
.slot-cube {
  position: absolute;
  transform-style: preserve-3d;
  cursor: pointer;
  transform: translateZ(calc(var(--slot-depth) * .35));
  transition: transform .16s ease;
}
.slot-cube:hover {
  transform: translateZ(calc(var(--slot-depth) * .78));
}
.slot-face {
  position: absolute;
  inset: 0;
  box-sizing: border-box;
  backface-visibility: visible;
}
.slot-front {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #256b2c;
  line-height: 1;
  background: linear-gradient(#f4fff4, #dff2e0);
  border: 1px solid #82c886;
  border-radius: 2px;
  box-shadow: inset 0 -4px 5px rgba(22, 101, 52, .08), 0 1px 0 rgba(255,255,255,.75);
  transform: translateZ(calc(var(--slot-depth) / 2));
}
.slot-front strong {
  font-size: 12px;
}
.slot-front span {
  font-size: 9px;
  color: #5f8f62;
}
.slot-top {
  height: var(--slot-depth);
  background: linear-gradient(135deg, #eaf8ea, #b7dfb9);
  border: 1px solid #7cbf80;
  transform: rotateX(90deg) translateZ(calc(var(--slot-depth) / 2));
  transform-origin: top;
}
.slot-bottom {
  height: var(--slot-depth);
  top: auto;
  bottom: 0;
  background: linear-gradient(135deg, #8bb98e, #63966a);
  border: 1px solid #5f9f65;
  transform: rotateX(-90deg) translateZ(calc(var(--slot-depth) / 2));
  transform-origin: bottom;
}
.slot-left,
.slot-right {
  width: var(--slot-depth);
  background: linear-gradient(180deg, #c7edc8, #85c58a);
  border: 1px solid #70b875;
}
.slot-left {
  right: auto;
  transform: rotateY(-90deg) translateZ(calc(var(--slot-depth) / 2));
  transform-origin: left;
}
.slot-right {
  left: auto;
  right: 0;
  transform: rotateY(90deg) translateZ(calc(var(--slot-depth) / 2));
  transform-origin: right;
}
.slot-back {
  background: linear-gradient(#d7ecd8, #aacfac);
  border: 1px solid #7fb583;
  transform: translateZ(calc(var(--slot-depth) / -2));
}
.slot-cube:hover .slot-front {
  background: #cfeed0;
  box-shadow: 0 0 0 2px rgba(64,158,255,.25), 0 6px 12px rgba(49, 71, 101, .2);
}
.slot-cube.highlight .slot-front {
  background: #f56c6c; color: #fff; border-color: #f23a3a; font-weight: bold;
  animation: blink 1s infinite alternate; z-index: 1;
}
.slot-cube.highlight .slot-top,
.slot-cube.highlight .slot-left,
.slot-cube.highlight .slot-right {
  background: linear-gradient(135deg, #ff9999, #df5555);
  border-color: #d94343;
}
.slot-cube.highlight .slot-front span { color: #fff; }
@keyframes blink {
  from { box-shadow: 0 0 5px 1px rgba(245,108,108,.55); }
  to   { box-shadow: 0 0 18px 6px rgba(245,108,108,.95); }
}
.hint { font-size: 12px; color: #909399; margin-top: 8px; }
.is-single .scene { border: none; background: transparent; }
</style>
