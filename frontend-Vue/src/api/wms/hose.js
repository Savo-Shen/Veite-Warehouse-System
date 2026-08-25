import request from '@/utils/request'

// 胶管规格 + 在库汇总
export function listHoseSpec(query) {
  return request({
    url: '/wms/hose/spec/list',
    method: 'get',
    params: query
  })
}

// 胶管在库分段明细（一段一行）
export function listHosePiece(query) {
  return request({
    url: '/wms/hose/piece/list',
    method: 'get',
    params: query
  })
}

// 接头列表（分页）
export function listHoseFitting(query) {
  return request({
    url: '/wms/hose/fitting/list',
    method: 'get',
    params: query
  })
}

// 接头下拉选项，配料时选两端接头用
export function optionsHoseFitting(keyword) {
  return request({
    url: '/wms/hose/fitting/options',
    method: 'get',
    params: { keyword }
  })
}

// 扣压外套列表
export function listHoseFerrule(query) {
  return request({
    url: '/wms/hose/ferrule/list',
    method: 'get',
    params: query
  })
}

// 扣压参数列表
export function listHoseCrimp() {
  return request({
    url: '/wms/hose/crimp/list',
    method: 'get'
  })
}

// 配料查询：要什么料、料在哪、怎么压、多少钱
export function quoteHose(data) {
  return request({
    url: '/wms/hose/quote',
    method: 'post',
    data: data
  })
}

// 扣压参数回填
export function saveHoseCrimp(list) {
  return request({
    url: '/wms/hose/crimp',
    method: 'put',
    data: list
  })
}

// 新增一段胶管
export function addHosePiece(data) {
  return request({
    url: '/wms/hose/piece',
    method: 'post',
    data: data
  })
}

// 修改一段胶管
export function updateHosePiece(data) {
  return request({
    url: '/wms/hose/piece',
    method: 'put',
    data: data
  })
}

// 裁走一段：把用掉的米数扣掉
export function cutHosePiece(id, usedM) {
  return request({
    url: '/wms/hose/piece/cut/' + id,
    method: 'put',
    params: { usedM }
  })
}

// 删除一段（录错了才用）
export function delHosePiece(id) {
  return request({
    url: '/wms/hose/piece/' + id,
    method: 'delete'
  })
}
