import request from '@/utils/request'

// 仓库经营看板总览
export function getDashboardOverview() {
  return request({
    url: '/wms/dashboard/overview',
    method: 'get'
  })
}
