<template>
  <div>
    <div class="receipt-order-edit-wrapper app-container" style="margin-bottom: 60px" v-loading="loading">
      <section class="mobile-only mobile-order-hero">
        <div>
          <small>{{ form.id ? '编辑出库单' : '新建出库单' }}</small>
          <h1>{{ form.orderNo || '待生成单号' }}</h1>
          <p>{{ warehouseName(form.warehouseId) }} · {{ orderTypeLabel }}</p>
        </div>
        <div class="mobile-order-total">
          <span>数量</span>
          <strong>{{ Number(form.totalQuantity || 0).toFixed(0) }}</strong>
        </div>
      </section>
      <el-card header="出库单基本信息" class="order-info-card">
        <el-form label-width="108px" :model="form" ref="shipmentForm" :rules="rules">
          <el-row :gutter="24">
            <el-col :span="11">
              <el-form-item label="出库单号" prop="orderNo">
                <el-input class="w200" v-model="form.orderNo" placeholder="出库单号"
                          :disabled="form.id"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="仓库" prop="warehouseId">
                <el-select v-model="form.warehouseId" placeholder="请选择仓库" @change="handleChangeWarehouse"
                           filterable>
                  <el-option v-for="item in useWmsStore().warehouseList" :key="item.id" :label="item.warehouseName"
                             :value="item.id"/>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="总数量" prop="totalQuantity">
                <el-input-number style="width: 100%" v-model="form.totalQuantity" :controls="false" :precision="0"
                                 :disabled="true"></el-input-number>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="24">
            <el-col :span="11">
              <el-form-item label="出库类型" prop="optType">
                <el-radio-group v-model="form.optType">
                  <el-radio-button
                    v-for="item in wms_shipment_type"
                    :key="item.value"
                    :label="item.value"
                  >{{ item.label }}
                  </el-radio-button
                  >
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="客户" prop="merchantId">
                <el-select v-model="form.merchantId" placeholder="请选择客户" clearable filterable @change="loadLastPrices">
                  <el-option v-for="item in useWmsStore().merchantList.filter(m => m.merchantType != 2)" :key="item.id" :label="item.merchantName"
                             :value="item.id"/>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="业务单号" prop="bizOrderNo">
                <el-input v-model="form.bizOrderNo" placeholder="请输入业务单号"></el-input>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="24">
            <el-col :span="11">
              <el-form-item label="备注" prop="remark">
                <el-input
                  v-model="form.remark"
                  placeholder="备注...100个字符以内"
                  rows="4"
                  maxlength="100"
                  type="textarea"
                  show-word-limit="show-word-limit"
                ></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <div class="amount-field">
                <el-form-item label="总金额" prop="totalAmount">
                  <el-input-number style="width: 100%;" v-model="form.totalAmount" :precision="3" :min="0"></el-input-number>
                </el-form-item>
                <el-button link type="primary" @click="handleAutoCalc" style="line-height: 32px">自动计算
                </el-button>
              </div>
            </el-col>
          </el-row>
          <el-row :gutter="24">
            <el-col :span="24">
              <el-form-item label="补充图片">
                <image-upload
                  v-model="form.supplementImageIds"
                  :limit="9"
                  :file-size="10"
                />
                <div class="image-help">可上传备货、装车、包装或签收照片，最多 9 张。</div>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>
      <el-card class="mt10 order-detail-card">
        <template #header>
          <div class="detail-card-header">
            <div>
              <span>商品明细</span>
              <small class="mobile-only">已选 {{ form.details.length }} 项</small>
            </div>
            <el-button class="mobile-only" type="primary" plain icon="Plus" @click="showAddItem" :disabled="!form.warehouseId">添加</el-button>
          </div>
        </template>
        <div class="receipt-order-content">
          <div class="flex-space-between mb8 desktop-add-row">
            <!-- <div>
              <span>审批 | 一物一码：</span>
              <el-switch
                :before-change="goSaasTip"
                class="mr10 ml10"
                inline-prompt
                size="large"
                :active-value="true"
                :inactive-value="false"
                active-text="开启"
                inactive-text="关闭"
              />
            </div> -->
            <el-popover
              placement="left"
              title="提示"
              :width="200"
              trigger="hover"
              :disabled="form.warehouseId"
              content="请先选择仓库！"
            >
              <template #reference>
                <el-button type="primary" plain="plain" size="default" @click="showAddItem" icon="Plus"
                           :disabled="!form.warehouseId">添加商品
                </el-button>
              </template>
            </el-popover>
          </div>
          <el-table :data="form.details" border stripe empty-text="暂无商品明细" class="desktop-only">
            <el-table-column label="商品信息" prop="itemSku.itemName">
              <template #default="{ row }">
                <div>{{
                    row.item.itemName + (row.item.itemCode ? ('(' + row.item.itemCode + ')') : '')
                  }}
                </div>
                <div v-if="row.item.itemBrand">
                  品牌：{{ useWmsStore().itemBrandMap.get(row.item.itemBrand).brandName }}
                </div>
              </template>
            </el-table-column>
            <el-table-column label="规格信息">
              <template #default="{ row }">
                <div>{{ row.itemSku.skuName }}</div>
                <div v-if="row.itemSku.barcode">条码：{{row.itemSku.barcode}}</div>
              </template>
            </el-table-column>
            <el-table-column label="出库数量" prop="quantity" width="220">
              <template #default="scope">
                <el-input-number
                  v-model="scope.row.quantity"
                  placeholder="出库数量"
                  :min="1"
                  :precision="0"
                  @change="handleChangeQuantity"
                ></el-input-number>
              </template>
            </el-table-column>
            <el-table-column label="单价" prop="itemSku.sellingPrice" width="220">
              <template #default="{ row }">
                <!-- <div v-if="row.itemSku.sellingPrice">{{ row.itemSku.sellingPrice }}</div>
                <div v-else>暂无售价</div> -->
                <el-input-number
                  v-model="row.itemSku.sellingPrice"
                  placeholder="单价"
                  :min="0"
                  :precision="2"
                  @change="handleChangeQuantity"
                ></el-input-number>
                <div v-if="lastPriceMap[String(row.skuId)]">
                  <el-tooltip
                    :content="'出库时间：' + parseTime(lastPriceMap[String(row.skuId)].createTime, '{y}-{m}-{d}')
                      + '，单号：' + lastPriceMap[String(row.skuId)].orderNo + '，点击填入'"
                    placement="top"
                  >
                    <el-link type="primary" :underline="false" @click="applyLastPrice(row)">
                      上次报价：￥{{ Number(lastPriceMap[String(row.skuId)].price).toFixed(2) }}
                    </el-link>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="金额" prop="amount" width="220" align="center">
              <template #default="scope">
                <div v-if="scope.row.amount !== undefined && scope.row.amount !== null">
                  <span style="color: #409EFF; font-weight: bold; font-size: 18px;">
                    ￥{{ Number(scope.row.amount).toFixed(2) }}
                  </span>
                </div>
                <div v-else>
                  <dict-tag :customTags="[{ label: '请输入数量和单价', type: 'info' }]"
                  ></dict-tag>
                </div>
                <!-- <el-input-number
                  v-model="scope.row.amount"
                  placeholder="金额"
                  :precision="3"
                  :min="0"
                  :max="2147483647"
                ></el-input-number> -->
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="right" fixed="right">
              <template #default="scope">
                <el-button icon="Delete" type="danger" plain size="small"
                           @click="handleDeleteDetail(scope.row, scope.$index)" link>删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="mobile-only mobile-edit-list">
            <div v-for="(row, index) in form.details" :key="row.id || row.skuId" class="mobile-edit-card">
              <div class="mobile-edit-card__title">
                <div>
                  <strong>{{ row.item.itemName }}</strong>
                  <div>{{ row.itemSku.skuName }}</div>
                  <small v-if="row.itemSku.barcode">条码：{{ row.itemSku.barcode }}</small>
                </div>
                <el-button type="danger" link icon="Delete" @click="handleDeleteDetail(row, index)">删除</el-button>
              </div>
              <div class="mobile-edit-fields">
                <label>
                  <span>出库数量</span>
                  <el-input-number v-model="row.quantity" :min="1" :precision="0" @change="handleChangeQuantity" />
                </label>
                <label>
                  <span>单价</span>
                  <el-input-number v-model="row.itemSku.sellingPrice" :min="0" :precision="2" @change="handleChangeQuantity" />
                </label>
              </div>
              <el-button
                v-if="lastPriceMap[String(row.skuId)]"
                link
                type="primary"
                @click="applyLastPrice(row)"
              >
                使用上次报价 ￥{{ Number(lastPriceMap[String(row.skuId)].price).toFixed(2) }}
              </el-button>
              <div class="mobile-edit-total">金额：￥{{ Number(row.amount || 0).toFixed(2) }}</div>
            </div>
            <el-empty v-if="!form.details.length" description="请添加需要出库的库存" :image-size="64" />
          </div>
        </div>
      </el-card>
      <InventorySelect
        ref="inventorySelectRef"
        :model-value="inventorySelectShow"
        @handleOkClick="handleOkClick"
        @handleCancelClick="inventorySelectShow = false"
        :size="mobileDrawerSize"
        :select-warehouse-disable="false"
        :warehouse-id="form.warehouseId"
        :selected-inventory="selectedInventory"
      />
    </div>
    <div class="footer-global">
      <div class="btn-box">
        <div class="primary-actions">
          <el-button @click="doShipment" type="primary" class="ml10">完成出库</el-button>
          <el-button @click="updateToInvalid" type="danger" v-if="form.id">作废</el-button>
        </div>
        <div class="secondary-actions">
          <el-button @click="save" type="primary">暂存</el-button>
          <el-button @click="cancel" class="mr10">取消</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup name="ShipmentOrderEdit">
import {computed, getCurrentInstance, onMounted, reactive, ref, toRef, toRefs, watch} from "vue";
import {addShipmentOrder, getShipmentOrder, updateShipmentOrder, shipment} from "@/api/wms/shipmentOrder";
import {delShipmentOrderDetail, getLastPrices} from "@/api/wms/shipmentOrderDetail";
import {ElMessage, ElMessageBox} from "element-plus";
import {useRoute} from "vue-router";
import {useWmsStore} from '@/store/modules/wms'
import {numSub, generateNo} from '@/utils/ruoyi'
import InventorySelect from "@/views/components/InventorySelect.vue";
import {getWarehouseAndSkuKey} from "@/utils/wmsUtil"

const {proxy} = getCurrentInstance();
const {wms_shipment_type} = proxy.useDict("wms_shipment_type");

const loading = ref(false)
const isMobileScreen = () => typeof window !== 'undefined' && window.matchMedia('(max-width: 768px)').matches
const mobileDrawerSize = computed(() => isMobileScreen() ? '100%' : '90%')
const initFormData = {
  id: undefined,
  orderNo: undefined,
  optType: "2",
  merchantId: undefined,
  bizOrderNo: undefined,
  totalAmount: undefined,
  orderStatus: 0,
  remark: undefined,
  supplementImageIds: undefined,
  warehouseId: "1945324849064845313",
  totalQuantity: 0,
  details: [],
}
const inventorySelectRef = ref(null)
const selectedInventory = ref([])
const data = reactive({
  form: {...initFormData},
  rules: {
    orderNo: [
      {required: true, message: "出库单号不能为空", trigger: "blur"}
    ],
    optType: [
      {required: true, message: "出库类型不能为空", trigger: "change"}
    ],
    warehouseId: [
      {required: true, message: "请选择仓库", trigger: ['blur', 'change']}
    ],
  }
});
const {form, rules} = toRefs(data);
const warehouseName = (id) => useWmsStore().warehouseMap.get(id)?.warehouseName || '请选择仓库'
const orderTypeLabel = computed(() => proxy.selectDictLabel(wms_shipment_type.value, form.value.optType) || '出库类型')
const cancel = async () => {
  await proxy?.$modal.confirm('确认取消编辑出库单吗？');
  close()
}
const close = () => {
  if (window.matchMedia('(max-width: 768px)').matches) {
    proxy?.$router.push({ path: "/mobile", query: { type: "shipment" } });
    return
  }
  const obj = {path: "/shipmentOrder"};
  proxy?.$tab.closeOpenPage(obj);
}
const inventorySelectShow = ref(false)

// 选择商品 start
const showAddItem = () => {
  inventorySelectRef.value.getList()
  inventorySelectShow.value = true
}
// 选择成功
const handleOkClick = (item) => {
  inventorySelectShow.value = false
  selectedInventory.value = [...item]
  item.forEach(it => {
    if (!form.value.details.find(detail => getWarehouseAndSkuKey(detail) === getWarehouseAndSkuKey(it))) {
      form.value.details.push(
        {
          itemSku: it.itemSku,
          item: it.item,
          skuId: it.skuId,
          amount: undefined,
          quantity: undefined,
          warehouseId: form.value.warehouseId,
          inventoryId: it.id,
        })
    }
  })
  loadLastPrices()
}
// 选择商品 end

// 客户历史报价 start
// skuId -> 该客户最近一次出库记录 {price, orderNo, createTime}
const lastPriceMap = ref({})

const loadLastPrices = () => {
  const merchantId = form.value.merchantId
  const skuIds = (form.value.details || []).map(it => it.skuId).filter(Boolean)
  if (!merchantId || !skuIds.length) {
    lastPriceMap.value = {}
    return
  }
  getLastPrices(merchantId, skuIds).then(res => {
    const map = {}
    if (res.data?.length) {
      res.data.forEach(it => {
        map[String(it.skuId)] = it
      })
    }
    lastPriceMap.value = map
  })
}

const applyLastPrice = (row) => {
  const last = lastPriceMap.value[String(row.skuId)]
  if (last) {
    row.itemSku.sellingPrice = Number(last.price)
    handleChangeQuantity()
  }
}
// 客户历史报价 end

// 初始化receipt-order-form ref
const shipmentForm = ref()

const save = async () => {
  await proxy?.$modal.confirm('确认暂存出库单吗？');
  doSave()
}

const getParamsBeforeSave = (orderStatus) => {
  let details = []
  if (form.value.details?.length) {
    // 构建参数
    details = form.value.details.map(it => {
      return {
        id: it.id,
        receiptOrderId: form.value.id,
        skuId: it.skuId,
        amount: it.amount,
        quantity: it.quantity,
        warehouseId: form.value.warehouseId
      }
    })
  }

  return {
    id: form.value.id,
    orderNo: form.value.orderNo,
    optType: form.value.optType,
    merchantId: form.value.merchantId,
    bizOrderNo: form.value.bizOrderNo,
    orderStatus,
    remark: form.value.remark,
    supplementImageIds: form.value.supplementImageIds,
    totalAmount: form.value.totalAmount,
    totalQuantity: form.value.totalQuantity,
    warehouseId: form.value.warehouseId,
    details: details
  }
}

const doSave = (orderStatus = 0) => {
  //验证shipmentForm表单
  shipmentForm.value?.validate((valid) => {
    // 校验
    if (!valid) {
      return ElMessage.error('请填写必填项')
    }

    //('提交前校验',form.value)
    const params = getParamsBeforeSave(orderStatus)

    loading.value = true
    if (params.id) {
      updateShipmentOrder(params).then((res) => {
        if (res.code === 200) {
          ElMessage.success(res.msg)
          close()
        } else {
          ElMessage.error(res.msg)
        }
      }).finally(()=>{
        loading.value = false
      })
    } else {
      addShipmentOrder(params).then((res) => {
        if (res.code === 200) {
          ElMessage.success(res.msg)
          close()
        } else {
          ElMessage.error(res.msg)
        }
      }).finally(()=>{
        loading.value = false
      })
    }
  })
}

const doShipment = async () => {
  await proxy?.$modal.confirm('确认出库吗？');
  shipmentForm.value?.validate((valid) => {
    // 校验
    if (!valid) {
      return ElMessage.error('请填写必填项')
    }
    if (!form.value.details?.length) {
      return ElMessage.error('请选择商品')
    }
    const invalidQuantityList = form.value.details.filter(it => !it.quantity)
    if (invalidQuantityList?.length) {
      return ElMessage.error('请选择数量')
    }
    const params = getParamsBeforeSave(1)

    loading.value = true
    shipment(params).then((res) => {
      if (res.code === 200) {
        ElMessage.success('出库成功')
        close()
      } else {
        ElMessage.error(res.msg)
      }
    }).finally(()=>{
      loading.value = false
    })
  })
}

const updateToInvalid = async () => {
  await proxy?.$modal.confirm('确认作废出库单吗？');
  doSave(-1)
}

const route = useRoute();
onMounted(() => {
  const id = route.query && route.query.id;
  if (id) {
    loadDetail(id)
  } else if (route.query && route.query.fromAi) {
    prefillFromAiDraft()
  } else {
    form.value.orderNo = 'CK' + generateNo()
  }
})

/** 由 AI 草稿预填出库单（不直接保存，等用户确认） */
const prefillFromAiDraft = () => {
  let draft = null
  try {
    draft = JSON.parse(sessionStorage.getItem('wms_ai_shipment_draft') || 'null')
  } catch (e) { /* ignore */ }
  sessionStorage.removeItem('wms_ai_shipment_draft')

  form.value.orderNo = 'CK' + generateNo()
  if (!draft) {
    return
  }
  if (draft.warehouseId) form.value.warehouseId = String(draft.warehouseId)
  if (draft.merchantId) form.value.merchantId = String(draft.merchantId)
  if (draft.remark) form.value.remark = draft.remark

  const wid = form.value.warehouseId
  form.value.details = (draft.details || []).map(d => ({
    item: d.item || {},
    itemSku: d.itemSku || {},
    skuId: d.skuId != null ? String(d.skuId) : d.skuId,
    quantity: d.quantity,
    amount: d.amount,
    warehouseId: wid,
    inventoryId: undefined,
  }))
  selectedInventory.value = form.value.details
    .filter(d => d.skuId)
    .map(d => ({ skuId: d.skuId, warehouseId: d.warehouseId }))

  inventorySelectRef.value?.setWarehouseId(form.value.warehouseId)
  loadLastPrices()
  handleChangeQuantity()
  handleAutoCalc()

  const tips = [...(draft.warnings || [])]
  ;(draft.unresolved || []).forEach(u => tips.push(`未匹配到商品：${u.name}（需手动添加）`))
  if (tips.length) {
    ElMessage.warning({ message: 'AI 草稿提示：\n' + tips.join('\n'), duration: 8000 })
  } else {
    ElMessage.success('已根据 AI 草稿预填，请核对后保存或出库')
  }
}


// 获取入库单详情
const loadDetail = (id) => {
  loading.value = true
  getShipmentOrder(id).then((response) => {
    if (response.data.details?.length) {
      selectedInventory.value = response.data.details.map(it => {
        return {
          id: it.id,
          skuId: it.skuId,
          warehouseId: it.warehouseId
        }
      })
    }
    form.value = {...response.data}
    inventorySelectRef.value.setWarehouseId(form.value.warehouseId)
    loadLastPrices()
    Promise.resolve();
  }).then(() => {
  }).finally(() => {
    loading.value = false
  })
}

const handleChangeWarehouse = (e) => {
  form.value.details = []
  inventorySelectRef.value.setWarehouseId(form.value.warehouseId)
}

const handleChangeQuantity = () => {
  let sum = 0
  form.value.details.forEach(it => {
    if (it.quantity) {
      sum += Number(it.quantity)
      it.amount = it.itemSku.sellingPrice ? (Number(it.quantity) * Number(it.itemSku.sellingPrice)) : undefined
      // it.amount = it.quantity * it.itemSku.costPrice
    }
  })
  form.value.totalQuantity = sum
}

const handleAutoCalc = () => {
  let sum = undefined
  form.value.details.forEach(it => {
    if (it.amount >= 0) {
      if (!sum) {
        sum = 0
      }
      sum = numSub(sum, -Number(it.amount))
    }
  })
  form.value.totalAmount = sum
}

const handleDeleteDetail = (row, index) => {
  if (row.id) {
    proxy.$modal.confirm('确认删除本条商品明细吗？如确认会立即执行！').then(function () {
      loading.value = true;
      return delShipmentOrderDetail(row.id);
    }).then(() => {
      form.value.details.splice(index, 1)
      proxy.$modal.msgSuccess("删除成功");
    }).finally(()=>{
      loading.value=false
    })
  } else {
    form.value.details.splice(index, 1)
  }
  const indexOfSelected = selectedInventory.value.findIndex(it => getWarehouseAndSkuKey(it) === getWarehouseAndSkuKey(row))
  selectedInventory.value.splice(indexOfSelected, 1)
}
const goSaasTip = () => {
  ElMessageBox.alert('如需体验，请在公众号内回复：saas', '请去Saas版本体验', {
    confirmButtonText: '确定'
  })
  return false
}
</script>

<style lang="scss" scoped>
@import "@/assets/styles/variables.module";

.btn-box {
  width: calc(100% - #{$base-sidebar-width});
  display: flex;
  align-items: center;
  justify-content: space-between;
  float: right;
}

.el-statistic__content {
  font-size: 14px;
}

.image-help {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 768px) {
  .receipt-order-edit-wrapper {
    margin-bottom: 132px !important;
    padding: 12px 10px 0;
    background: #f3f6fa;
  }

  .receipt-order-edit-wrapper :deep(.el-col) {
    flex: 0 0 100%;
    max-width: 100%;
  }

  .receipt-order-edit-wrapper :deep(.el-card) {
    border: 0;
    border-radius: 12px;
    box-shadow: 0 4px 18px rgba(31, 45, 61, 0.06);
  }

  .receipt-order-edit-wrapper :deep(.el-card__header) {
    padding: 13px 14px;
    font-weight: 700;
  }

  .receipt-order-edit-wrapper :deep(.el-card__body) {
    padding: 14px;
  }

  .receipt-order-edit-wrapper :deep(.el-form-item) {
    display: block;
    margin-bottom: 14px;
  }

  .receipt-order-edit-wrapper :deep(.el-form-item__label) {
    justify-content: flex-start;
    width: auto !important;
    height: auto;
    margin-bottom: 6px;
    color: #667085;
    font-size: 13px;
    line-height: 1.2;
  }

  .receipt-order-edit-wrapper :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }

  .receipt-order-edit-wrapper :deep(.el-select),
  .receipt-order-edit-wrapper :deep(.el-input-number),
  .receipt-order-edit-wrapper :deep(.el-input),
  .receipt-order-edit-wrapper :deep(.el-textarea) {
    width: 100% !important;
  }

  .receipt-order-edit-wrapper :deep(.el-radio-group) {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
    width: 100%;
  }

  .receipt-order-edit-wrapper :deep(.el-radio-button__inner) {
    width: 100%;
    border-left: var(--el-border);
    border-radius: 8px;
  }

  .mobile-order-hero {
    display: flex !important;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
    padding: 14px;
    color: #fff;
    background: #059669;
    border-radius: 12px;
  }

  .mobile-order-hero small {
    opacity: .84;
    font-weight: 700;
  }

  .mobile-order-hero h1 {
    margin: 4px 0;
    font-size: 20px;
    line-height: 1.2;
    word-break: break-all;
  }

  .mobile-order-hero p {
    margin: 0;
    font-size: 13px;
    opacity: .9;
  }

  .mobile-order-total {
    flex: 0 0 auto;
    min-width: 74px;
    padding: 8px 10px;
    text-align: center;
    background: rgba(255,255,255,.16);
    border-radius: 10px;
  }

  .mobile-order-total span,
  .detail-card-header small {
    display: block;
    font-size: 12px;
  }

  .mobile-order-total strong {
    display: block;
    margin-top: 2px;
    font-size: 24px;
    line-height: 1;
  }

  .amount-field {
    display: block !important;
  }

  .amount-field .el-button {
    margin: -6px 0 0 0;
  }

  .detail-card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
  }

  .detail-card-header > div {
    display: grid;
    gap: 2px;
  }

  .detail-card-header small {
    color: #98a2b3;
    font-weight: 400;
  }

  .desktop-add-row {
    display: none;
  }

  .mobile-edit-list {
    display: grid !important;
    gap: 10px;
  }

  .mobile-edit-card {
    padding: 12px;
    background: #fbfcfe;
    border: 1px solid #e8edf3;
    border-radius: 10px;
  }

  .mobile-edit-card__title {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 10px;
  }

  .mobile-edit-card__title > div {
    min-width: 0;
  }

  .mobile-edit-card__title strong,
  .mobile-edit-card__title div,
  .mobile-edit-card__title small {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-edit-card__title small {
    display: block;
    color: #98a2b3;
  }

  .mobile-edit-fields {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    margin-top: 12px;
  }

  .mobile-edit-fields label {
    display: grid;
    gap: 6px;
    color: #667085;
    font-size: 13px;
  }

  .mobile-edit-total {
    margin-top: 10px;
    padding-top: 10px;
    color: #059669;
    font-size: 16px;
    font-weight: 700;
    text-align: right;
    border-top: 1px dashed #dfe5ec;
  }

  .footer-global .btn-box {
    width: 100%;
    display: grid;
    grid-template-columns: 1fr;
    padding: 10px 10px calc(72px + env(safe-area-inset-bottom));
    gap: 8px;
    background: #fff;
    box-shadow: 0 -3px 12px rgba(31, 45, 61, 0.08);
  }

  .footer-global .btn-box > div {
    display: flex;
    gap: 8px;
  }

  .footer-global .el-button {
    flex: 1;
    min-height: 42px;
    margin-left: 0 !important;
  }

  .footer-global .primary-actions {
    order: 1;
  }

  .footer-global .secondary-actions {
    order: 2;
  }
}
</style>
