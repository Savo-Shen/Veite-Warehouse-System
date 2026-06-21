<template>
  <div class="app-container">
    <!-- 搜索 -->
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="70px" @submit.prevent @keyup.enter="handleQuery">
        <el-form-item label="位置名称" prop="keywords">
          <el-input
            v-model="queryParams.keywords"
            placeholder="请输入位置编码/名称"
            clearable
          />
        </el-form-item>
        <el-form-item label="所属仓库" prop="warehouseId">
          <el-select v-model="queryParams.warehouseId" clearable filterable placeholder="全部仓库" @change="handleQuery" style="width: 180px;">
            <el-option v-for="w in useWmsStore().warehouseList" :key="w.id" :label="w.warehouseName" :value="w.id"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 货架示意图（中间，3D 可拖拽排布） -->
    <el-card class="mt20">
      <div class="mb8" style="display:flex; align-items:center; gap:12px; flex-wrap: wrap;">
        <span style="font-size: large">货架示意图</span>
        <el-select v-model="mapWarehouseId" filterable placeholder="选择仓库" style="width: 220px;">
          <el-option v-for="w in useWmsStore().warehouseList" :key="w.id" :label="w.warehouseName" :value="w.id"/>
        </el-select>
        <span v-if="hoverInfo" style="color:#f56c6c;">
          当前：{{ hoverInfo.locationCode }} {{ hoverInfo.locationName }}
          <template v-if="parseCoord(hoverInfo.locationCode)">
            （{{ parseCoord(hoverInfo.locationCode).floor }}楼 {{ parseCoord(hoverInfo.locationCode).row }}排
            {{ parseCoord(hoverInfo.locationCode).col }}列 {{ parseCoord(hoverInfo.locationCode).cell }}格）
          </template>
        </span>
        <span v-else style="color:#909399;">鼠标移到下方列表的某一行，或图中货位，即可高亮对照</span>
      </div>
      <ShelfMap :warehouse-id="mapWarehouseId" :highlight-id="highlightId" editable @cell-hover="onMapHover"/>
    </el-card>

    <el-card class="mt20">

      <el-row :gutter="10" class="mb8" type="flex" justify="space-between">
        <el-col :span="6"><span style="font-size: large">位置列表</span></el-col>
        <el-col :span="1.5">
          <el-button
            type="primary"
            plain
            icon="Plus"
            @click="handleAdd"
            v-hasPermi="['wms:location:edit']"
          >新增</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="locationList" border stripe class="mt20" empty-text="暂无位置"
                highlight-current-row @cell-mouse-enter="onRowHover">
        <el-table-column label="位置编码" prop="locationCode" />
        <el-table-column label="位置名称" prop="locationName" />
        <el-table-column label="所属仓库" prop="warehouseId">
          <template #default="{ row }">
            {{ useWmsStore().warehouseMap.get(row.warehouseId)?.warehouseName || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="货架坐标" width="180">
          <template #default="{ row }">
            <el-tag v-if="parseCoord(row.locationCode)" type="success" effect="plain">
              {{ parseCoord(row.locationCode).floor }}楼 · {{ parseCoord(row.locationCode).row }}排 ·
              {{ parseCoord(row.locationCode).col }}列 · {{ parseCoord(row.locationCode).cell }}格
            </el-tag>
            <span v-else style="color:#c0c4cc">编码非货架格式</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" show-overflow-tooltip/>
        <el-table-column label="操作" align="right" class-name="small-padding fixed-width" width="180">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wms:location:edit']">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wms:location:edit']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

    </el-card>

    <!-- 添加或修改商品位置对话框 -->
    <el-drawer :title="title" v-model="open" size="50%" append-to-body>
      <el-form ref="locationRef" :model="form" :rules="rules" label-width="90px" @submit.prevent @keyup.enter="submitForm">
        <el-form-item label="位置编码" prop="locationCode">
          <el-input v-model="form.locationCode" placeholder="如 2-B2-3（楼层-排列-格）" />
        </el-form-item>
        <el-form-item label="位置名称" prop="locationName">
          <el-input v-model="form.locationName" placeholder="请输入位置名称" />
        </el-form-item>
        <el-form-item label="所属仓库" prop="warehouseId">
          <el-select v-model="form.warehouseId" clearable filterable placeholder="请选择仓库" style="width: 100%;">
            <el-option v-for="w in useWmsStore().warehouseList" :key="w.id" :label="w.warehouseName" :value="w.id"/>
          </el-select>
        </el-form-item>
        <el-alert
          type="info" :closable="false" show-icon
          title="位置编码即货架坐标，格式 楼层-排列-格，例如 2-B2-3 = 2楼/B排/第2列/第3格。按此格式填写后，该货位会自动出现在货架示意图上。"
          style="margin-bottom: 16px;"
        />
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
  </div>
</template>

<script setup name="Location">
import { listLocationNoPage, listLocation, getLocation, addLocation, updateLocation, delLocation } from "@/api/wms/location";
import {ElMessageBox} from "element-plus";
import {useWmsStore} from '@/store/modules/wms'
import ShelfMap from '@/views/components/ShelfMap.vue'
import {parseLocationCode} from '@/utils/shelf'

const { proxy } = getCurrentInstance();

/** 解析位置编码为货架坐标 */
const parseCoord = (code) => parseLocationCode(code)

const locationList = ref([]);
const open = ref(false);
const buttonLoading = ref(false);
const loading = ref(true);
const ids = ref([]);
const total = ref(0);
const title = ref("");

// 货架示意图相关
const mapWarehouseId = ref(undefined);
const highlightId = ref(undefined);
const hoverInfo = ref(null);

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    locationName: undefined,
    locationCode: undefined,
    remark: undefined,
    keywords: undefined,
    warehouseId: undefined,
  },
  rules: {
    locationName: [
      { required: true, message: "位置名称不能为空", trigger: "blur" }
    ],
    locationCode: [
      { required: true, message: "位置编号不能为空", trigger: "blur" }
    ]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询商品位置列表 */
async function getList() {
  loading.value = true;
  const store = useWmsStore()
  await store.getLocationList()

  let list = [...store.locationList]
  if (queryParams.value.keywords) {
    list = list.filter(it => it.locationName.includes(queryParams.value.keywords) || it.locationCode.includes(queryParams.value.keywords) || (it.remark && it.remark.includes(queryParams.value.keywords)))
  }
  if (queryParams.value.warehouseId) {
    list = list.filter(it => it.warehouseId === queryParams.value.warehouseId)
  }
  locationList.value = list;
  // 默认货架图仓库：优先用筛选的仓库，否则第一个仓库
  if (!mapWarehouseId.value) {
    mapWarehouseId.value = queryParams.value.warehouseId || store.warehouseList?.[0]?.id;
  }
  loading.value = false;
}

/** 列表行 hover：在货架图上高亮对应货位 */
function onRowHover(row) {
  if (!row || !parseCoord(row.locationCode)) {
    hoverInfo.value = null;
    return;
  }
  if (row.warehouseId) mapWarehouseId.value = row.warehouseId;
  highlightId.value = row.id;
  hoverInfo.value = row;
}

/** 货架图货位 hover：展示该货位信息 */
function onMapHover(loc) {
  highlightId.value = loc.id;
  hoverInfo.value = loc;
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
    locationCode: null,
    locationName: null,
    warehouseId: null,
    remark: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null
  };
  proxy.resetForm("locationRef");
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
  title.value = "添加商品位置";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  const _id = row.id || ids.value
  getLocation(_id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改商品位置";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["locationRef"].validate(valid => {
    if (valid) {
      buttonLoading.value = true;
      if (form.value.id != null) {
        updateLocation(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功");
          open.value = false;
          getList();
        }).finally(() => {
          buttonLoading.value = false;
        });
      } else {
        addLocation(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功");
          open.value = false;
          getList();
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
  proxy.$modal.confirm('确认删除位置【' + row.locationName + '】吗？').then(function() {
    return delLocation(_ids);
  }).then(() => {
    loading.value = true;
    getList();
    proxy.$modal.msgSuccess("删除成功");
  }).finally(() => {
    loading.value = false;
  });
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('wms/location/export', {
    ...queryParams.value
  }, `location_${new Date().getTime()}.xlsx`)
}

getList();
</script>
