<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" label-width="90px" :inline="true" @submit.prevent @keyup.enter="handleQuery">
        <div style="display: flex; justify-content: space-between; ">

          <el-form-item class="col4" label="维度" prop="itemId">
            <el-radio-group v-model="queryType" size="default" @change="handleSortTypeChange">
              <el-radio-button label="item">商品</el-radio-button>
              <el-radio-button label="warehouse">仓库</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="综合搜索" style="flex: 1; margin-left: -100px;">
            <el-input v-model="queryParams.itemKeywords" clearable placeholder="商品/规格名称、编号、条码或位置编码（多个关键字用空格分隔）"  />
          </el-form-item>

          <el-form-item class="col4">
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            <el-button type="text" @click="advancedSearchVisible = !advancedSearchVisible">高级搜索</el-button>
          </el-form-item>
        </div>
        <!-- 高级搜索表单区 -->
        <el-collapse-transition>
          <div v-if="advancedSearchVisible" class="advanced-search">
            <el-form-item class="col4" label="仓库" prop="warehouseId">
              <el-select style="width: 100%" v-model="queryParams.warehouseId" placeholder="请选择仓库" filterable clearable>
                <el-option v-for="item in useWmsStore().warehouseList" :key="item.id" :label="item.warehouseName" :value="item.id"/>
              </el-select>
            </el-form-item>

            <el-form-item class="col4" label="商品名称" prop="itemName">
              <el-input v-model="queryParams.itemName" clearable placeholder="商品名称"></el-input>
            </el-form-item>

            <el-form-item class="col4" label="规格名称" prop="skuName">
              <el-input v-model="queryParams.skuName" clearable placeholder="规格名称"></el-input>
            </el-form-item>

            <el-form-item label="商品位置" prop="itemLocationId">
              <el-select v-model="queryParams.itemLocationId" clearable filterable @keyup.enter.native="handleQuery">
                <el-option
                  v-for="item in useWmsStore().locationList"
                  :key="item.id"
                  :label="item.locationCode + ' (' + item.locationName + ')'"
                  :value="item.id"
                ></el-option>
              </el-select>
            </el-form-item>

            <el-form-item class="col4" label="商品编号" prop="itemCode">
              <el-input v-model="queryParams.itemCode" clearable placeholder="商品编号"></el-input>
            </el-form-item>

            <el-form-item class="col4" label="规格编号" prop="skuCode">
              <el-input v-model="queryParams.skuCode" clearable placeholder="规格编号"></el-input>
            </el-form-item>

            <el-form-item class="col4" label="标签" prop="tagId">
              <el-select style="width: 100%" v-model="queryParams.tagId" placeholder="请选择标签" filterable clearable @change="handleQuery">
                <el-option v-for="tag in useWmsStore().itemTagList" :key="tag.id" :label="tag.tagName" :value="tag.id">
                  <span :style="{ display:'inline-block', width:'10px', height:'10px', borderRadius:'50%', background: tag.color || '#909399', marginRight:'6px', verticalAlign:'middle' }"></span>
                  {{ tag.tagName }}
                </el-option>
              </el-select>
            </el-form-item>

            <el-form-item class="col4" label="库存预警" prop="maxQuantity">
              <el-select style="width: 100%" v-model="queryParams.maxQuantity" placeholder="全部库存" clearable @change="handleQuery">
                <el-option label="缺货（库存 = 0）" :value="0"/>
                <el-option label="低库存（≤ 5）" :value="5"/>
                <el-option label="偏低（≤ 10）" :value="10"/>
                <el-option label="充足（≤ 50）" :value="50"/>
              </el-select>
            </el-form-item>

          </div>
        </el-collapse-transition>

      </el-form>
    </el-card>
    <el-card class="mt20">
      <div class="mb8 flex-space-between">
        <div style="font-size: large; display: flex; align-items: center;">
          <span>库存统计</span>
          <el-tag
            v-if="negativeCount > 0"
            type="danger"
            effect="dark"
            class="negative-hint"
            @click="showNegativeOnly"
          >负库存 {{ negativeCount }} 条待盘点</el-tag>
          <el-button
            type="text"
            :icon="showCostPrice ? 'StarFilled' : 'Star'"
            @click="showCostPrice = !showCostPrice"
            style="margin-left: -8px; margin-top: -24px; color: #f7ba2a"
            circle
          />
        </div>
        <div style="display: flex; align-items: center; gap: 12px;">
          <el-select v-model="queryParams.sortMode" @change="handleQuery" style="width: 150px" placeholder="排序方式">
            <el-option label="默认排序" value=""/>
            <el-option label="库存少 → 多" value="quantityAsc"/>
            <el-option label="库存多 → 少" value="quantityDesc"/>
          </el-select>
          <el-button type="success" icon="Download" @click="handleExport">
            {{ selectedRows.length ? `导出已选（${selectedRows.length} 条）` : `导出当前页（${ inventoryList.length } 条）` }}
          </el-button>
          <el-button v-if="hasSearchCondition" type="warning" plain icon="Download" @click="handleExportAllSearch">
            导出所有搜索结果（{{ total }} 条）
          </el-button>
          <el-checkbox v-model="filterable" label="过滤掉库存为0的商品" size="large" :disabled="!!queryParams.negativeOnly" @change="handleChangeFilterZero"/>
          <el-checkbox v-model="queryParams.negativeOnly" label="只看负库存" size="large" @change="handleChangeNegativeOnly"/>
        </div>
      </div>
      <el-table :data="inventoryList" border stripe :span-method="spanMethod"
                cell-class-name="vertical-top-cell" v-loading="loading" empty-text="暂无库存"
                @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" align="center"/>
        <template v-if="queryType == 'warehouse'">
          <el-table-column label="仓库" prop="warehouseId">
            <template #default="{ row }">
              <div>{{ useWmsStore().warehouseMap.get(row.warehouseId)?.warehouseName }}</div>
            </template>
          </el-table-column>
          <el-table-column label="商品信息" prop="warehouseIdAndItemId">
            <template #default="{ row }">
              <div>{{ row.item.itemName }}</div>
              <div v-if="row.item.itemCode">商品编号：{{ row.item.itemCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="规格信息" :prop="skuId">
            <template #default="{ row }">
              <div>{{ row.itemSku.skuName }}</div>
              <div v-if="row.itemSku.skuCode">规格编号：{{ row.itemSku.skuCode }}</div>
            </template>
          </el-table-column>
        </template>
        <template v-else>
          <el-table-column label="商品信息" prop="itemId">
            <template #default="{ row }">
              <div>{{ row.item.itemName }}</div>
              <div v-if="row.item.itemCode">商品编号：{{ row.item.itemCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="规格信息" prop="skuId">
            <template #default="{ row }">
              <div>{{ row.itemSku.skuName }}</div>
              <div v-if="row.itemSku.skuCode">规格编号：{{ row.itemSku.skuCode }}</div>
            </template>
          </el-table-column>
          <el-table-column label="位置信息" prop="locationId">
            <template #default="{ row }">
              <dict-tag v-if="!row.location"
                :customTags="[
                  { label: '暂无位置', type: 'info' }
                ]"
              />
              <el-popover v-else placement="right" trigger="hover" :width="320"
                          :disabled="!hasShelf(row.location)">
                <template #reference>
                  <div style="cursor: default;">
                    <dict-tag :customTags="[
                      { label: row.location.locationCode, type: 'primary' }
                    ]"/>
                    <div>{{ row.location.locationName }}</div>
                  </div>
                </template>
                <ShelfMap :warehouse-id="shelfLoc(row.location)?.warehouseId" :highlight-id="row.location.id" single/>
              </el-popover>
            </template>
          </el-table-column>

        </template>
        <el-table-column label="单价（售价）" prop="sellingPrice">
          <template #default="{row}">
            <el-statistic v-if="row.itemSku?.sellingPrice" :value="Number(row.itemSku.sellingPrice)" :precision="3"/>
            <dict-tag v-else :customTags="[
              { label: '暂无价格', type: 'info' }
            ]"/>
          </template>
        </el-table-column>
        <el-table-column v-if="showCostPrice" label="单价（进价）" prop="costPrice">
          <template #default="{row}">
            <el-statistic :value="row.itemSku?.costPrice ? Number(row.itemSku.costPrice) : '暂无价格'" :precision="3"/>
          </template>
        </el-table-column>
        <!-- <el-table-column label="库存" prop="quantity">
          <template #default="{ row }">
            <el-statistic :value="Number(row.quantity)" :precision="0"/>
          </template>
        </el-table-column> -->
        <el-table-column label="库存" prop="quantity" align="center">
          <template #default="{ row }">
            <el-tag
              class="inventory-tag"
              :class="{ 'negative-quantity': row.quantity < 0 }"
              :type="row.quantity == 0 
                        ? 'info' 
                        : row.quantity <= 5 
                          ? 'danger' 
                          : row.quantity <= 10 
                            ? 'warning' 
                            : 'success'"
              effect="dark"
            >
              {{ formatQuantity(row.quantity) }}
            </el-tag>
            <div v-if="row.quantity < 0" class="negative-note">待盘点补正</div>
          </template>
        </el-table-column>
        <el-table-column label="仓库" prop="skuIdAndWarehouseId" align="right" min-width="100%">
          <template #default="{row}">
            <div>{{ useWmsStore().warehouseMap.get(row.warehouseId)?.warehouseName }}</div>
          </template>
        </el-table-column>
          
      </el-table>

      <el-row>
        <pagination v-show="total>0" :total="total" v-model:page="queryParams.pageNum"
                    v-model:limit="queryParams.pageSize" @pagination="getList"/>
      </el-row>
    </el-card>
  </div>
</template>

<script setup name="Inventory">
import {
  listInventoryBoard,
  getNegativeCount
} from '@/api/wms/inventory';
import {computed, getCurrentInstance, onMounted, onBeforeUnmount, ref} from 'vue';
import {ElForm, ElMessage, ElMessageBox} from 'element-plus';
import {getRowspanMethod} from "@/utils/getRowSpanMethod";
import {useWmsStore} from '@/store/modules/wms'
import ShelfMap from '@/views/components/ShelfMap.vue'
import {parseLocationCode} from '@/utils/shelf'

/** 从 store 取该位置的完整信息（用于拿到所属仓库） */
const shelfLoc = (loc) => loc ? useWmsStore().locationMap.get(loc.id) : null
/** 位置编码能否解析出货架坐标 */
const hasShelf = (loc) => !!(loc && parseLocationCode(loc.locationCode))

const {proxy} = getCurrentInstance();
const spanMethod = computed(() => getRowspanMethod(inventoryList.value, rowSpanArray.value))

const inventoryList = ref([]);
const buttonLoading = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);
const rowSpanArray = ref(['itemId', 'skuId','skuIdAndWarehouseId'])
const EXPORT_WARNING_THRESHOLD = 1000

const showCostPrice = ref(false);

const selectedRows = ref([]);

const advancedSearchVisible = ref(false);
const searchedCondition = ref(null);

const filterable = ref(false)
const queryType = ref("item")
const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  skuId: undefined,
  warehouseId: undefined,
  itemName: undefined,
  itemCode: undefined,
  skuName: undefined,
  skuCode: undefined,
  itemLocationId: undefined,
  minQuantity: undefined,
  maxQuantity: undefined, // 库存上限（缺货/低库存预警）
  tagId: undefined,       // 标签筛选
  negativeOnly: false,    // 只看负库存（出库时欠下、等待盘点补正的记录）
  sortMode: '',           // 排序方式：''默认 / quantityAsc / quantityDesc
  itemKeywords: undefined, // 新增关键字搜索
})

const hasSearchCondition = computed(() => {
  return searchedCondition.value?.hasCondition === true
})

const detectSearchCondition = (params, filterZero) => {
  return Boolean(filterZero)
    || Boolean(String(params.itemKeywords || '').trim())
    || Boolean(params.warehouseId)
    || Boolean(String(params.itemName || '').trim())
    || Boolean(String(params.itemCode || '').trim())
    || Boolean(String(params.skuName || '').trim())
    || Boolean(String(params.skuCode || '').trim())
    || Boolean(params.itemLocationId)
    || Boolean(params.tagId)
    || params.maxQuantity != null
    || Boolean(params.negativeOnly)
}

/** 查询库存列表 */
const getList = async () => {
  let query = {...queryParams.value}
  // 只看负库存时不能再套用「库存 >= 1」，两个条件是互斥的
  if (filterable.value && !query.negativeOnly) {
    query.minQuantity = 1
  } else {
    query.minQuantity = undefined
  }
  loading.value = true;
  const res = await listInventoryBoard(query,queryType.value);
  inventoryList.value = res.rows;
  inventoryList.value.forEach(it => {
    if (queryType.value == "warehouse") {
      it.warehouseIdAndItemId = it.warehouseId + '-' + it.item.id
    } else if (queryType.value == "item") {
      it.itemId = it.item.id
      it.skuIdAndWarehouseId = it.skuId + '-' + it.warehouseId
    }
  })
  total.value = res.total;
  loading.value = false;
}

/** 表格勾选变化 */
const handleSelectionChange = (rows) => {
  selectedRows.value = rows;
}

const buildExportQuery = (params = queryParams.value, filterZero = filterable.value) => {
  const query = { ...params }
  query.minQuantity = (filterZero && !query.negativeOnly) ? 1 : undefined
  // 导出无需分页
  delete query.pageNum
  delete query.pageSize
  return query
}

/** 导出Excel：勾选则导出勾选项，否则导出当前页 */
const handleExport = () => {
  const query = buildExportQuery()
  if (selectedRows.value.length > 0) {
    query.ids = selectedRows.value.map(it => it.id).join(',')
  } else {
    if (!inventoryList.value.length) {
      ElMessage.warning('当前页没有可导出的库存数据')
      return
    }
    query.ids = inventoryList.value.map(it => it.id).join(',')
  }
  proxy.download('wms/inventory/export', query, `库存_${new Date().getTime()}.xlsx`)
}

/** 导出所有搜索结果：按搜索条件导出，不受当前页和勾选限制 */
const handleExportAllSearch = async () => {
  const query = buildExportQuery(searchedCondition.value?.params || queryParams.value, searchedCondition.value?.filterable ?? filterable.value)
  if (Number(total.value || 0) > EXPORT_WARNING_THRESHOLD) {
    await ElMessageBox.confirm(
      `当前搜索结果共有 ${total.value} 条，导出可能会比较慢，确定继续导出全部搜索结果吗？`,
      '导出数量较多',
      {
        confirmButtonText: '继续导出',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  }
  proxy.download('wms/inventory/export', query, `库存_搜索结果_${new Date().getTime()}.xlsx`)
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  searchedCondition.value = {
    hasCondition: detectSearchCondition(queryParams.value, filterable.value),
    params: { ...queryParams.value },
    filterable: filterable.value
  }
  getList();
}

/** 重置按钮操作 */
const resetQuery = () => {
  proxy.resetForm("queryRef");
  // 综合搜索输入框及工具栏排序无 prop，resetForm 不会清空，需手动重置
  queryParams.value.itemKeywords = undefined;
  queryParams.value.maxQuantity = undefined;
  queryParams.value.tagId = undefined;
  queryParams.value.sortMode = '';
  queryParams.value.negativeOnly = false;
  searchedCondition.value = null;
  handleQuery();
}
const calcSubtotal = (row) => {
  const tempList = inventoryList.value.filter(it => it.itemId === row.itemId)
  let sum = 0
  tempList.forEach(it => {
    sum += Number(it.quantity)
  })
  return sum
}

const handleSortTypeChange = (e) => {
  if (e === "warehouse") {
    rowSpanArray.value = ['warehouseId', 'warehouseIdAndItemId']
  }  else if (e === "item") {
    rowSpanArray.value = ['itemId', 'skuId','skuIdAndWarehouseId']
  }
  queryParams.value.pageNum = 1;
  searchedCondition.value = null;
  getList()
}

/** 只看负库存：与「过滤掉库存为0」互斥 */
const handleChangeNegativeOnly = () => {
  if (queryParams.value.negativeOnly) {
    filterable.value = false
  }
  handleQuery()
}

/** 点击顶部提示直接筛出负库存 */
const showNegativeOnly = () => {
  queryParams.value.negativeOnly = true
  filterable.value = false
  handleQuery()
}

/** 负数按原值展示（Math.floor 会把 -2.5 变成 -3） */
const formatQuantity = (quantity) => {
  const num = Number(quantity)
  if (!Number.isFinite(num)) {
    return quantity
  }
  return num < 0 ? Number(num.toFixed(2)) : Math.floor(num)
}

const handleChangeFilterZero = (e) => {
  queryParams.value.pageNum = 1;
  searchedCondition.value = {
    hasCondition: detectSearchCondition(queryParams.value, filterable.value),
    params: { ...queryParams.value },
    filterable: filterable.value
  }
  getList()
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

const negativeCount = ref(0)
const loadNegativeCount = async () => {
  try {
    const res = await getNegativeCount()
    negativeCount.value = Number(res.data || 0)
  } catch (e) { /* 提示用，失败不打扰 */ }
}

onMounted(() => {
  getList();
  loadNegativeCount();
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>
<style>
.el-statistic__content {
  font-size: 14px;
}
.el-table .vertical-top-cell {
  vertical-align: top
}
.negative-hint {
  margin-left: 10px;
  cursor: pointer;
}
.negative-quantity {
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #f56c6c;
}
.negative-note {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 2px;
}
.inventory-tag {
  font-size: 20px;
  font-weight: bold;
  padding: 12px 6px;
  font-family: monospace; /* 等宽字体 */
  min-width: 50px; /* 固定宽度，数字看起来更整齐 */
  text-align: center;
  justify-content: center;
  display: inline-flex;
}
</style>
