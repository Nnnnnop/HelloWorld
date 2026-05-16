import { request } from './http'
import { csrf } from './authApi'

const BASE_URL = '/api/posts'

export function listPosts({ type, published, limit } = {}) {
  const query = new URLSearchParams()
  if (type) query.set('type', String(type).trim().toUpperCase())
  if (published !== undefined && published !== null) query.set('published', String(Boolean(published)))
  if (limit) query.set('limit', String(limit))
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return request(`${BASE_URL}${suffix}`)
}

export function getPost(id) {
  return request(`${BASE_URL}/${id}`)
}

export function createPost(payload) {
  return csrf().catch(() => null).then(() => request(BASE_URL, {
    method: 'POST',
    body: JSON.stringify(payload)
  }))
}

export function updatePost(id, payload) {
  return csrf().catch(() => null).then(() => request(`${BASE_URL}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }))
}

export function deletePost(id) {
  return csrf().catch(() => null).then(() => request(`${BASE_URL}/${id}`, {
    method: 'DELETE'
  }))
}
