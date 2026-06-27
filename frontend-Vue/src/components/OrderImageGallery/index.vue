<template>
  <div v-if="images.length" class="order-image-gallery">
    <el-image
      v-for="(image, index) in images"
      :key="image.ossId"
      :src="image.url"
      :preview-src-list="previewUrls"
      :initial-index="index"
      fit="cover"
      preview-teleported
      class="order-image"
    />
  </div>
  <el-empty v-else description="本次单据未上传补充图片" :image-size="56" />
</template>

<script setup>
import { listByIds } from "@/api/system/oss";

const props = defineProps({
  imageIds: {
    type: String,
    default: ""
  }
});

const images = ref([]);
const previewUrls = computed(() => images.value.map(item => item.url));

watch(() => props.imageIds, async value => {
  if (!value) {
    images.value = [];
    return;
  }
  const response = await listByIds(value);
  images.value = response.data || [];
}, { immediate: true });
</script>

<style scoped lang="scss">
.order-image-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.order-image {
  width: 112px;
  height: 112px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}
</style>
