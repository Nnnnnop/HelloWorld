import { request } from './http'

function getCookie(name) {
  const value = `; ${document.cookie}`
  const parts = value.split(`; ${name}=`)
  if (parts.length === 2) return parts.pop().split(';').shift()
  return ''
}

const BASE_URL = '/api/files'

export function listFiles() {
  return request(BASE_URL)
}

export function getFileDetail(id, keyword) {
  const query = new URLSearchParams()
  if (keyword) query.set('keyword', keyword.trim())
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return request(`${BASE_URL}/${id}${suffix}`)
}

export function searchFiles({ keyword, fileType, category, uploader, uploadDateFrom, uploadDateTo }) {
  const query = new URLSearchParams()
  if (keyword) query.set('keyword', keyword.trim())
  if (fileType) query.set('fileType', fileType.trim())
  if (category) query.set('category', category.trim())
  if (uploader) query.set('uploader', uploader.trim())
  if (uploadDateFrom) query.set('uploadDateFrom', String(uploadDateFrom).trim())
  if (uploadDateTo) query.set('uploadDateTo', String(uploadDateTo).trim())

  const suffix = query.toString() ? `?${query.toString()}` : ''
  return request(`${BASE_URL}/search${suffix}`)
}

export async function uploadFile(fileOrFormData, payload) {
  const formData = fileOrFormData instanceof FormData ? fileOrFormData : new FormData()
  if (!(fileOrFormData instanceof FormData)) {
    formData.append('file', fileOrFormData)
    if (payload && typeof payload === 'object') {
      for (const [key, value] of Object.entries(payload)) {
        if (value !== undefined && value !== null) {
          formData.append(key, String(value))
        }
      }
    }
  }

  const response = await fetch(`${BASE_URL}/upload`, {
    method: 'POST',
    credentials: 'include',
    headers: (() => {
      const token = getCookie('XSRF-TOKEN')
      return token ? { 'X-XSRF-TOKEN': token } : {}
    })(),
    body: formData
  })
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message || 'Upload failed')
  }
  return response.json()
}

export async function uploadBulk(formData) {
  const controller = new AbortController()
  // Set 10 minute timeout for large bulk uploads
  const timeoutId = setTimeout(() => controller.abort(), 10 * 60 * 1000)
  
  try {
    const response = await fetch(`${BASE_URL}/upload-bulk`, {
      method: 'POST',
      credentials: 'include',
      signal: controller.signal,
      headers: (() => {
        const token = getCookie('XSRF-TOKEN')
        return token ? { 'X-XSRF-TOKEN': token } : {}
      })(),
      body: formData
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message || 'Upload failed')
    }
    return response
  } finally {
    clearTimeout(timeoutId)
  }
}

export function downloadFile(id, fileName) {
  const encodedName = encodeURIComponent(fileName || `file-${id}`)
  const link = document.createElement('a')
  link.href = `${BASE_URL}/${id}/download`
  link.download = decodeURIComponent(encodedName)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

export function filePreviewUrl(id) {
  return `${BASE_URL}/${id}/preview`
}

export function getArchiveList(id) {
  return request(`${BASE_URL}/${id}/archive-list`)
}

/** Absolute URL for streaming one ZIP member (same-origin; cookies sent when used in iframe/img/fetch). */
export function archiveEntryUrl(zipId, entryPath) {
  const q = new URLSearchParams()
  q.set('path', entryPath)
  return `${BASE_URL}/${zipId}/archive-entry?${q.toString()}`
}

export function updateFile(id, payload) {
  return request(`${BASE_URL}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function deleteFile(id) {
  return request(`${BASE_URL}/${id}`, {
    method: 'DELETE'
  })
}
