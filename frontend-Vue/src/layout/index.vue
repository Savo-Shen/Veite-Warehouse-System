<template>
  <div :class="classObj" class="app-wrapper" :style="{ '--current-color': theme }">
    <div v-if="!isMobileStandalone && device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside"/>
    <sidebar v-if="!isMobileStandalone && !sidebar.hide" class="sidebar-container" />
    <div :class="{ hasTagsView: needTagsView && !isMobileStandalone, sidebarHide: sidebar.hide || isMobileStandalone }" class="main-container">
      <div v-if="!isMobileStandalone" :class="{ 'fixed-header': fixedHeader }">
        <navbar @setLayout="setLayout" />
        <tags-view v-if="needTagsView" />
      </div>
      <app-main />
      <settings v-if="!isMobileStandalone" ref="settingRef" />
    </div>
  </div>
</template>

<script setup>
import { useWindowSize } from '@vueuse/core'
import Sidebar from './components/Sidebar/index.vue'
import { AppMain, Navbar, Settings, TagsView } from './components'
import defaultSettings from '@/settings'

import useAppStore from '@/store/modules/app'
import useSettingsStore from '@/store/modules/settings'

const settingsStore = useSettingsStore()
const theme = computed(() => settingsStore.theme);
const sideTheme = computed(() => settingsStore.sideTheme);
const sidebar = computed(() => useAppStore().sidebar);
const device = computed(() => useAppStore().device);
const needTagsView = computed(() => settingsStore.tagsView);
const fixedHeader = computed(() => settingsStore.fixedHeader);
const route = useRoute()

// 手机上不套系统外壳（侧边栏/导航/标签栏）的页面，各自画满整屏
const mobileStandalonePaths = ['/receiptOrderEdit', '/shipmentOrderEdit', '/wms/ai']
// 其中这几个自己管高度和滚动（AI 页是「头部固定 + 消息区内部滚」的聊天布局），
// 要把 app-main 钉死成一屏；其余的（出入库编辑页）是普通长表单，得让页面自己往下滚，
// 钉死一屏会把底部的商品明细和操作栏裁掉、划不到底。
const mobileFullscreenPaths = ['/wms/ai']
const isMobileStandalone = computed(() => (
  device.value === 'mobile' && mobileStandalonePaths.includes(route.path)
))
const isMobileFullscreen = computed(() => (
  device.value === 'mobile' && mobileFullscreenPaths.includes(route.path)
))

const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  openSidebar: sidebar.value.opened,
  withoutAnimation: sidebar.value.withoutAnimation,
  mobile: device.value === 'mobile',
  mobileStandalone: isMobileStandalone.value,
  mobileFullscreen: isMobileFullscreen.value
}))

const { width, height } = useWindowSize();
const WIDTH = 992; // refer to Bootstrap's responsive design

watchEffect(() => {
  if (device.value === 'mobile' && sidebar.value.opened) {
    useAppStore().closeSideBar({ withoutAnimation: false })
  }
  if (width.value - 1 < WIDTH) {
    useAppStore().toggleDevice('mobile')
    useAppStore().closeSideBar({ withoutAnimation: true })
  } else {
    useAppStore().toggleDevice('desktop')
  }
})

function handleClickOutside() {
  useAppStore().closeSideBar({ withoutAnimation: false })
}

const settingRef = ref(null);
function setLayout() {
  settingRef.value.openSetting();
}
</script>

<style lang="scss" scoped>
  @import "@/assets/styles/mixin.scss";
  @import "@/assets/styles/variables.module.scss";

.app-wrapper {
  @include clearfix;
  position: relative;
  height: 100%;
  width: 100%;

  &.mobile.openSidebar {
    position: fixed;
    top: 0;
  }
}

.drawer-bg {
  background: #000;
  opacity: 0.3;
  width: 100%;
  top: 0;
  height: 100%;
  position: absolute;
  z-index: 999;
}

.fixed-header {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 9;
  width: calc(100% - #{$base-sidebar-width});
  transition: width 0.28s;
}

.hideSidebar .fixed-header {
  width: calc(100% - 54px);
}

.sidebarHide .fixed-header {
  width: 100%;
}

.mobile .fixed-header {
  width: 100%;
}

.mobileStandalone {
  :deep(.app-main) {
    // 没有导航和标签栏了，顶部的让位也一并去掉
    padding-top: 0 !important;
    padding-bottom: 0 !important;
    // 高度交给内容撑，页面照常往下滚（app-main 自带 overflow: hidden，
    // 一旦给它定高就等于把超出一屏的内容裁掉，怎么划都到不了底）
    min-height: 100dvh;
  }
}

.mobileFullscreen {
  :deep(.app-main) {
    height: 100dvh;
    min-height: 0;
    // 这里必须是 auto 不能是 hidden：mobileStandalone 把 app-main 钉成
    // 100dvh，而它内部（新建出入库单那两个页面）没有任何自带滚动容器，
    // 写 hidden 的话超出一屏的内容直接被裁掉且划不动——商品明细和
    // 「添加商品」整块都够不到。/wms/ai 自己按 --ai-vh 撑满一屏且有内部
    // 滚动区，内容不溢出，所以改成 auto 对它没有影响。
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    padding-top: 0 !important;
    padding-bottom: 0 !important;
  }
}
</style>
