<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="68px">
        <el-form-item label="编号" prop="merchantCode">
          <el-input
            v-model="queryParams.merchantCode"
            placeholder="请输入编号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="名称" prop="merchantName">
          <el-input
            v-model="queryParams.merchantName"
            placeholder="请输入名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="企业类型" label-width="70px" prop="merchantType">
          <el-select v-model="queryParams.merchantType" placeholder="请选择企业类型" clearable>
            <el-option
              v-for="dict in merchant_type"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="mt20">

      <el-row :gutter="10" class="mb8" type="flex" justify="space-between">
        <el-col :span="6"><span style="font-size: large">往来单位</span></el-col>
        <el-col :span="12" style="display: flex; justify-content: flex-end; gap: 10px">
          <el-radio-group v-model="viewMode">
            <el-radio-button label="list">列表</el-radio-button>
            <el-radio-button label="map">地图</el-radio-button>
          </el-radio-group>
          <el-button
            type="primary"
            plain
            icon="Plus"
            @click="handleAdd"
            v-hasPermi="['wms:merchant:edit']"
          >新增</el-button>
        </el-col>
      </el-row>

      <el-table v-show="viewMode === 'list'" v-loading="loading" :data="merchantList" border stripe class="mt20" empty-text="暂无往来单位">
        <el-table-column label="id" prop="id" v-if="false"/>
        <el-table-column label="编号" prop="merchantCode" />
        <el-table-column label="名称" prop="merchantName" />
        <el-table-column label="企业类型" prop="merchantType">
          <template #default="scope">
            <dict-tag :options="merchant_type" :value="scope.row.merchantType"/>
          </template>
        </el-table-column>
        <el-table-column label="级别" prop="merchantLevel" />
        <el-table-column label="地址" prop="address">
          <template #default="scope">
            <el-popover
              v-if="scope.row.longitude && scope.row.latitude"
              placement="right"
              :width="290"
              trigger="hover"
              @show="loadRowImages(scope.row)"
            >
              <template #reference>
                <span class="addr-link" title="点击在地图总览中定位" @click="gotoMap(scope.row)">
                  <el-icon color="#67C23A" style="vertical-align: -2px; margin-right: 2px"><LocationFilled /></el-icon>{{ scope.row.address }}
                </span>
              </template>
              <div class="addr-preview">
                <div class="name">{{ scope.row.merchantName }}</div>
                <div class="line">{{ scope.row.address }}</div>
                <div class="line coords">经纬度：{{ scope.row.longitude }}, {{ scope.row.latitude }}</div>
                <div v-if="(rowImages[scope.row.id] || []).length" class="imgs">
                  <el-image
                    v-for="(url, i) in rowImages[scope.row.id]"
                    :key="url"
                    :src="url"
                    :preview-src-list="rowImages[scope.row.id]"
                    :initial-index="i"
                    preview-teleported
                    fit="cover"
                    class="thumb"
                  />
                </div>
                <div v-else-if="scope.row.imageIds && rowImages[scope.row.id] === undefined" class="line muted">图片加载中…</div>
                <div v-else class="line muted">暂无图片</div>
                <div class="line hint">点击地址可跳转「地图总览」并定位</div>
              </div>
            </el-popover>
            <span v-else>{{ scope.row.address }}</span>
          </template>
        </el-table-column>
        <el-table-column label="联系人" prop="contactPerson" />
        <el-table-column label="备注" prop="remark" />
        <el-table-column label="操作" align="right" class-name="small-padding fixed-width">
            <template #default="scope">
                <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wms:merchant:edit']">修改</el-button>
                <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wms:merchant:remove']">删除</el-button>
            </template>
        </el-table-column>
      </el-table>

      <el-row>
        <pagination
          v-show="viewMode === 'list' && total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </el-row>

      <!-- 地图模式：来往单位分布 -->
      <div v-show="viewMode === 'map'" class="merchant-map-wrap mt20">
        <template v-if="amapConfigured">
          <div ref="merchantMapRef" class="merchant-map" v-loading="mapLoading"></div>
          <div class="map-legend">
            <span class="legend-item"><i class="dot" style="background: #409EFF"></i>客户</span>
            <span class="legend-item"><i class="dot" style="background: #67C23A"></i>供应商</span>
            <span class="legend-item"><i class="dot" style="background: #E6A23C"></i>物流单位</span>
          </div>
          <div class="map-tip" v-if="mapStats.total > 0">
            共 {{ mapStats.total }} 家单位，已标记 {{ mapStats.located }} 家<template v-if="mapStats.total - mapStats.located > 0">，其余 {{ mapStats.total - mapStats.located }} 家可在编辑中通过「地图选点」补充位置</template>
          </div>
        </template>
        <el-empty v-else description="未配置高德地图 Key">
          <div style="color: #909399; font-size: 13px; line-height: 1.8">
            请管理员前往「基础资料 → <router-link :to="ENV_CONFIG_ROUTE" style="color: #409EFF">环境配置</router-link>」
            填写高德地图 Key（页面内有详细申请步骤），保存后回到本页即可使用。
          </div>
        </el-empty>
      </div>

    </el-card>
    <!-- 添加或修改往来单位对话框 -->
    <el-drawer :title="title" v-model="open" append-to-body size="50%">
      <el-form ref="merchantRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="企业类型" prop="merchantType">
          <el-select v-model="form.merchantType" placeholder="请选择企业类型">
            <el-option
              v-for="dict in merchant_type"
              :key="dict.value"
              :label="dict.label"
              :value="parseInt(dict.value)"
            ></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="编号" prop="merchantCode">
          <el-input
            v-model="form.merchantCode"
            :placeholder="form.merchantType ? '请输入编号' : '选择企业类型后自动生成，也可手动填写'"
            @input="codeTouched = true"
          >
            <template #append>
              <el-button icon="Refresh" :loading="codeLoading" @click="handleGenerateCode">自动生成</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="名称" prop="merchantName">
          <el-input v-model="form.merchantName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="级别" prop="merchantLevel">
          <el-input v-model="form.merchantLevel" placeholder="请输入级别" />
        </el-form-item>
        <el-form-item label="开户行" prop="bankName">
          <el-input v-model="form.bankName" placeholder="请输入开户行" />
        </el-form-item>
        <el-form-item label="银行账户" prop="bankAccount">
          <el-input v-model="form.bankAccount" placeholder="请输入银行账户" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入地址或通过地图选点获取">
            <template #append>
              <el-button icon="Location" @click="pickerVisible = true">地图选点</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="经纬度">
          <el-input
            :model-value="form.longitude && form.latitude ? form.longitude + ', ' + form.latitude : ''"
            readonly
            placeholder="通过「地图选点」获取，用于在地图上标记该单位"
          >
            <template #append>
              <el-button icon="Delete" :disabled="!form.longitude" @click="clearLocation">清除</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="单位图片" prop="imageIds">
          <image-upload v-model="form.imageIds" :limit="5" />
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="form.mobile" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="座机号" prop="tel">
          <el-input v-model="form.tel" placeholder="请输入座机号" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="form.contactPerson" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="Email" prop="email">
          <el-input v-model="form.email" placeholder="请输入Email" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 地图选点 -->
    <a-map-picker
      v-model="pickerVisible"
      :longitude="form.longitude"
      :latitude="form.latitude"
      :address="form.address"
      @confirm="handlePickLocation"
    />
  </div>
</template>

<script setup name="Merchant">
import { listMerchant, listMerchantNoPage, getMerchant, delMerchant, addMerchant, updateMerchant, getNextMerchantCode } from "@/api/wms/merchant";
import {ElMessageBox, ElMessage} from "element-plus";
import { getCurrentInstance, reactive, ref, toRefs, watch, nextTick, onMounted, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import AMapPicker from "@/components/AMapPicker";
import { loadAMap, checkAMapConfigured, ENV_CONFIG_ROUTE, MAP_OVERVIEW_ROUTE, MERCHANT_TYPE_COLORS, MERCHANT_TYPE_DEFAULT_COLOR } from "@/utils/amap";
import { fetchOssUrls, openMerchantInfoWindow } from "@/utils/mapMarkerInfo";

const { proxy } = getCurrentInstance();
const { merchant_type } = proxy.useDict('merchant_type');
const router = useRouter();

const merchantList = ref([]);
const open = ref(false);
const buttonLoading = ref(false);
const loading = ref(true);
const ids = ref([]);
const total = ref(0);
const title = ref("");
const pickerVisible = ref(false);
// 编号是否被手动改过：改过就不再自动覆盖
const codeTouched = ref(false);
const codeLoading = ref(false);

// 地图模式
const viewMode = ref('list');
const amapConfigured = ref(true);
const merchantMapRef = ref(null);
const mapLoading = ref(false);
const mapStats = reactive({ total: 0, located: 0 });
let merchantMap = null;
let mapMarkers = [];
let mapInfoWindow = null;

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    merchantCode: undefined,
    merchantName: undefined,
    merchantType: undefined,
  },
  rules: {
    merchantCode: [
      { required: true, message: "编号不能为空", trigger: "blur" }
    ],
    merchantName: [
      { required: true, message: "名称不能为空", trigger: "blur" }
    ],
    merchantType: [
      { required: true, message: "企业类型不能为空", trigger: "change" }
    ],
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询往来单位列表 */
function getList() {
  loading.value = true;
  listMerchant(queryParams.value).then(response => {
    merchantList.value = response.rows;
    total.value = response.total;
    loading.value = false;
  });
}

// 取消按钮
function cancel() {
  open.value = false;
  reset();
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    merchantCode: null,
    merchantName: null,
    merchantType: null,
    merchantLevel: null,
    bankName: null,
    bankAccount: null,
    address: null,
    longitude: null,
    latitude: null,
    mobile: null,
    tel: null,
    contactPerson: null,
    email: null,
    remark: null,
    imageIds: null,
    delFlag: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  proxy.resetForm("merchantRef");
  codeTouched.value = false;
}

/** 取下一个可用编号填入（新增时选完企业类型自动调用，也可手动点「自动生成」） */
async function fillNextCode(merchantType) {
  if (merchantType == null) return;
  codeLoading.value = true;
  try {
    const res = await getNextMerchantCode(merchantType);
    form.value.merchantCode = res.data;
  } finally {
    codeLoading.value = false;
  }
}

/** 点击「自动生成」：重新按当前企业类型取号 */
function handleGenerateCode() {
  if (form.value.merchantType == null) {
    proxy.$modal.msgWarning("请先选择企业类型");
    return;
  }
  codeTouched.value = false;
  fillNextCode(form.value.merchantType);
}

// 新增时切换企业类型自动带出该类型的下一个编号（用户手动改过则不覆盖）
watch(() => form.value.merchantType, (type) => {
  if (form.value.id != null || codeTouched.value) return;
  fillNextCode(type);
});

/** 地图选点确认 */
function handlePickLocation({ longitude, latitude, address }) {
  form.value.longitude = longitude;
  form.value.latitude = latitude;
  if (address) {
    form.value.address = address;
  }
}

/** 清除经纬度 */
function clearLocation() {
  form.value.longitude = null;
  form.value.latitude = null;
}

/** ------- 地址列 hover 预览 / 跳转 ------- */
// { [merchantId]: 图片URL数组 }，undefined 表示尚未加载
const rowImages = reactive({});
const rowImagesLoading = new Set();

function loadRowImages(row) {
  if (!row.imageIds || rowImages[row.id] !== undefined || rowImagesLoading.has(row.id)) return;
  rowImagesLoading.add(row.id);
  fetchOssUrls(row.imageIds)
    .then(urls => { rowImages[row.id] = urls; })
    .finally(() => rowImagesLoading.delete(row.id));
}

/** 跳转地图总览并定位到该单位 */
function gotoMap(row) {
  router.push({ path: MAP_OVERVIEW_ROUTE, query: { merchantId: row.id } });
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef");
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  reset();
  open.value = true;
  title.value = "添加往来单位";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _id = row.id || ids.value
  getMerchant(_id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改往来单位";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["merchantRef"].validate(valid => {
    if (valid) {
      buttonLoading.value = true;
      if (form.value.id != null) {
        updateMerchant(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
          refreshMerchantMarkers();
        }).finally(() => {
          buttonLoading.value = false;
        });
      } else {
        addMerchant(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
          refreshMerchantMarkers();
        }).finally(() => {
          buttonLoading.value = false;
        });
      }
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value;
  proxy.$modal.confirm('确认删除往来单位【' + row.merchantName + '】吗？').then(function() {
    return delMerchant(_ids);
  }).then((res) => {
    loading.value = true;
    getList();
    refreshMerchantMarkers();
    proxy.$modal.msgSuccess("删除成功");
  }).finally(() => {
    loading.value = false;
  });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('wms/merchant/export', {
    ...queryParams.value
  }, `merchant_${new Date().getTime()}.xlsx`)
}

/** ------- 地图模式 ------- */
watch(viewMode, async (mode) => {
  if (mode !== 'map') return;
  amapConfigured.value = await checkAMapConfigured();
  if (amapConfigured.value) {
    nextTick(() => initMerchantMap());
  }
});

async function initMerchantMap() {
  mapLoading.value = true;
  try {
    const AMap = await loadAMap();
    if (!merchantMap) {
      merchantMap = new AMap.Map(merchantMapRef.value, {
        zoom: 5,
        viewMode: '2D'
      });
      merchantMap.addControl(new AMap.ToolBar());
      merchantMap.addControl(new AMap.Scale());
      mapInfoWindow = new AMap.InfoWindow({ offset: new AMap.Pixel(0, -14) });
    }
    await refreshMerchantMarkers();
  } catch (e) {
    ElMessage.error(e.message || '地图加载失败');
  } finally {
    mapLoading.value = false;
  }
}

async function refreshMerchantMarkers() {
  if (!merchantMap) return;
  const res = await listMerchantNoPage({});
  const list = res.data || [];
  mapStats.total = list.length;
  const located = list.filter(m => m.longitude != null && m.latitude != null);
  mapStats.located = located.length;

  merchantMap.remove(mapMarkers);
  mapMarkers = [];
  const AMap = window.AMap;
  located.forEach(m => {
    const color = MERCHANT_TYPE_COLORS[m.merchantType] || MERCHANT_TYPE_DEFAULT_COLOR;
    const marker = new AMap.Marker({
      position: [Number(m.longitude), Number(m.latitude)],
      title: m.merchantName,
      anchor: 'center',
      content: `<div style="width:16px;height:16px;border-radius:50%;background:${color};border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4);cursor:pointer"></div>`
    });
    marker.on('click', () => {
      openMerchantInfoWindow(mapInfoWindow, merchantMap, marker.getPosition(), m, color, typeLabel(m.merchantType));
    });
    mapMarkers.push(marker);
  });
  if (mapMarkers.length) {
    merchantMap.add(mapMarkers);
    merchantMap.setFitView(mapMarkers, false, [60, 60, 60, 60]);
  }
}

function typeLabel(type) {
  const dict = (merchant_type.value || []).find(d => String(d.value) === String(type));
  return dict ? dict.label : '未知类型';
}

// 键盘事件处理
const handleKeydown = (e) => {
  if (e.key === 'ArrowLeft') {
    // 上一页
    if (queryParams.value.pageNum > 1) {
      queryParams.value.pageNum--
      getList()
    }
  } else if (e.key === 'ArrowRight') {
    // 下一页
    const maxPage = Math.ceil(total.value / queryParams.value.pageSize)
    if (queryParams.value.pageNum < maxPage) {
      queryParams.value.pageNum++
      getList()
    }
  }
}

onMounted(() => {
  getList();
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (merchantMap) {
    merchantMap.destroy()
    merchantMap = null
    mapMarkers = []
    mapInfoWindow = null
  }
})
</script>

<style scoped>
.merchant-map-wrap {
  position: relative;
}
.merchant-map {
  width: 100%;
  height: 560px;
  border-radius: 4px;
  overflow: hidden;
}
.map-legend {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 10;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  padding: 8px 12px;
  display: flex;
  gap: 14px;
  font-size: 13px;
  color: #303133;
}
.map-legend .legend-item {
  display: flex;
  align-items: center;
  gap: 5px;
}
.map-legend .dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.map-tip {
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
}
.addr-link {
  cursor: pointer;
  color: #409eff;
}
.addr-link:hover {
  text-decoration: underline;
}
.addr-preview .name {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 4px;
}
.addr-preview .line {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.addr-preview .coords {
  color: #909399;
  font-size: 12px;
}
.addr-preview .muted {
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 6px;
}
.addr-preview .hint {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid #ebeef5;
  color: #909399;
  font-size: 12px;
}
.addr-preview .imgs {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.addr-preview .thumb {
  width: 72px;
  height: 72px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  cursor: zoom-in;
}
</style>
