import { request } from './http'

const BASE_URL = '/api/auth'

export function register(payload) {
  return request(`${BASE_URL}/register`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function login(payload) {
  return request(`${BASE_URL}/login`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function logout() {
  return request(`${BASE_URL}/logout`, { method: 'POST' })
}

export function me() {
  return request(`${BASE_URL}/me`)
}

export function csrf() {
  return request(`${BASE_URL}/csrf`)
}

export function oauthUrl() {
  return request(`${BASE_URL}/oauth2/url`)
}

export function requestPasswordReset(payload) {
  return request(`${BASE_URL}/password-reset/request`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function confirmPasswordReset(payload) {
  return request(`${BASE_URL}/password-reset/confirm`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function listPendingUsers() {
  return request(`${BASE_URL}/pending-users`)
}

export function approveUser(payload) {
  return request(`${BASE_URL}/approve`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateRole(payload) {
  return request(`${BASE_URL}/role`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
