import request from '@/utils/request'

// 上新列表
export function listEcProduct(query) {
  return request({
    url: '/wms/ecProduct/list',
    method: 'get',
    params: query
  })
}

// 某个电商商品的 SKU 明细
export function getEcProductSkus(productId) {
  return request({
    url: '/wms/ecProduct/skus/' + productId,
    method: 'get'
  })
}

// 电商类目（含商品计数与三个平台的类目 ID）
export function listEcCategory() {
  return request({
    url: '/wms/ecProduct/categories',
    method: 'get'
  })
}

// 保存标题、卖点、备注
export function updateEcProduct(data) {
  return request({
    url: '/wms/ecProduct',
    method: 'put',
    data: data
  })
}

// 标记上架状态
export function updateEcProductStatus(id, status) {
  return request({
    url: '/wms/ecProduct/status/' + id,
    method: 'put',
    params: { status }
  })
}

// 回填平台类目 ID
export function updateEcCategory(data) {
  return request({
    url: '/wms/ecProduct/category',
    method: 'put',
    data: data
  })
}

// 录入实测重量与包装尺寸
export function saveEcMeasures(list) {
  return request({
    url: '/wms/ecProduct/measures',
    method: 'put',
    data: list
  })
}

// 提交 AI 生成标题任务，立即返回 taskId
export function genEcTitle(data) {
  return request({
    url: '/wms/ecProduct/genTitle',
    method: 'post',
    data: data
  })
}

// 查询生成任务进度
export function getEcGenProgress(taskId) {
  return request({
    url: '/wms/ecProduct/genTitle/' + taskId,
    method: 'get'
  })
}
