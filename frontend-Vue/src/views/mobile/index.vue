<template>
  <div class="mobile-wms">
    <header>
      <div class="type-tabs">
        <button :class="{ active: type === 'price' }" @click="switchType('price')">查价格</button>
        <button :class="{ active: type === 'receipt' }" @click="switchType('receipt')">入库</button>
        <button :class="{ active: type === 'shipment' }" @click="switchType('shipment')">出库</button>
        <button @click="goAi">AI助手</button>
      </div>
      <el-dropdown @command="handleCommand">
        <img :src="userStore.avatar" class="avatar" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="setting">布局设置</el-dropdown-item>
            <el-dropdown-item command="desktop">电脑版</el-dropdown-item>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <settings ref="settingRef" />

    <main>
      <section class="page-title">
        <div>
          <small>{{ pageMeta.eyebrow }}</small>
          <h1>{{ pageMeta.title }}</h1>
        </div>
        <el-button v-if="isOrderMode" type="primary" round size="large" @click="createOrder">
          <el-icon><Plus /></el-icon>新建{{ typeText }}
        </el-button>
        <el-button
          v-else
          class="price-secret-toggle"
          :class="{ active: showCostPrice }"
          circle
          :icon="showCostPrice ? StarFilled : Star"
          aria-label="敏感价格"
          @click="showCostPrice = !showCostPrice"
        />
      </section>

      <section class="search-box">
        <el-input
          ref="searchRef"
          v-model="keyword"
          size="large"
          clearable
          :placeholder="searchPlaceholder"
          @keyup.enter="search"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" size="large" @click="search">搜索</el-button>
      </section>

      <div v-if="isOrderMode" class="status-tabs">
        <button
          v-for="item in statuses"
          :key="item.value"
          :class="{ active: status === item.value }"
          @click="changeStatus(item.value)"
        >{{ item.label }}</button>
      </div>

      <section v-if="type === 'price'" v-loading="loading" class="results">
        <article v-for="row in inventoryList" :key="row.id" class="price-card">
          <div class="price-card__head">
            <div class="price-card__title">
              <strong>{{ row.item?.itemName || '-' }}</strong>
              <small v-if="row.item?.itemCode">商品编号：{{ row.item.itemCode }}</small>
            </div>
            <div class="stock-pill" :class="stockClass(row.quantity)">
              <span class="stock-status">{{ stockText(row.quantity) }}</span>
              <i></i>
              <strong>{{ Math.floor(Number(row.quantity || 0)) }}</strong>
              <span class="stock-unit">件</span>
            </div>
          </div>
          <div class="sku-line">
            <span>{{ row.itemSku?.skuName || '-' }}</span>
            <small v-if="row.itemSku?.skuCode">规格编号：{{ row.itemSku.skuCode }}</small>
          </div>
          <div class="price-grid" :class="{ 'with-cost': showCostPrice }">
            <div class="sale-price-cell">
              <small>售价</small>
              <strong>{{ formatPrice(row.itemSku?.sellingPrice) }}</strong>
            </div>
            <div v-if="showCostPrice" class="cost-price-cell">
              <small>成本</small>
              <strong>{{ formatPrice(row.itemSku?.costPrice) }}</strong>
            </div>
            <div class="location-cell">
              <small>位置</small>
              <strong>{{ row.location?.locationCode || '-' }}</strong>
            </div>
            <div class="warehouse-cell">
              <small>仓库</small>
              <strong>{{ warehouseName(row.warehouseId) }}</strong>
            </div>
          </div>
        </article>
        <el-empty v-if="!loading && !inventoryList.length" description="输入商品名、规格名或编号查价格" />
      </section>

      <section v-else v-loading="loading" class="results">
        <article v-for="order in orders" :key="order.id" class="order-card">
          <div class="order-card__head">
            <div><strong>{{ order.orderNo }}</strong><small v-if="order.bizOrderNo">{{ order.bizOrderNo }}</small></div>
            <dict-tag :options="statusDict" :value="order.orderStatus" />
          </div>
          <div class="order-info">
            <div><small>仓库</small><strong>{{ warehouseName(order.warehouseId) }}</strong></div>
            <div><small>{{ type === 'receipt' ? '供应商' : '客户' }}</small><strong>{{ merchantName(order.merchantId) }}</strong></div>
            <div><small>数量</small><strong>{{ Number(order.totalQuantity || 0).toFixed(0) }}</strong></div>
            <div><small>金额</small><strong>{{ order.totalAmount ?? '-' }}</strong></div>
          </div>
          <div class="actions">
            <el-button size="large" @click="toggleDetail(order)">{{ expandedId === order.id ? '收起' : '详情' }}</el-button>
            <el-button type="primary" size="large" :disabled="[-1, 1].includes(order.orderStatus)" @click="continueOrder(order)">
              继续{{ typeText }}
            </el-button>
          </div>
          <div v-if="expandedId === order.id" v-loading="detailLoading" class="details">
            <div v-for="detail in order.details || []" :key="detail.id" class="product">
              <div><strong>{{ detail.item?.itemName || '-' }}</strong><small>{{ detail.itemSku?.skuName || '-' }}</small></div>
              <b>× {{ Number(detail.quantity || 0).toFixed(0) }}</b>
            </div>
            <order-image-gallery :image-ids="order.supplementImageIds" />
            <el-empty v-if="!order.details?.length" description="暂无商品明细" :image-size="50" />
          </div>
        </article>
        <el-empty v-if="!loading && !orders.length" description="没有找到相关单据">
          <el-button type="primary" @click="createOrder">立即新建</el-button>
        </el-empty>
      </section>
    </main>
  </div>
</template>

<script setup name="MobileWarehouse">
import { ElMessageBox } from "element-plus";
import { Plus, Search, Star, StarFilled } from "@element-plus/icons-vue";
import { listInventoryBoard } from "@/api/wms/inventory";
import { getReceiptOrder, listReceiptOrder } from "@/api/wms/receiptOrder";
import { getShipmentOrder, listShipmentOrder } from "@/api/wms/shipmentOrder";
import OrderImageGallery from "@/components/OrderImageGallery";
import Settings from "@/layout/components/Settings";
import useUserStore from "@/store/modules/user";
import { useWmsStore } from "@/store/modules/wms";

const router = useRouter();

const goAi = () => router.push('/wms/ai');
const route = useRoute();
const userStore = useUserStore();
const wmsStore = useWmsStore();
const { proxy } = getCurrentInstance();
const { wms_receipt_status, wms_shipment_status } = proxy.useDict("wms_receipt_status", "wms_shipment_status");
const type = ref("price");
const keyword = ref("");
const status = ref(-2);
const orders = ref([]);
const inventoryList = ref([]);
const showCostPrice = ref(false);
const loading = ref(false);
const detailLoading = ref(false);
const expandedId = ref(null);
const searchRef = ref(null);
const settingRef = ref(null);
const typeText = computed(() => type.value === "receipt" ? "入库" : "出库");
const isOrderMode = computed(() => type.value === "receipt" || type.value === "shipment");
const pageMeta = computed(() => {
  if (type.value === "price") {
    return { eyebrow: "快速查价格", title: "搜索商品售价和库存" };
  }
  return { eyebrow: `快速${typeText.value}`, title: `搜索和处理${typeText.value}单` };
});
const searchPlaceholder = computed(() => type.value === "price" ? "输入商品名、规格名、商品编号" : `输入${typeText.value}单号或业务单号`);
const statusDict = computed(() => type.value === "receipt" ? wms_receipt_status.value : wms_shipment_status.value);
const statuses = computed(() => [{ label: "全部", value: -2 }, ...statusDict.value.map(it => ({ label: it.label, value: it.value }))]);
const warehouseName = id => wmsStore.warehouseMap.get(id)?.warehouseName || "-";
const merchantName = id => wmsStore.merchantMap.get(id)?.merchantName || "-";
const formatPrice = value => {
  if (value === undefined || value === null || value === "") return "暂无价格";
  return `￥${Number(value).toFixed(3)}`;
};
const stockClass = value => {
  const quantity = Number(value || 0);
  if (quantity === 0) return "empty";
  if (quantity <= 5) return "low";
  if (quantity <= 10) return "warn";
  return "safe";
};
const stockText = value => {
  const quantity = Number(value || 0);
  if (quantity === 0) return "无库存";
  if (quantity <= 5) return "偏低";
  if (quantity <= 10) return "需关注";
  return "充足";
};

function switchType(value) {
  type.value = value;
  router.replace({ path: "/mobile", query: { type: value } });
  keyword.value = "";
  status.value = -2;
  expandedId.value = null;
  orders.value = [];
  inventoryList.value = [];
  search();
  nextTick(() => searchRef.value?.focus());
}

function changeStatus(value) {
  status.value = value;
  search();
}

async function search() {
  loading.value = true;
  expandedId.value = null;
  if (type.value === "price") {
    const params = { pageNum: 1, pageSize: 30, itemKeywords: keyword.value || undefined };
    try {
      const response = await listInventoryBoard(params, "item");
      inventoryList.value = response.rows || [];
    } finally {
      loading.value = false;
    }
    return;
  }
  const params = { pageNum: 1, pageSize: 30, keyword: keyword.value || undefined, orderStatus: status.value === -2 ? undefined : status.value };
  try {
    const response = type.value === "receipt" ? await listReceiptOrder(params) : await listShipmentOrder(params);
    orders.value = response.rows || [];
  } finally {
    loading.value = false;
  }
}

function createOrder() {
  if (!isOrderMode.value) return;
  router.push(type.value === "receipt" ? "/receiptOrderEdit" : "/shipmentOrderEdit");
}

function continueOrder(order) {
  if (!isOrderMode.value) return;
  router.push({ path: type.value === "receipt" ? "/receiptOrderEdit" : "/shipmentOrderEdit", query: { id: order.id } });
}

async function toggleDetail(order) {
  if (expandedId.value === order.id) {
    expandedId.value = null;
    return;
  }
  expandedId.value = order.id;
  if (order.details) return;
  detailLoading.value = true;
  try {
    const response = type.value === "receipt" ? await getReceiptOrder(order.id) : await getShipmentOrder(order.id);
    Object.assign(order, response.data);
  } finally {
    detailLoading.value = false;
  }
}

function handleCommand(command) {
  if (command === "setting") {
    settingRef.value.openSetting();
  } else if (command === "desktop") {
    router.push("/index?desktop=1");
  } else if (command === "logout") {
    ElMessageBox.confirm("确定退出登录吗？", "提示", { confirmButtonText: "退出", cancelButtonText: "取消", type: "warning" })
      .then(() => userStore.logOut())
      .then(() => { location.href = import.meta.env.VITE_APP_CONTEXT_PATH + "login"; })
      .catch(() => {});
  }
}

onMounted(() => {
  if (["price", "inventory", "receipt", "shipment"].includes(route.query.type)) {
    type.value = route.query.type === "inventory" ? "price" : route.query.type;
  }
  search();
  nextTick(() => searchRef.value?.focus());
});
</script>

<style scoped lang="scss">
.mobile-wms { min-height: 100vh; color: #1f2937; background: #f3f6fa; }
header { position: sticky; z-index: 20; top: 0; display: flex; align-items: center; justify-content: space-between; height: calc(58px + env(safe-area-inset-top)); padding: env(safe-area-inset-top) 14px 0; background: rgba(255,255,255,.96); border-bottom: 1px solid #e8edf3; backdrop-filter: blur(12px); }
.type-tabs { display: flex; flex: 1; gap: 4px; min-width: 0; padding: 4px; background: #eef3f8; border-radius: 12px; }
.type-tabs button { flex: 1; min-width: 0; padding: 8px 7px; color: #667085; background: transparent; border: 0; border-radius: 9px; font-size: 14px; font-weight: 700; white-space: nowrap; }
.type-tabs button.active { color: #fff; background: var(--el-color-primary); box-shadow: 0 4px 12px rgba(64,158,255,.28); }
.avatar { width: 38px; height: 38px; object-fit: cover; border-radius: 50%; }
main { padding: 18px 12px calc(28px + env(safe-area-inset-bottom)); }
.page-title { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 16px; }
.page-title small { color: var(--el-color-primary); font-weight: 700; }
.page-title h1 { margin: 4px 0 0; font-size: 21px; }
.price-secret-toggle { flex: 0 0 auto; color: #c7a03b; background: #fff7df; border-color: #ffe5a3; box-shadow: 0 6px 16px rgba(199,160,59,.16); }
.price-secret-toggle.active { color: #fff; background: #d6a51d; border-color: #d6a51d; }
.search-box { display: flex; gap: 8px; padding: 12px; background: #fff; border-radius: 14px; box-shadow: 0 6px 22px rgba(31,45,61,.07); }
.search-box .el-input { flex: 1; }
.status-tabs { display: flex; gap: 8px; margin: 14px 0; overflow-x: auto; }
.status-tabs button { flex: 0 0 auto; padding: 8px 14px; color: #667085; background: #fff; border: 1px solid #e3e9f0; border-radius: 999px; }
.status-tabs button.active { color: var(--el-color-primary); background: var(--el-color-primary-light-9); border-color: var(--el-color-primary-light-5); }
.results { min-height: 220px; }
.order-card { margin-bottom: 12px; padding: 14px; background: #fff; border-radius: 14px; box-shadow: 0 4px 18px rgba(31,45,61,.06); }
.order-card__head,.actions,.product { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.order-card__head > div,.product > div { display: grid; gap: 3px; }
.order-card__head strong { font-size: 16px; }
.order-card small,.product small { color: #98a2b3; font-size: 12px; }
.order-info { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 14px 0; }
.order-info div { display: grid; gap: 3px; min-width: 0; }
.order-info strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.actions .el-button { flex: 1; margin-left: 0; }
.details { margin-top: 14px; padding-top: 12px; border-top: 1px dashed #dfe5ec; }
.product { padding: 9px 0; border-bottom: 1px solid #f0f2f5; }
.price-card { margin-bottom: 10px; padding: 12px; background: #fff; border-radius: 14px; box-shadow: 0 4px 18px rgba(31,45,61,.06); }
.price-card__head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.price-card__title { display: grid; gap: 3px; min-width: 0; }
.price-card__head strong { overflow: hidden; color: #111827; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.price-card small,.sku-line small,.price-grid small { color: #98a2b3; font-size: 12px; }
.stock-pill { display: inline-flex; align-items: center; justify-content: center; gap: 5px; flex: 0 0 auto; min-width: 104px; height: 34px; padding: 0 11px; color: inherit; white-space: nowrap; border-radius: 999px; border: 1px solid transparent; }
.stock-pill .stock-status { color: inherit; font-size: 12px; font-weight: 800; }
.stock-pill i { display: block; width: 3px; height: 3px; background: currentColor; border-radius: 50%; opacity: .55; }
.stock-pill strong { color: inherit; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; font-size: 18px; line-height: 1; }
.stock-pill .stock-unit { color: inherit; font-size: 12px; font-weight: 700; opacity: .82; }
.stock-pill.safe { color: #078669; background: #e9fbf5; border-color: #b8efdf; }
.stock-pill.warn { color: #b77905; background: #fff8e6; border-color: #ffe2a4; }
.stock-pill.low { color: #c2410c; background: #fff1e8; border-color: #ffc9a8; }
.stock-pill.empty { color: #64748b; background: #f1f5f9; border-color: #e2e8f0; }
.sku-line { display: grid; gap: 3px; margin: 10px 0; padding: 9px 12px; color: #4b5563; background: #f7f9fc; border-radius: 12px; }
.sku-line span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.price-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.price-grid div { display: grid; gap: 3px; min-width: 0; padding: 9px 12px; background: #fbfcfe; border: 1px solid #eef2f7; border-radius: 12px; }
.price-grid strong { overflow: hidden; color: #17233d; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.price-grid .sale-price-cell { background: #effaf8; border-color: #c7eee7; }
.price-grid .sale-price-cell strong { color: #087f75; font-size: 18px; }
.price-grid .warehouse-cell { grid-column: 1 / -1; }
.price-grid.with-cost .warehouse-cell { grid-column: auto; }
.price-grid.with-cost .location-cell { order: 4; }
.price-grid.with-cost .warehouse-cell { order: 3; }
.price-grid .cost-price-cell { background: #fffaf0; border-color: #fde7bd; }
.price-grid .cost-price-cell strong { color: #9a6a05; }
@media (min-width: 769px) { .mobile-wms { max-width: 520px; margin: 0 auto; border-right: 1px solid #e8edf3; border-left: 1px solid #e8edf3; } }
</style>
