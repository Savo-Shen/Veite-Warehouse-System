<template>
  <div v-if="!checking" style="display: flex;justify-content: center;align-items: center;height: 80vh">
    <el-card header="选择仓库后开始盘库，进入后手动添加需要盘点的商品" >
      <el-form>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" placeholder="请选择仓库" :disabled="checking"
                     filterable>
            <el-option v-for="item in useWmsStore().warehouseList" :key="item.id" :label="item.warehouseName"
                       :value="item.id"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" plain="plain" size="default" @click="startCheck"  style="width: 100%!important;">开始盘库</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
  <div v-else v-loading="loading">
    <div class="receipt-order-edit-wrapper app-container" style="margin-bottom: 60px">
      <el-card header="盘库单基本信息">
        <el-form label-width="108px" :model="form" ref="checkForm" :rules="rules">
          <el-row :gutter="24">
            <el-col :span="11">
              <el-form-item label="盘库单号" prop="orderNo">
                <el-input class="w200" v-model="form.orderNo" placeholder="盘库单号"
                          :disabled="form.id"></el-input>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="仓库" prop="warehouseId">
                <el-select v-model="form.warehouseId" placeholder="请选择仓库" :disabled="checking"
                           filterable>
                  <el-option v-for="item in useWmsStore().warehouseList" :key="item.id" :label="item.warehouseName"
                             :value="item.id"/>
                </el-select>
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
              <el-form-item label="盈亏数" prop="totalQuantity">
                <el-input-number v-model="form.totalQuantity" :controls="false" :precision="0"
                                 :disabled="true"></el-input-number>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>
      <el-card header="商品明细" class="mt10">
        <div class="receipt-order-content">
          <div class="flex-space-between mb8">
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
                  <div>
                    <el-button type="primary" plain="plain" size="default" @click="showInventorySelect" icon="Plus"
                               :disabled="!form.warehouseId">添加盘点商品
                    </el-button>
                    <el-button type="warning" plain="plain" size="default" @click="showSkuSelect" icon="Plus"
                               :disabled="!form.warehouseId">新增库存
                    </el-button>
                  </div>
          </div>
          <el-table :data="form.details" border stripe empty-text="暂无商品明细">
            <el-table-column label="商品信息" prop="itemSku.itemName">
              <template #default="scope">
                  <div>{{
                      scope.row.item.itemName + (scope.row.item.itemCode ? ('(' + scope.row.item.itemCode + ')') : '')
                    }}
                  </div>
                  <div v-if="scope.row.item.itemBrand">
                    品牌：{{ useWmsStore().itemBrandMap.get(scope.row.item.itemBrand).brandName }}
                  </div>
                </template>
            </el-table-column>
            <el-table-column label="规格信息">
              <template #default="{ row }">
                <template v-if="row.newInventoryDetail">
                  <div v-if="row.itemSku">{{ row.itemSku.skuName + (row.itemSku.barcode ? ('(' + row.itemSku.barcode + ')') : '') }}</div>
                  <div v-else>请选择商品</div>
                </template>
                <template v-else>
                  <div>{{ row.itemSku.skuName + (row.itemSku.barcode ? ('(' + row.itemSku.barcode + ')') : '') }}</div>
                </template>
              </template>
            </el-table-column>
            <el-table-column label="位置">
              <template #default="{ row }">
                <!-- <div>{{ row }}</div> -->
                <dict-tag v-if="!row.location"
                :customTags="[
                  { label: '暂无位置', type: 'info' }
                ]"
              />
              <div v-else>
                <dict-tag :customTags="[
                  { label: row.location.locationCode, type: '' }
                ]"
                />
                <div>{{ row.location.locationName }}</div>
              </div>
              </template>
            </el-table-column>
            <el-table-column label="账面库存" align="right" width="150">
              <template #default="{ row }">
                <el-statistic :value="Number(row.quantity)" :precision="0"/>
              </template>
            </el-table-column>
            <el-table-column label="盈亏数" prop="remainQuantity" align="right" width="150">
              <template #default="{ row }">
                <el-statistic :value="Number(row.checkQuantity) - Number(row.quantity)" :precision="0"/>
              </template>
            </el-table-column>
            <el-table-column label="实际库存" prop="checkQuantity" width="180">
              <template #default="scope">
                <el-input-number
                  v-model="scope.row.checkQuantity"
                  placeholder="实际库存"
                  :min="0"
                  :precision="0"
                  @change="handleChangeQuantity"
                ></el-input-number>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="center">
              <template #default="scope">
                <el-button icon="Delete" type="danger" plain size="small"
                           @click="handleDeleteDetail(scope.row, scope.$index)" link>删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
      <SkuSelect
        ref="skuSelectRef"
        :model-value="skuSelectShow"
        :selected-sku="selectedSku"
        @handleOkClick="handleOkClick"
        @handleCancelClick="skuSelectShow = false"
        :size="'80%'"
      />
      <InventorySelect
        ref="inventorySelectRef"
        :model-value="inventorySelectShow"
        @handleOkClick="handleInventoryOkClick"
        @handleCancelClick="inventorySelectShow = false"
        :size="'90%'"
        :selected-inventory="selectedInventory"
        :min-quantity="null"
      />
    </div>
    <div class="footer-global" v-if="checking">
      <div class="btn-box">
        <div>
          <el-button @click="doCheck" type="primary" class="ml10">完成盘库</el-button>
          <el-button @click="updateToInvalid" type="danger" v-if="form.id">作废</el-button>
        </div>
        <div>
          <el-button @click="save" type="primary">暂存</el-button>
          <el-button @click="cancel" class="mr10">取消</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup name="CheckOrderEdit">
import {computed, getCurrentInstance, onMounted, reactive, ref, toRef, toRefs, watch} from "vue";
const skuSelectRef = ref(null)
import {addCheckOrder, getCheckOrder, updateCheckOrder, check} from "@/api/wms/checkOrder";
import {delCheckOrderDetail} from "@/api/wms/checkOrderDetail";
import {ElMessage, ElMessageBox} from "element-plus";
import {useRoute} from "vue-router";
import {useWmsStore} from '@/store/modules/wms'
import {numSub, generateNo} from '@/utils/ruoyi'
import SkuSelect from "@/views/components/SkuSelect.vue";
import InventorySelect from "@/views/components/InventorySelect.vue";
import {getWarehouseAndSkuKey} from "@/utils/wmsUtil";

const {proxy} = getCurrentInstance();
const {wms_shipment_type} = proxy.useDict("wms_shipment_type");
const loading = ref(false)
const selectedSku = ref([])
const initFormData = {
  id: undefined,
  orderNo: undefined,
  orderStatus: 0,
  remark: undefined,
  warehouseId: "1945324849064845313",
  totalQuantity: 0,
  details: [],
}
const data = reactive({
  form: {...initFormData},
  rules: {
    orderNo: [
      {required: true, message: "盘库单号不能为空", trigger: "blur"}
    ],
    warehouseId: [
      {required: true, message: "请选择仓库", trigger: ['blur', 'change']}
    ],
  }
});
const {form, rules} = toRefs(data);
const cancel = async () => {
  await proxy?.$modal.confirm('确认取消编辑盘库单吗？');
  close()
}
const close = () => {
  const obj = {path: "/checkOrder"};
  proxy?.$tab.closeOpenPage(obj);
}
const inventorySelectShow = ref(false)
const skuSelectShow = ref(false)
const currentSkuSelectIndex = ref(null)
const inventorySelectRef = ref(null)
const selectedInventory = ref([])
// 盘库中标识
const checking = ref(false)

// 选择商品 start
// 开始盘库：仅进入盘库界面，需要盘点的商品由用户手动添加，避免库存量大时一次性全量加载卡死
const startCheck = () => {
  if (!form.value.warehouseId) {
    return ElMessage.error('请先选择仓库！')
  }
  checking.value = true
}

// 从现有库存中选择需要盘点的商品（分页）
const showInventorySelect = () => {
  inventorySelectRef.value.setWarehouseId(form.value.warehouseId)
  inventorySelectRef.value.getList()
  inventorySelectShow.value = true
}

const handleInventoryOkClick = (items) => {
  inventorySelectShow.value = false
  selectedInventory.value = [...items]
  items.forEach(it => {
    if (!form.value.details.find(detail => detail.skuId === it.skuId)) {
      form.value.details.unshift({
        itemSku: it.itemSku,
        item: it.item,
        location: it.location,
        inventoryId: it.id,
        skuId: it.skuId,
        warehouseId: it.warehouseId,
        quantity: Number(it.quantity),
        checkQuantity: Number(it.quantity),
        newInventory: false
      })
      selectedSku.value.push({id: it.skuId})
    }
  })
}
// 选择成功
const handleOkClick = (item) => {
  skuSelectShow.value = false
  selectedSku.value = [...item]
  item.forEach(it => {
    if (!form.value.details.find(detail => detail.itemSku.id === it.id)) {
      form.value.details.unshift(
        {
          itemSku: it.itemSku,
          item: it.item,
          skuId: it.id,
          warehouseId: form.value.warehouseId,
          inventoryId: null,
          quantity: 0,
          checkQuantity: 0,
          newInventory: true
        })
    }
  })
}

const showSkuSelect = () => {
  skuSelectRef.value.getList()
  skuSelectShow.value = true
}
// 选择商品 end

// 初始化receipt-order-form ref
const checkForm = ref()

const save = async () => {
  await proxy?.$modal.confirm('确认暂存盘库单吗？');
  doSave()
}
const getParams = (orderStatus) => {
  let details = []
  if (form.value.details?.length) {
    // 构建参数
    details = form.value.details.map(it => {
      return {
        id: it.id,
        orderId: form.value.id,
        skuId: it.skuId,
        quantity: it.quantity,
        checkQuantity: it.checkQuantity,
        inventoryId: it.inventoryId,
        warehouseId: form.value.warehouseId,
      }
    })
  }
  return  {
    id: form.value.id,
    orderNo: form.value.orderNo,
    orderStatus,
    remark: form.value.remark,
    totalQuantity: form.value.totalQuantity,
    warehouseId: form.value.warehouseId,
    details: details
  }
}
const doSave = (orderStatus = 0) => {
  //验证shipmentForm表单
  checkForm.value?.validate((valid) => {
    // 校验
    if (!valid) {
      return ElMessage.error('请填写必填项')
    }
    const params = getParams(orderStatus);
    loading.value = true
    if (params.id) {
      updateCheckOrder(params).then((res) => {
        if (res.code === 200) {
          ElMessage.success(res.msg)
          close()
        } else {
          ElMessage.error(res.msg)
        }
      }).finally(() => {
        loading.value = false
      })
    } else {
      addCheckOrder(params).then((res) => {
        if (res.code === 200) {
          ElMessage.success(res.msg)
          close()
        } else {
          ElMessage.error(res.msg)
        }
      }).finally(() => {
        loading.value = false
      })
    }
  })
}


const updateToInvalid = async () => {
  await proxy?.$modal.confirm('确认作废盘库单吗？');
  doSave(-1)
}

const doCheck = async () => {
  await proxy?.$modal.confirm('确认盘库结束吗？');
  checkForm.value?.validate((valid) => {
    // 校验
    if (!valid) {
      return ElMessage.error('请填写必填项')
    }
    loading.value = true
    const params = getParams(1);
    check(params).then((res) => {
      if (res.code === 200) {
        ElMessage.success('盘库成功')
        close()
      } else {
        ElMessage.error(res.msg)
      }
    }).finally(() => {
      loading.value = false
    })
  })
}

const route = useRoute();
onMounted(() => {
  const id = route.query && route.query.id;
  if (id) {
    checking.value = true
    loadDetail(id)
  } else if (route.query && route.query.fromAi) {
    prefillFromAiDraft()
  } else {
    form.value.orderNo = 'PK' + generateNo()
  }
})

/** 由 AI 草稿预填盘点单：账面数、实盘数都带进来，用户核对后再保存 */
const prefillFromAiDraft = () => {
  let draft = null
  try {
    draft = JSON.parse(sessionStorage.getItem('wms_ai_check_draft') || 'null')
  } catch (e) { /* ignore */ }
  sessionStorage.removeItem('wms_ai_check_draft')

  form.value.orderNo = 'PK' + generateNo()
  if (!draft || draft.type !== 'check') {
    return
  }
  if (draft.warehouseId) form.value.warehouseId = String(draft.warehouseId)
  if (draft.remark) form.value.remark = draft.remark
  form.value.details = (draft.details || []).filter(d => d.skuId).map(d => ({
    itemSku: d.itemSku || {},
    item: d.item || {},
    location: d.location,
    inventoryId: d.inventoryId != null ? String(d.inventoryId) : null,
    skuId: String(d.skuId),
    warehouseId: form.value.warehouseId,
    quantity: Number(d.quantity || 0),
    checkQuantity: Number(d.checkQuantity || 0),
    newInventory: !d.inventoryId
  }))
  selectedSku.value = form.value.details.map(d => ({ id: d.skuId }))
  selectedInventory.value = form.value.details.filter(d => d.inventoryId).map(d => ({ skuId: d.skuId, warehouseId: d.warehouseId }))
  handleChangeQuantity()

  const tips = [...(draft.warnings || [])]
  ;(draft.unresolved || []).forEach(u => tips.push(`未匹配到商品：${u.name}（需手动添加）`))
  if (tips.length) {
    ElMessage.warning({ message: 'AI 草稿提示：\n' + tips.join('\n'), duration: 8000 })
  } else {
    ElMessage.success('已根据 AI 草稿预填账面数和实盘数，请核对后保存')
  }
}


// 获取入库单详情
const loadDetail = (id) => {
  loading.value = true
  getCheckOrder(id).then((response) => {
    if (response.data.details?.length) {
      response.data.details.forEach(detail => {
        detail.newInventory = !detail.inventoryId
      })
      selectedSku.value = response.data.details.map(it => {
        return {
          id: it.skuId
        }
      })
      selectedInventory.value = response.data.details
        .filter(it => it.inventoryId)
        .map(it => {
          return {
            skuId: it.skuId,
            warehouseId: it.warehouseId
          }
        })
    }
    form.value = {...response.data}
    Promise.resolve();
  }).then(() => {
  }).finally(() => {
    loading.value = false
  })
}

const handleDeleteDetail = (row, index) => {
  if (row.id) {
    proxy.$modal.confirm('确认删除本条商品明细吗？如确认会立即执行！').then(function () {
      return delCheckOrderDetail(row.id);
    }).then(() => {
      form.value.details.splice(index, 1)
      proxy.$modal.msgSuccess("删除成功");
    })
  } else {
    form.value.details.splice(index, 1)
  }
  const indexOfSelected = selectedSku.value.findIndex(it => row.itemSku.id === it.id)
  if (indexOfSelected !== -1) {
    selectedSku.value.splice(indexOfSelected, 1)
  }
  const indexOfInventory = selectedInventory.value.findIndex(it => getWarehouseAndSkuKey(it) === getWarehouseAndSkuKey(row))
  if (indexOfInventory !== -1) {
    selectedInventory.value.splice(indexOfInventory, 1)
  }
}

const handleChangeQuantity = () => {
  let totalQuantity = 0
  form.value.details.forEach(it => {
    if (it.quantity !== it.checkQuantity) {
      totalQuantity += (it.checkQuantity - it.quantity)
    }
  })
  form.value.totalQuantity = totalQuantity
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
.hover-text {
  color: black; /* 初始文字颜色 */
  text-decoration: none; /* 初始没有下划线 */
  transition: color 0.3s, text-decoration 0.3s; /* 平滑过渡效果 */
}

.hover-text:hover {
  color: #409EFF; /* 鼠标移上去时文字颜色变为蓝色 */
  text-decoration: underline; /* 鼠标移上去时带有下划线 */
}
</style>
