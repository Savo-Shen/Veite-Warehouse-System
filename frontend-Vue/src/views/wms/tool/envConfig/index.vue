<template>
  <div class="app-container">
    <el-card v-loading="loading">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span style="font-size: large">环境配置</span>
          <span style="font-size: 13px; color: #909399">
            配置保存在服务器参数表（sys_config）中，修改后无需重新打包前端，刷新页面即可生效
          </span>
        </div>
      </template>

      <!-- 高德地图 -->
      <div class="config-group">
        <div class="group-title">
          <svg-icon icon-class="international" class="group-icon" />
          高德地图
        </div>
        <el-alert type="info" :closable="false" class="group-intro">
          <p>用于「往来单位」的<b>地图选点</b>和<b>单位分布图</b>。按以下步骤获取 Key：</p>
          <ol>
            <li>打开 <a href="https://console.amap.com/dev/key/app" target="_blank">高德开放平台控制台</a>，注册并完成个人开发者认证（免费）；</li>
            <li>「应用管理 → 我的应用」中创建一个应用；</li>
            <li>在该应用下「添加 Key」，服务平台务必选择 <b>「Web端(JS API)」</b>（选成"Web服务"等其他类型将无法使用）；</li>
            <li>创建后会得到一个 <b>Key</b> 和一个配套的<b>安全密钥（jscode）</b>，分别填入下方两栏并保存。</li>
          </ol>
          <p style="color: #E6A23C">安全建议：在高德控制台为该 Key 绑定「域名白名单」，防止 Key 被他人盗用产生配额消耗。</p>
        </el-alert>

        <el-form label-width="110px" style="max-width: 720px">
          <el-form-item label="JS API Key">
            <el-input v-model="form.key" placeholder="例如：0123456789abcdef0123456789abcdef" clearable />
            <div class="field-desc">高德「Web端(JS API)」类型的 Key，地图能否显示取决于它。留空表示未启用地图功能。</div>
          </el-form-item>
          <el-form-item label="安全密钥">
            <el-input v-model="form.securityCode" placeholder="创建 Key 时生成的 jscode" show-password clearable />
            <div class="field-desc">与上方 Key 配套的安全密钥（jscode）。2021 年 12 月后申请的 Key 必须同时配置，否则搜索、逆地理编码等功能会报错。</div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" v-hasPermi="['system:config:edit']" @click="save()">保 存</el-button>
            <el-button :loading="testing" v-hasPermi="['system:config:edit']" @click="saveAndTest">保存并测试地图</el-button>
          </el-form-item>
        </el-form>

        <!-- 测试结果 -->
        <el-alert v-if="testMessage" :type="testResult" :closable="false" style="margin-bottom: 10px; max-width: 720px">
          {{ testMessage }}
        </el-alert>
        <div v-show="showPreview" ref="previewRef" class="preview-map"></div>

        <div class="field-desc" style="margin-top: 12px">
          说明：构建时环境变量 VITE_AMAP_KEY / VITE_AMAP_SECURITY_CODE（frontend-Vue/.env.local）仍可作为兜底，
          但以本页配置优先；两处都为空时，地图相关页面会显示配置指引而不会报错。
        </div>
      </div>

      <!-- 库存 -->
      <div class="config-group" style="margin-top: 28px">
        <div class="group-title">
          <svg-icon icon-class="shopping" class="group-icon" />
          库存
        </div>
        <el-alert type="info" :closable="false" class="group-intro">
          <p>用于「货已经发出去了，但这个规格还没盘过库」的情况。</p>
          <ul>
            <li>打开后：出库遇到库存不够，会先弹出提示列出差多少，操作员确认「仍然出库」才会把账面扣成负数；</li>
            <li>关闭后：库存不够一律拦下，必须先盘库或入库才能出库（系统原本的行为）；</li>
            <li>无论开关如何，<b>移库都不允许负库存</b>——源库位没货就是选错了；</li>
            <li>产生的负库存会在「库存统计」页顶部提示条数，勾选「只看负库存」可以列出来，盘点后即可归零。</li>
          </ul>
        </el-alert>

        <el-form label-width="110px" style="max-width: 720px">
          <el-form-item label="允许负库存">
            <el-switch v-model="inventoryForm.allowNegative" active-text="允许" inactive-text="禁止" inline-prompt />
            <div class="field-desc">
              当前：{{ inventoryForm.allowNegative ? '出库时库存不足可二次确认后扣成负数' : '出库时库存不足直接拦截' }}
            </div>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingInventory" v-hasPermi="['system:config:edit']" @click="saveInventory">保 存</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup name="EnvConfig">
import { ref, reactive, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { getConfigKey, updateConfigByKey } from '@/api/system/config'
import {
  AMAP_KEY_CONFIG,
  AMAP_SECURITY_CONFIG,
  loadAMap,
  resetAMapConfigCache,
  getLoadedKey
} from '@/utils/amap'

const loading = ref(true)
const saving = ref(false)
const testing = ref(false)
const showPreview = ref(false)
const previewRef = ref(null)
const testResult = ref('success')
const testMessage = ref('')
const form = reactive({ key: '', securityCode: '' })
const inventoryForm = reactive({ allowNegative: false })
const savingInventory = ref(false)
const ALLOW_NEGATIVE_CONFIG = 'wms.inventory.allowNegative'

let previewMap = null

async function loadValues() {
  loading.value = true
  try {
    const [keyRes, codeRes, negativeRes] = await Promise.all([
      getConfigKey(AMAP_KEY_CONFIG),
      getConfigKey(AMAP_SECURITY_CONFIG),
      getConfigKey(ALLOW_NEGATIVE_CONFIG)
    ])
    form.key = (keyRes.msg || '').trim()
    form.securityCode = (codeRes.msg || '').trim()
    inventoryForm.allowNegative = (negativeRes.msg || '').trim() === 'true'
  } finally {
    loading.value = false
  }
}
loadValues()

async function save(silent = false) {
  saving.value = true
  try {
    await updateConfigByKey(AMAP_KEY_CONFIG, form.key.trim())
    await updateConfigByKey(AMAP_SECURITY_CONFIG, form.securityCode.trim())
    resetAMapConfigCache()
    if (!silent) {
      ElMessage.success('保存成功' + (needRefresh() ? '，本页已加载过旧 Key 的地图，刷新页面后新 Key 生效' : ''))
    }
    return true
  } catch (e) {
    return false
  } finally {
    saving.value = false
  }
}

async function saveInventory() {
  savingInventory.value = true
  try {
    await updateConfigByKey(ALLOW_NEGATIVE_CONFIG, String(inventoryForm.allowNegative))
    ElMessage.success(inventoryForm.allowNegative
      ? '已允许负库存出库，出库页刷新后生效'
      : '已禁止负库存出库，出库页刷新后生效')
  } finally {
    savingInventory.value = false
  }
}

function needRefresh() {
  const loaded = getLoadedKey()
  return !!loaded && loaded !== form.key.trim()
}

async function saveAndTest() {
  const ok = await save(true)
  if (!ok) return
  if (!form.key.trim()) {
    testResult.value = 'warning'
    testMessage.value = '已保存，但 Key 为空，无法测试。'
    showPreview.value = false
    return
  }
  if (needRefresh()) {
    testResult.value = 'warning'
    testMessage.value = '已保存。当前页面已用旧 Key 加载过地图 SDK，浏览器限制无法热替换，请刷新页面后再测试。'
    return
  }
  testing.value = true
  testMessage.value = ''
  try {
    const AMap = await loadAMap()
    showPreview.value = true
    await nextTick()
    if (!previewMap) {
      previewMap = new AMap.Map(previewRef.value, { zoom: 11, viewMode: '2D' })
      previewMap.addControl(new AMap.Scale())
    }
    testResult.value = 'success'
    testMessage.value = '保存成功，地图加载正常！下方是实时预览。如果地图瓦片能正常显示，说明 Key 有效。'
  } catch (e) {
    showPreview.value = false
    testResult.value = 'error'
    testMessage.value = '已保存，但地图加载失败：' + (e.message || e) + '。请检查 Key 类型是否为「Web端(JS API)」、是否配置了正确的安全密钥。'
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.config-group {
  margin-bottom: 10px;
}
.group-title {
  font-size: 15px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.group-icon {
  color: #409eff;
}
.group-intro {
  margin-bottom: 18px;
  max-width: 720px;
  line-height: 1.8;
}
.group-intro ol {
  margin: 4px 0;
  padding-left: 20px;
}
.field-desc {
  width: 100%;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 2px;
}
.preview-map {
  width: 100%;
  max-width: 720px;
  height: 320px;
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #dcdfe6;
}
</style>
