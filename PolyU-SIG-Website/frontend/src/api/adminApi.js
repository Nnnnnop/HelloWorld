import { request } from './http'

export function listAuditLogs() {
  return request('/api/admin/audit-logs')
}
