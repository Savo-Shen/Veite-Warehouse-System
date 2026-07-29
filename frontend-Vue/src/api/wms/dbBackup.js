import request from '@/utils/request'

export function listDbBackups() {
  return request({ url: '/wms/dbBackup/list', method: 'get' })
}

export function createDbBackup() {
  return request({ url: '/wms/dbBackup/create', method: 'post', timeout: 1800000 })
}

export function downloadDbBackup(filename) {
  return request({
    url: `/wms/dbBackup/download/${encodeURIComponent(filename)}`,
    method: 'get',
    responseType: 'blob',
    timeout: 1800000
  })
}

export function deleteDbBackup(filename) {
  return request({
    url: `/wms/dbBackup/${encodeURIComponent(filename)}`,
    method: 'delete'
  })
}
