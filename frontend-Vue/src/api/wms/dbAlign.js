import request from '@/utils/request'

// 检测数据库与最新版本的差异
export function checkDbAlign() {
  return request({
    url: '/wms/dbAlign/check',
    method: 'get'
  })
}

// 一键对齐（补齐缺失的表/字段/菜单）
export function runDbAlign() {
  return request({
    url: '/wms/dbAlign/run',
    method: 'post'
  })
}
