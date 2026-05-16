import { request } from './http'

const JOIN = '/api/join'
const ADMIN_JOIN = '/api/admin/join'

export function listRecruitingGroups() {
  return request(`${JOIN}/groups`)
}

export function listMyJoinApplications() {
  return request(`${JOIN}/applications/mine`)
}

export function listMyGroupMemberships() {
  return request(`${JOIN}/memberships/mine`)
}

export function submitJoinApplication(payload) {
  return request(`${JOIN}/applications`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function withdrawJoinApplication(id) {
  return request(`${JOIN}/applications/${id}`, { method: 'DELETE' })
}

/** Admin */
export function adminListAllGroups() {
  return request(`${ADMIN_JOIN}/groups`)
}

export function adminCreateGroup(payload) {
  return request(`${ADMIN_JOIN}/groups`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function adminUpdateGroup(id, payload) {
  return request(`${ADMIN_JOIN}/groups/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function adminDeactivateGroup(id) {
  return request(`${ADMIN_JOIN}/groups/${id}`, { method: 'DELETE' })
}

export function adminListJoinApplications(status) {
  const q = status ? `?status=${encodeURIComponent(status)}` : ''
  return request(`${ADMIN_JOIN}/applications${q}`)
}

export function adminReviewJoinApplication(id, approved) {
  return request(`${ADMIN_JOIN}/applications/${id}/review`, {
    method: 'POST',
    body: JSON.stringify({ approved })
  })
}
