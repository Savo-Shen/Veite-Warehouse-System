import request from '@/utils/request'

export function uploadDbImport(file) {
  const form = new FormData()
  form.append('file', file)
  return request({
    url: '/wms/dbImport/upload',
    method: 'post',
    data: form,
    timeout: 1800000,
    headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false }
  })
}

export function applyDbImport(sessionId, data) {
  return request({
    url: `/wms/dbImport/${sessionId}/apply`,
    method: 'post',
    data,
    timeout: 1800000
  })
}

export function cancelDbImport(sessionId) {
  return request({ url: `/wms/dbImport/${sessionId}`, method: 'delete' })
}
