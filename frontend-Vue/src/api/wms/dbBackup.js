import request from '@/utils/request'

export function listDbBackups() {
  return request({ url: '/wms/dbBackup/list', method: 'get' })
}

export function getDbBackupStatus() {
  return request({ url: '/wms/dbBackup/status', method: 'get' })
}

export function getDbBackupSettings() {
  return request({ url: '/wms/dbBackup/settings', method: 'get' })
}

export function saveDbBackupSettings(data) {
  return request({ url: '/wms/dbBackup/settings', method: 'post', data })
}

// 环境自检会真连一次数据库导一遍表结构，比普通接口慢
export function checkDbBackupEnv() {
  return request({ url: '/wms/dbBackup/check', method: 'get', timeout: 180000 })
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
