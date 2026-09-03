<!--
  出库单事后补充：只改备注和现场照片。

  出库/作废之后整单锁死是为了让明细跟已经动过的库存对得上，但签收单常常是第二天才拍到、
  当时漏写的一句说明回头也得补进去——这两样既不进库存也不进金额，所以单开这个口子。
  每次保存都会在单据的变更历史里留一条。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="'补充备注/图片 · ' + (order?.orderNo || '')"
    :width="dialogWidth"
    top="6vh"
    append-to-body
    @update:model-value="close"
  >
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="备注">
        <el-input
          v-model="remark"
          type="textarea"
          :rows="4"
          maxlength="255"
          show-word-limit
          placeholder="补充说明...255个字符以内"
        />
      </el-form-item>
      <el-form-item label="补充图片">
        <image-upload v-model="supplementImageIds" :limit="9" :file-size="10" />
        <div class="supplement-help">可上传签收单、现场或破损照片，最多 9 张。</div>
      </el-form-item>
    </el-form>
    <div class="supplement-tip">只改备注和图片，商品明细和库存不受影响；每次改动都会记进变更历史。</div>
    <template #footer>
      <el-button @click="close(false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup name="ShipmentSupplementDialog">
import { ElMessage } from "element-plus";
import { supplementShipmentOrder } from "@/api/wms/shipmentOrder";

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  // 至少要有 id / orderNo / remark / supplementImageIds
  order: {
    type: Object,
    default: null
  }
});

const emit = defineEmits(["update:modelValue", "saved"]);

const remark = ref("");
const supplementImageIds = ref("");
const saving = ref(false);
const dialogWidth = computed(() => (window.innerWidth < 768 ? "94%" : "560px"));

// 每次打开都从单据上重新读一遍，避免上一次编辑的残留内容串到别的单
watch(() => props.modelValue, visible => {
  if (!visible) return;
  remark.value = props.order?.remark || "";
  supplementImageIds.value = props.order?.supplementImageIds || "";
}, { immediate: true });

function close(value = false) {
  if (value) return;
  emit("update:modelValue", false);
}

async function save() {
  if (!props.order?.id) return;
  saving.value = true;
  try {
    const payload = {
      id: props.order.id,
      remark: remark.value ?? "",
      supplementImageIds: supplementImageIds.value ?? ""
    };
    await supplementShipmentOrder(payload);
    ElMessage.success("已保存");
    emit("saved", payload);
    emit("update:modelValue", false);
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.supplement-help,
.supplement-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.supplement-help {
  margin-top: 6px;
}
</style>
