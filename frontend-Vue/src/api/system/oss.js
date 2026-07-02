import request from '@/utils/request'
import { normalizeOssUrl } from '@/utils/ossUrl'

// 查询OSS对象存储列表
export function listOss(query) {
  return request({
    url: '/system/oss/list',
    method: 'get',
    params: query
  })
}

// 查询OSS对象基于id串
export function listByIds(ossId) {
  return request({
    url: '/system/oss/listByIds/' + ossId,
    method: 'get'
  }).then(res => {
    // 数据库存储模式返回根相对路径，统一补全接口前缀，便于前端直接加载
    if (res && Array.isArray(res.data)) {
      res.data.forEach(item => {
        if (item && item.url) item.url = normalizeOssUrl(item.url)
      })
    }
    return res
  })
}

// 删除OSS对象存储
export function delOss(ossId) {
  return request({
    url: '/system/oss/' + ossId,
    method: 'delete'
  })
}

