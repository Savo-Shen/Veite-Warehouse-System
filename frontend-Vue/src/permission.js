import router from './router'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import { isHttp } from '@/utils/validate'
import { isRelogin } from '@/utils/request'
import useUserStore from '@/store/modules/user'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'
import {useWmsStore} from '@/store/modules/wms';

NProgress.configure({ showSpinner: false });

const whiteList = ['/login', '/register'];
const desktopHomePaths = ['/', '/index', '/dashboard', '/system/dashboard'];

function shouldUseMobileHome(to) {
  return window.matchMedia('(max-width: 768px)').matches
    && desktopHomePaths.includes(to.path)
    && to.query.desktop !== '1'
}

router.beforeEach((to, from, next) => {
  NProgress.start()
  if (getToken()) {
    if (shouldUseMobileHome(to)) {
      next({ path: '/mobile', replace: true })
      NProgress.done()
      return
    }
    to.meta.title && useSettingsStore().setTitle(to.meta.title)
    /* has token*/
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else {
      if (useUserStore().roles.length === 0) {
        isRelogin.show = true
        // 判断当前用户是否已拉取完user_info信息
        useUserStore().getInfo().then(() => {
          // 每次重新打开系统时续期常用设备，续期失败不阻断当前已验证的会话
          useUserStore().renewSession().catch(() => {})
          isRelogin.show = false
          usePermissionStore().generateRoutes().then(accessRoutes => {
            // 根据roles权限生成可访问的路由表
            accessRoutes.forEach(route => {
              if (!isHttp(route.path)) {
                router.addRoute(route) // 动态添加可访问路由表
              }
            })
            next(shouldUseMobileHome(to)
              ? { path: '/mobile', replace: true }
              : { ...to, replace: true }) // hack方法 确保addRoutes已完成
          })
        }).catch(err => {
          useUserStore().logOut().then(() => {
            ElMessage.error(err)
            next({ path: '/' })
          })
        })
        initData()
      } else {
        next()
      }
    }
  } else {
    // 没有token
    if (whiteList.indexOf(to.path) !== -1) {
      // 在免登录白名单，直接进入
      next()
    } else {
      next(`/login?redirect=${to.fullPath}`) // 否则全部重定向到登录页
      NProgress.done()
    }
  }
})

async function initData() {
  await useWmsStore().getWarehouseList()
  await useWmsStore().getMerchantList()
  await useWmsStore().getItemCategoryList()
  await useWmsStore().getItemCategoryTreeList()
  await useWmsStore().getItemBrandList()
  await useWmsStore().getItemTagList()
  await useWmsStore().getLocationList()
}

router.afterEach(() => {
  NProgress.done()
})
