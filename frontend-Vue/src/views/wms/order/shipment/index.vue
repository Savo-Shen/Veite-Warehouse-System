<template>
  <div class="app-container">
    <el-card class="desktop-only">
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="70px" @submit.prevent @keyup.enter="handleQuery">
        <div style="display: flex;">
          <el-form-item label="综合搜索" style="flex: 1;">
            <el-input v-model="queryParams.keyword" clearable placeholder="搜索出库单号 / 业务单号 / 备注 / 客户 / 仓库 / 商品·规格·条码（多个关键字用空格分隔）"/>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            <el-button type="text" @click="advancedSearchVisible = !advancedSearchVisible">高级搜索</el-button>
          </el-form-item>
        </div>
        <el-collapse-transition>
          <div v-if="advancedSearchVisible">
            <el-form-item label="出库状态" prop="orderStatus">
              <el-radio-group v-model="queryParams.orderStatus" @change="handleQuery">
                <el-radio-button
                  :key="-2"
                  :label="-2"
                >
                  全部
                </el-radio-button>
                <el-radio-button
                  v-for="item in wms_shipment_status"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="出库类型" prop="optType">
              <el-radio-group v-model="queryParams.optType" @change="handleQuery">
                <el-radio-button
                  :key="-1"
                  :label="-1"
                >
                  全部
                </el-radio-button>
                <el-radio-button
                  v-for="item in wms_shipment_type"
                  :key="item.value"
                  :label="item.value"
                >
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="出库单号" prop="orderNo">
              <el-input
                v-model="queryParams.orderNo"
                placeholder="请输入出库单号"
                clearable
                @keyup.enter="handleQuery"
              />
            </el-form-item>
            <el-form-item label="业务单号" prop="bizOrderNo">
              <el-input
                v-model="queryParams.bizOrderNo"
                placeholder="请输入业务单号"
                clearable
                @keyup.enter="handleQuery"
              />
            </el-form-item>
            <el-form-item label="客户" prop="merchantId">
              <el-select v-model="queryParams.merchantId" placeholder="请选择客户" clearable filterable
                         style="width: 200px" @change="handleQuery">
                <el-option v-for="item in useWmsStore().merchantList.filter(m => m.merchantType != 2)"
                           :key="item.id" :label="item.merchantName" :value="item.id"/>
              </el-select>
            </el-form-item>
            <el-form-item label="仓库" prop="warehouseId">
              <el-select v-model="queryParams.warehouseId" placeholder="请选择仓库" clearable filterable
                         style="width: 200px" @change="handleQuery">
                <el-option v-for="item in useWmsStore().warehouseList"
                           :key="item.id" :label="item.warehouseName" :value="item.id"/>
              </el-select>
            </el-form-item>
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="queryParams.remark"
                placeholder="请输入备注关键字"
                clearable
                @keyup.enter="handleQuery"
              />
            </el-form-item>
            <el-form-item label="创建时间">
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                value-format="YYYY-MM-DD HH:mm:ss"
                range-separator="-"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                :default-time="[new Date(2000, 1, 1, 0, 0, 0), new Date(2000, 1, 1, 23, 59, 59)]"
                @change="handleQuery"
              />
            </el-form-item>
          </div>
        </el-collapse-transition>
      </el-form>
    </el-card>

    <el-card class="mobile-only mobile-search-card">
      <div class="mobile-search-bar">
        <el-input
          v-model="queryParams.keyword"
          clearable
          size="large"
          placeholder="搜索出库单号或业务单号"
          @keyup.enter="handleQuery"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" size="large" @click="handleQuery">搜索</el-button>
      </div>
      <div class="mobile-filter-row">
        <el-select v-model="queryParams.orderStatus" @change="handleQuery">
          <el-option label="全部状态" :value="-2" />
          <el-option v-for="item in wms_shipment_status" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="queryParams.optType" @change="handleQuery">
          <el-option label="全部类型" :value="-1" />
          <el-option v-for="item in wms_shipment_type" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>
    </el-card>

    <el-card class="mt20">

      <el-row :gutter="10" class="mb8" type="flex" justify="space-between">
        <el-col :span="6"><span style="font-size: large">出库单</span></el-col>
        <el-col :span="1.5">
          <el-button
            type="primary"
            plain
            icon="Plus"
            @click="handleAdd"
            v-hasPermi="['wms:shipment:all']"
          >新增</el-button>
        </el-col>
      </el-row>
      <el-table v-loading="loading" :data="shipmentOrderList" border stripe class="mt20 desktop-only"
                @expand-change="handleExpandExchange"
                :row-key="getRowKey"
                :expand-row-keys="expandedRowKeys"
                empty-text="暂无出库单"
                cell-class-name="vertical-top-cell"
      >
        <el-table-column type="expand">
          <template #default="props">
            <div style="padding: 0 50px 20px 50px">
              <h3>商品明细</h3>
              <el-table :data="props.row.details" v-loading="detailLoading[props.$index]" empty-text="暂无商品明细">
                <el-table-column label="商品名称">
                  <template #default="{ row }">
                    <div>{{ row?.item?.itemName }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="规格名称">
                  <template #default="{ row }">
                    <div>{{ row?.itemSku?.skuName }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="数量" prop="quantity" align="right">
                  <template #default="{ row }">
                    <el-statistic :value="Number(row.quantity)" :precision="0"/>
                  </template>
                </el-table-column>
                <el-table-column label="金额(元)" align="right">
                  <template #default="{ row }">
                    <el-statistic v-if="row.amount || row.amount === 0" :precision="3" :value="Number(row.amount)"/>
                    <div v-else>-</div>
                  </template>
                </el-table-column>
              </el-table>
              <h3>补充图片</h3>
              <order-image-gallery :image-ids="props.row.supplementImageIds" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="单号/业务单号" align="left" min-width="120">
          <template #default="{ row }">
            <div>单号：{{ row.orderNo }}</div>
            <div v-if="row.bizOrderNo">业务单号：{{ row.bizOrderNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="仓库" align="left">
          <template #default="{ row }">
            <div>{{ useWmsStore().warehouseMap.get(row.warehouseId)?.warehouseName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="总数量/总金额(元)" align="left" min-width="100">
          <template #default="{ row }">
            <div class="flex-space-between">
              <span>数量：</span>
              <el-statistic :value="Number(row.totalQuantity)" :precision="0"/>
            </div>
            <div class="flex-space-between" v-if="row.totalAmount || row.totalAmount === 0">
              <span>金额：</span>
              <el-statistic :value="Number(row.totalAmount)" :precision="3"/>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="出库状态" align="center" prop="orderStatus" width="80">
          <template #default="{ row }">
            <dict-tag :options="wms_shipment_status" :value="row.orderStatus" />
          </template>
        </el-table-column>
        <el-table-column label="出库类型" align="center" prop="optType" width="100">
          <template #default="{ row }">
            <dict-tag :options="wms_shipment_type" :value="row.optType" />
          </template>
        </el-table-column>
        <el-table-column label="客户" align="left" prop="merchantId">
          <template #default="{ row }">
            <div>{{ useWmsStore().merchantMap.get(row.merchantId)?.merchantName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" align="left" width="150">
          <template #default="{ row }">
            <div>创建：{{ parseTime(row.createTime, '{mm}-{dd} {hh}:{ii}') }}</div>
            <div>更新：{{ parseTime(row.updateTime, '{mm}-{dd} {hh}:{ii}') }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作人" align="left">
          <template #default="{ row }">
            <div>{{ row.createBy }}</div>
            <div v-if="row.updateBy">{{ row.updateBy }}</div>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" />
        <el-table-column label="操作" align="right" class-name="small-padding fixed-width" width="120">
          <template #default="scope">
            <div>
              <el-popover
                placement="left"
                title="提示"
                :width="300"
                trigger="hover"
                :disabled="scope.row.orderStatus === 0"
                :content="'出库单【' + scope.row.orderNo + '】已' + (scope.row.orderStatus === 1 ? '出库' : '作废') + '，无法修改！' "
              >
                <template #reference>
                  <el-button link type="primary" @click="handleUpdate(scope.row)" v-hasPermi="['wms:shipment:all']" :disabled="[-1, 1].includes(scope.row.orderStatus)">修改</el-button>
                </template>
              </el-popover>
              <el-button link type="primary" @click="handleGoDetail(scope.row)" v-hasPermi="['wms:shipment:all']">{{ expandedRowKeys.includes(scope.row.id) ? '收起' : '查看' }}</el-button>
            </div>
            <div class="mt10">
              <el-popover
                placement="left"
                title="提示"
                :width="300"
                trigger="hover"
                :disabled="[-1, 0].includes(scope.row.orderStatus)"
                :content="'出库单【' + scope.row.orderNo + '】已出库，无法删除！' "
              >
                <template #reference>
                  <el-button link type="danger" @click="handleDelete(scope.row)" v-hasPermi="['wms:shipment:all']" :disabled="scope.row.orderStatus === 1">删除</el-button>
                </template>
              </el-popover>
              <el-button
                link
                type="primary"
                v-hasPermi="['wms:shipment:all']"
                :loading="printingId === scope.row.id"
                @click="handlePrint(scope.row)"
              >打印</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div v-loading="loading" class="mobile-only mobile-order-list">
        <div v-for="row in shipmentOrderList" :key="row.id" class="mobile-order-card">
          <div class="mobile-order-card__header">
            <span>{{ row.orderNo }}</span>
            <dict-tag :options="wms_shipment_status" :value="row.orderStatus" />
          </div>
          <div v-if="row.bizOrderNo" class="mobile-order-card__row"><span>业务单号</span><strong>{{ row.bizOrderNo }}</strong></div>
          <div class="mobile-order-card__row"><span>仓库</span><span>{{ useWmsStore().warehouseMap.get(row.warehouseId)?.warehouseName || '-' }}</span></div>
          <div class="mobile-order-card__row"><span>客户</span><span>{{ useWmsStore().merchantMap.get(row.merchantId)?.merchantName || '-' }}</span></div>
          <div class="mobile-order-card__row"><span>数量 / 金额</span><span>{{ Number(row.totalQuantity || 0).toFixed(0) }} / {{ row.totalAmount ?? '-' }}</span></div>
          <div class="mobile-order-card__row"><span>创建时间</span><span>{{ parseTime(row.createTime, '{mm}-{dd} {hh}:{ii}') }}</span></div>
          <div class="mobile-order-card__actions">
            <el-button @click="handleGoDetail(row)">{{ expandedRowKeys.includes(row.id) ? '收起' : '查看' }}</el-button>
            <el-button type="primary" :disabled="[-1, 1].includes(row.orderStatus)" @click="handleUpdate(row)">继续出库</el-button>
          </div>
          <div v-if="expandedRowKeys.includes(row.id)" class="mobile-detail-panel">
            <div v-for="detail in row.details || []" :key="detail.id" class="mobile-detail-item">
              <strong>{{ detail.item?.itemName }} / {{ detail.itemSku?.skuName }}</strong>
              <div class="mobile-order-card__row"><span>数量</span><span>{{ Number(detail.quantity || 0).toFixed(0) }}</span></div>
            </div>
            <order-image-gallery :image-ids="row.supplementImageIds" />
          </div>
        </div>
        <el-empty v-if="!loading && !shipmentOrderList.length" description="没有找到出库单" />
      </div>

      <el-row>
        <pagination
          v-show="total>0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </el-row>
    </el-card>

    <!-- 打印前手动校对/调整 -->
    <el-dialog v-model="printDialogVisible" title="打印送货单" width="900px" top="5vh" append-to-body>
      <el-form :model="printForm" label-width="86px" @submit.prevent>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="打印纸张">
              <el-select v-model="paperSizeKey" style="width: 100%;">
                <el-option v-for="size in SHIPMENT_PAPER_SIZES" :key="size.key" :label="size.name" :value="size.key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="日期">
              <el-input v-model="printForm.createTime" placeholder="打印在单据上的日期" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="送货单号">
              <el-input v-model="printForm.orderNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="业务单号">
              <el-input v-model="printForm.bizOrderNo" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户">
              <el-input v-model="printForm.merchantName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input v-model="printForm.merchantPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="送货地址">
              <el-input v-model="printForm.merchantAddress" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="printForm.remark" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="打印偏移">
              <el-input-number v-model="offsetX" :step="1" :precision="1" controls-position="right" style="width: 130px;" />
              <span style="margin: 0 12px 0 6px; color: #909399;">mm 左右（正数右移）</span>
              <el-input-number v-model="offsetY" :step="1" :precision="1" controls-position="right" style="width: 130px;" />
              <span style="margin-left: 6px; color: #909399;">mm 上下（正数下移）</span>
              <div style="color: #909399; font-size: 12px; line-height: 1.6;">
                针式打印机每次装纸位置会有偏差，打偏了用这两个值微调，会按纸张分别记住。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="制单人">
              <el-input v-model="printForm.createBy" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="送货人">
              <el-input v-model="printForm.deliveryBy" placeholder="留空则打印空白待手写" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收货人签字">
              <el-input v-model="printForm.receiveBy" placeholder="留空则打印空白待手写" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <el-table :data="printRows" size="small" border max-height="300">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="商品名称">
          <template #default="scope"><el-input v-model="scope.row.itemName" size="small" /></template>
        </el-table-column>
        <el-table-column label="规格名称">
          <template #default="scope"><el-input v-model="scope.row.skuName" size="small" /></template>
        </el-table-column>
        <el-table-column label="数量" width="110">
          <template #default="scope">
            <el-input v-model="scope.row.quantity" size="small" @change="recalculatePrintAmount(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column label="单价(元)" width="120">
          <template #default="scope">
            <el-input v-model="scope.row.unitPrice" size="small" @change="recalculatePrintAmount(scope.row)" />
          </template>
        </el-table-column>
        <el-table-column label="金额(元)" width="120">
          <template #default="scope"><el-input v-model="scope.row.amount" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="70" align="center">
          <template #default="scope">
            <el-button link type="danger" @click="printRows.splice(scope.$index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt10">
        <el-button link type="primary" @click="printRows.push({ itemName: '', skuName: '', quantity: '', unitPrice: '', amount: '' })">
          + 添加一行
        </el-button>
        <span style="color: #909399; font-size: 12px; margin-left: 12px;">
          明细不足 {{ currentPaperSize.minRows }} 行时会自动补空白行；删掉内容留空行也可以，打印出来就是空格子。
        </span>
      </div>

      <template #footer>
        <el-button @click="printDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doPrint">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="ShipmentOrder">
import {listShipmentOrder, delShipmentOrder, getShipmentOrder} from "@/api/wms/shipmentOrder";
import {listByShipmentOrderId, getLastPrices} from "@/api/wms/shipmentOrderDetail";
import {getCurrentInstance, reactive, ref, computed, watch, toRefs, onMounted, onBeforeUnmount} from "vue";
import {useWmsStore} from "../../../../store/modules/wms";
import {
  buildVeiteShipmentPanel,
  SHIPMENT_PAPER_SIZES,
  DEFAULT_SHIPMENT_PAPER_SIZE,
  getShipmentPaperSize,
  mmToPt
} from "@/components/PrintTemplate/veite-shipment-panel";
import { printStyleHandler, loadPrintLogo } from "@/utils/print";
import OrderImageGallery from "@/components/OrderImageGallery";

const { proxy } = getCurrentInstance();
const { wms_shipment_status, wms_shipment_type} = proxy.useDict("wms_shipment_status", "wms_shipment_type");
const shipmentOrderList = ref([]);
const open = ref(false);
const buttonLoading = ref(false);
const loading = ref(true);
const ids = ref([]);
const total = ref(0);
const title = ref("");
// 当前展开集合
const expandedRowKeys = ref([])
// 商品明细table的loading状态集合
const detailLoading = ref([])
// 高级搜索面板是否展开（默认仅显示综合搜索）
const advancedSearchVisible = ref(false)
// 打印纸张：记住上次选择
const PAPER_SIZE_STORAGE_KEY = 'wms:shipment:paperSize'
const paperSizeKey = ref(localStorage.getItem(PAPER_SIZE_STORAGE_KEY) || DEFAULT_SHIPMENT_PAPER_SIZE)
const currentPaperSize = computed(() => getShipmentPaperSize(paperSizeKey.value))
// 正在打印的出库单id，避免重复点击
const printingId = ref(null)
// 打印偏移(mm)：针式打印机装纸位置有偏差，按纸张分别记住
const OFFSET_STORAGE_KEY = 'wms:shipment:printOffset'
const offsetX = ref(0)
const offsetY = ref(0)

function readOffsets() {
  try {
    return JSON.parse(localStorage.getItem(OFFSET_STORAGE_KEY)) || {}
  } catch (e) {
    return {}
  }
}

function loadOffset() {
  const saved = readOffsets()[paperSizeKey.value] || {}
  offsetX.value = Number(saved.x) || 0
  offsetY.value = Number(saved.y) || 0
}

function saveOffset() {
  const all = readOffsets()
  all[paperSizeKey.value] = { x: offsetX.value || 0, y: offsetY.value || 0 }
  localStorage.setItem(OFFSET_STORAGE_KEY, JSON.stringify(all))
}

loadOffset()
watch(paperSizeKey, loadOffset)

function recalculatePrintAmount(row) {
  const quantity = Number(row.quantity)
  const unitPrice = Number(row.unitPrice)
  if (Number.isFinite(quantity) && Number.isFinite(unitPrice)) {
    row.amount = (quantity * unitPrice).toFixed(2)
  }
}

// 打印前校对弹窗
const printDialogVisible = ref(false)
const printForm = ref({})
const printRows = ref([])
const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    orderNo: undefined,
    optType: -1,
    merchantId: undefined,
    warehouseId: undefined,
    bizOrderNo: undefined,
    remark: undefined,
    totalAmount: undefined,
    orderStatus: -2,
    keyword: undefined,
  },
});

const { queryParams } = toRefs(data);
// 创建时间范围
const dateRange = ref([]);

/** 查询入库单列表 */
function getList() {
  loading.value = true;
  const query = proxy.addDateRange({...queryParams.value}, dateRange.value)
  if (query.orderStatus === -2) {
    query.orderStatus = null
  }
  if (query.optType === -1) {
    query.optType = null
  }
  listShipmentOrder(query).then(response => {
    shipmentOrderList.value = response.rows;
    total.value = response.total;
    for (let i = 0; i < total; i++) {
      detailLoading.value.push(false)
    }
    expandedRowKeys.value = []
    loading.value = false;
  });
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1;
  getList();
}

/** 重置按钮操作 */
function resetQuery() {
  dateRange.value = [];
  proxy.resetForm("queryRef");
  // 综合搜索输入框无 prop，需手动清空
  queryParams.value.keyword = undefined;
  queryParams.value.orderStatus = -2;
  queryParams.value.optType = -1;
  handleQuery();
}

/** 新增按钮操作 */
function handleAdd() {
  proxy.$router.push({ path: "/shipmentOrderEdit" });
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value;
  proxy.$modal.confirm('确认删除出库单【' + row.orderNo + '】吗？').then(function() {
    loading.value = true;
    return delShipmentOrder(_ids);
  }).then(() => {
    proxy.$modal.msgSuccess("删除成功");
  }).finally(() => {
    loading.value = false;
    getList();
  });
}

function handleUpdate(row) {
  proxy.$router.push({ path: "/shipmentOrderEdit",  query: { id: row.id } });
}

function handleGoDetail(row) {
  const index = expandedRowKeys.value.indexOf(row.id)
  if (index !== -1) {
    // 收起
    expandedRowKeys.value.splice(index, 1)
  } else {
    // 展开
    expandedRowKeys.value.push(row.id)
    loadShipmentOrderDetail(row)
  }
}

/** 打印送货单：先拉单据数据，填进弹窗给用户校对/手改，确认后再出纸 */
async function handlePrint(row, sizeKey) {
  if (printingId.value) {
    return
  }
  if (sizeKey) {
    paperSizeKey.value = sizeKey
    localStorage.setItem(PAPER_SIZE_STORAGE_KEY, sizeKey)
  }
  printingId.value = row.id
  try {
    const res = await getShipmentOrder(row.id)
    const shipmentOrder = res.data
    const wmsStore = useWmsStore()
    // 页面刚打开就点打印时往来单位可能还没加载完，先补一次，否则客户名是空的
    if (!wmsStore.merchantMap.size) {
      await wmsStore.getMerchantList()
    }
    const merchant = wmsStore.merchantMap.get(shipmentOrder.merchantId)
    printForm.value = {
      orderNo: shipmentOrder.orderNo || '',
      bizOrderNo: shipmentOrder.bizOrderNo || '',
      merchantName: merchant?.merchantName || '',
      merchantPhone: merchant?.mobile || merchant?.tel || '',
      merchantAddress: merchant?.address || '',
      createBy: shipmentOrder.createBy || '',
      createTime: proxy.parseTime(shipmentOrder.createTime, '{y}-{m}-{d}') || '',
      remark: shipmentOrder.remark || '',
      deliveryBy: '',
      receiveBy: ''
    }
    const details = shipmentOrder.details || []
    const skuIds = [...new Set(details.map(detail => detail.skuId).filter(Boolean))]
    const lastPriceMap = {}
    if (shipmentOrder.merchantId && skuIds.length) {
      try {
        const priceRes = await getLastPrices(shipmentOrder.merchantId, skuIds)
        priceRes.data?.forEach(item => {
          lastPriceMap[String(item.skuId)] = item.price
        })
      } catch (e) {
        // 历史报价接口异常不应阻断打印，下面会自动回退到商品售价。
        console.warn('查询客户历史售价失败，打印单价将使用商品售价', e)
      }
    }
    printRows.value = details.map(detail => {
      const quantity = Number(detail.quantity || 0)
      const amount = Number(detail.amount || 0)
      const lastPrice = lastPriceMap[String(detail.skuId)]
      const preferredPrice = lastPrice !== undefined && lastPrice !== null
        ? lastPrice
        : detail.itemSku?.sellingPrice
      const unitPrice = preferredPrice === undefined || preferredPrice === null || preferredPrice === ''
        ? NaN
        : Number(preferredPrice)
      return {
        itemName: detail.item?.itemName || '',
        skuName: detail.itemSku?.skuName || '',
        quantity: quantity.toFixed(0),
        // 当前客户有历史成交价时优先使用；没有历史记录则回退到商品售价。
        unitPrice: Number.isFinite(unitPrice) ? unitPrice.toFixed(2) : '',
        amount: amount.toFixed(2)
      }
    })
    printDialogVisible.value = true
  } catch (e) {
    console.error(e)
    proxy.$modal.msgError('打印失败：' + (e?.msg || e?.message || '请稍后重试'))
  } finally {
    printingId.value = null
  }
}

/** 弹窗确认后真正出纸 */
async function doPrint() {
  localStorage.setItem(PAPER_SIZE_STORAGE_KEY, paperSizeKey.value)
  saveOffset()
  const minRows = currentPaperSize.value.minRows || 0
  let no = 0
  const rows = printRows.value.map(row => {
    const empty = !row.itemName && !row.skuName && !row.quantity && !row.unitPrice && !row.amount
    // 整行留空的就打成空格子，不占序号，方便手写补货
    return empty
      ? { index: '', itemName: '', skuName: '', quantity: '', unitPrice: '', amount: '' }
      : { ...row, index: ++no }
  })
  // 明细不足时补空白行，单据看起来是画好格子的完整表
  while (rows.length < minRows) {
    rows.push({ index: '', itemName: '', skuName: '', quantity: '', unitPrice: '', amount: '' })
  }
  try {
    // 先在主页面把 logo 取成 base64，交给 hiprint 的打印 iframe，
    // 免得图片没加载完就触发打印，纸上留一个空白方框
    const logo = await loadPrintLogo()
    const printTemplate = new proxy.$hiprint.PrintTemplate({
      template: buildVeiteShipmentPanel(paperSizeKey.value, { logo })
    })
    // hiprint 的偏移单位是 pt，整页内容一起平移
    const printOptions = {
      leftOffset: mmToPt(offsetX.value || 0),
      topOffset: mmToPt(offsetY.value || 0)
    }
    printTemplate.print({ ...printForm.value, table: rows }, printOptions, { styleHandler: printStyleHandler })
    printDialogVisible.value = false
  } catch (e) {
    console.error(e)
    proxy.$modal.msgError('打印失败：' + (e?.msg || e?.message || '请稍后重试'))
  }
}


function handleExpandExchange(value, expandedRows) {
  if (!ifExpand(expandedRows)) {
    return
  }
  expandedRowKeys.value = expandedRows.map(it => it.id)
  loadShipmentOrderDetail(value)
}

function loadShipmentOrderDetail(row) {
  const index = shipmentOrderList.value.findIndex(it => it.id === row.id)
  detailLoading.value[index] = true
  listByShipmentOrderId(row.id).then(res => {
    if (res.data?.length) {
      const details = res.data.map(it => {
        return {
          ...it,
          warehouseName: useWmsStore().warehouseMap.get(it.warehouseId)?.warehouseName
        }
      })
      shipmentOrderList.value[index].details = details
    }
  }).finally(() => {
    detailLoading.value[index] = false
  })
}

function ifExpand(expandedRows) {
  if (expandedRows.length < expandedRowKeys.value.length) {
    expandedRowKeys.value = expandedRows.map(it => it.id)

    return false;
  }

  return true
}

function getRowKey(row) {
  return row.id
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

getList();

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>
<style lang="scss">
.el-statistic__content {
  font-size: 14px;
}
.el-table .vertical-top-cell {
  vertical-align: top
}
</style>
