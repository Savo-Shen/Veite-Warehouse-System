import request from '@/utils/request'

// 查询商品标签列表（分页）
export function listItemTagPage(query) {
  return request({
    url: '/wms/itemTag/list',
    method: 'get',
    params: query
  })
}

// 查询商品标签列表（不分页）
export function listItemTag(query) {
  return request({
    url: '/wms/itemTag/listNoPage',
    method: 'get',
    params: query
  })
}

// 查询商品标签详细
export function getItemTag(id) {
  return request({
    url: '/wms/itemTag/' + id,
    method: 'get'
  })
}

// 新增商品标签
export function addItemTag(data) {
  return request({
    url: '/wms/itemTag',
    method: 'post',
    data: data
  })
}

// 修改商品标签
export function updateItemTag(data) {
  return request({
    url: '/wms/itemTag',
    method: 'put',
    data: data
  })
}

// 删除商品标签
export function delItemTag(id) {
  return request({
    url: '/wms/itemTag/' + id,
    method: 'delete'
  })
}

// 批量给商品打标签
export function assignItemTags(data) {
  return request({
    url: '/wms/itemTag/assign',
    method: 'post',
    data: data
  })
}

// 移除某个商品的某个标签
export function removeItemTagRel(itemId, tagId) {
  return request({
    url: '/wms/itemTag/rel/' + itemId + '/' + tagId,
    method: 'delete'
  })
}
