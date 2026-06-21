<template>
  <div class="app-container">
    <el-card>
      <el-form :model="queryParams" ref="queryRef" :inline="true" label-width="70px" @submit.prevent @keyup.enter="handleQuery">
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="queryParams.tagName" placeholder="请输入标签名称" clearable/>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="mt20">
      <el-row :gutter="10" class="mb8" type="flex" justify="space-between">
        <el-col :span="6"><span style="font-size: large">标签列表</span></el-col>
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['wms:itemTag:edit']">新增标签</el-button>
        </el-col>
      </el-row>

      <el-table v-loading="loading" :data="itemTagList" border stripe empty-text="暂无标签">
        <el-table-column label="标签" width="160">
          <template #default="{ row }">
            <el-tag :color="row.color" effect="dark" style="border: none; color: #fff;">{{ row.tagName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签名称" prop="tagName"/>
        <el-table-column label="颜色" prop="color" width="120">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 6px;">
              <span :style="{ display: 'inline-block', width: '14px', height: '14px', borderRadius: '3px', background: row.color }"/>
              <span>{{ row.color }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" show-overflow-tooltip/>
        <el-table-column label="创建时间" prop="createTime" width="180"/>
        <el-table-column label="操作" align="right" class-name="small-padding fixed-width" width="180">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['wms:itemTag:edit']">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['wms:itemTag:edit']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total>0" :total="total" v-model:page="queryParams.pageNum"
                  v-model:limit="queryParams.pageSize" @pagination="getList"/>
    </el-card>

    <!-- 添加或修改标签对话框 -->
    <el-drawer :title="title" v-model="open" size="40%" append-to-body>
      <el-form ref="itemTagRef" :model="form" :rules="rules" label-width="90px" @submit.prevent @keyup.enter="submitForm">
        <el-form-item label="标签名称" prop="tagName">
          <el-input v-model="form.tagName" placeholder="请输入标签名称，如：清仓处理" maxlength="30" show-word-limit/>
        </el-form-item>
        <el-form-item label="标签颜色" prop="color">
          <el-color-picker v-model="form.color" :predefine="predefineColors"/>
          <span class="ml10">预览：</span>
          <el-tag :color="form.color" effect="dark" style="border: none; color: #fff;">{{ form.tagName || '标签预览' }}</el-tag>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" placeholder="请输入备注"/>
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

<script setup name="ItemTag">
import { getItemTag, delItemTag, addItemTag, updateItemTag, listItemTagPage } from "@/api/wms/itemTag";
import { useWmsStore } from '@/store/modules/wms'

const { proxy } = getCurrentInstance();

const itemTagList = ref([]);
const open = ref(false);
const buttonLoading = ref(false);
const loading = ref(true);
const total = ref(0);
const title = ref("");

const predefineColors = [
  '#F56C6C', '#E6A23C', '#5CB87A', '#409EFF', '#909399',
  '#FF7D00', '#1ABC9C', '#9B59B6', '#34495E', '#E91E63'
]

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    tagName: undefined,
  },
  rules: {
    tagName: [
      { required: true, message: "标签名称不能为空", trigger: "blur" }
    ],
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询标签列表 */
async function getList() {
  loading.value = true;
  const res = await listItemTagPage(queryParams.value).finally(() => loading.value = false);
  itemTagList.value = res.rows;
  total.value = res.total;
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
    tagName: null,
    color: '#409EFF',
    remark: null,
  };
  proxy.resetForm("itemTagRef");
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
  title.value = "新增标签";
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset();
  getItemTag(row.id).then(response => {
    form.value = response.data;
    open.value = true;
    title.value = "修改标签";
  });
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["itemTagRef"].validate(valid => {
    if (valid) {
      buttonLoading.value = true;
      const api = form.value.id != null ? updateItemTag : addItemTag;
      api(form.value).then(() => {
        proxy.$modal.msgSuccess(form.value.id != null ? "修改成功" : "新增成功");
        open.value = false;
        getList();
        useWmsStore().getItemTagList();
      }).finally(() => {
        buttonLoading.value = false;
      });
    }
  });
}

/** 删除按钮操作 */
function handleDelete(row) {
  proxy.$modal.confirm('确认删除标签【' + row.tagName + '】吗？删除后将同时移除商品上的该标签。').then(function() {
    return delItemTag(row.id);
  }).then(() => {
    getList();
    useWmsStore().getItemTagList();
    proxy.$modal.msgSuccess("删除成功");
  }).catch(() => {});
}

getList();
</script>
