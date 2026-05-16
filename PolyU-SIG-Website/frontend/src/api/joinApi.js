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

export function adminListAllGroups(nameQuery) {
  const q = nameQuery?.trim()
  const suffix = q ? `?q=${encodeURIComponent(q)}` : ''
  return request(`${ADMIN_JOIN}/groups${suffix}`)
}

export function adminListGroupMembers(groupId) {
  const id = typeof groupId === 'number' ? groupId : Number(groupId)
  if (!Number.isFinite(id)) {
    return Promise.reject(new Error('Invalid group id.'))
  }
  return request(`${ADMIN_JOIN}/groups/${id}/members`)
}

export function adminRemoveGroupMembership(groupId, membershipId) {
  const gid = Number(groupId)
  const mid = Number(membershipId)
  if (!Number.isFinite(gid) || !Number.isFinite(mid)) {
    return Promise.reject(new Error('Invalid group or membership id.'))
  }
  return request(`${ADMIN_JOIN}/groups/${gid}/memberships/${mid}`, { method: 'DELETE' })
}

export function adminSetMemberSiteTier(userId, memberSiteTier) {
  const uid = Number(userId)
  if (!Number.isFinite(uid)) {
    return Promise.reject(new Error('Invalid user id.'))
  }
  return request(`${ADMIN_JOIN}/members/site-tier`, {
    method: 'PATCH',
    body: JSON.stringify({ userId: uid, memberSiteTier })
  })
}

/** @param {{ username?: string, memberSiteTier?: 'L1' | 'L2' }} opts */
export function adminLookupMember(opts = {}) {
  const params = new URLSearchParams()
  const u = typeof opts.username === 'string' ? opts.username.trim() : ''
  if (u) params.set('username', u)
  const t = opts.memberSiteTier
  if (t === 'L1' || t === 'L2') params.set('memberSiteTier', t)
  const qs = params.toString()
  if (!qs) {
    return Promise.reject(new Error('Enter NetID (optional) and/or choose Level 1 / Level 2.'))
  }
  return request(`${ADMIN_JOIN}/members/lookup?${qs}`)
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

export function adminReviewJoinApplication(id, approved, memberSiteTier) {
  const body = { approved }
  if (approved === true) {
    body.memberSiteTier = memberSiteTier || 'L1'
  }
  return request(`${ADMIN_JOIN}/applications/${id}/review`, {
    method: 'POST',
    body: JSON.stringify(body)
  })
}
