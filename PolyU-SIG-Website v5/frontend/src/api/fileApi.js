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

export function searchFiles({ keyword, fileType, category, uploader }) {
  const query = new URLSearchParams()
  if (keyword) query.set('keyword', keyword.trim())
  if (fileType) query.set('fileType', fileType.trim())
  if (category) query.set('category', category.trim())
  if (uploader) query.set('uploader', uploader.trim())

  const suffix = query.toString() ? `?${query.toString()}` : ''
  return request(`${BASE_URL}/search${suffix}`)
}

function requestWithProgress(url, formData, onProgress, requestOptions = {}) {
  const progressThrottleMs = requestOptions.progressThrottleMs ?? 0
  let lastEmitAt = 0
  let lastEmittedPercent = -1

  const emitProgress = (percent, loaded, total) => {
    if (typeof onProgress !== 'function') return
    if (progressThrottleMs <= 0) {
      onProgress(percent, loaded, total)
      return
    }
    const now = Date.now()
    const jump = Math.abs(percent - lastEmittedPercent)
    if (
      percent === 100 ||
      percent === 0 ||
      now - lastEmitAt >= progressThrottleMs ||
      jump >= 2
    ) {
      lastEmitAt = now
      lastEmittedPercent = percent
      onProgress(percent, loaded, total)
    }
  }

  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', url, true)
    xhr.withCredentials = true

    const token = getCookie('XSRF-TOKEN')
    if (token) {
      xhr.setRequestHeader('X-XSRF-TOKEN', token)
    }

    // Set 20 minute timeout for bulk uploads (covers upload + backend processing)
    xhr.timeout = 20 * 60 * 1000

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && typeof onProgress === 'function') {
        const percent = Math.min(99, Math.round((event.loaded / event.total) * 100))
        emitProgress(percent, event.loaded, event.total)
      }
    }

    xhr.onload = () => {
      if (typeof onProgress === 'function') {
        emitProgress(100)
      }

      const contentType = xhr.getResponseHeader('Content-Type') || ''
      let body = null
      if (contentType.includes('application/json')) {
        try {
          body = JSON.parse(xhr.responseText)
        } catch (e) {
          body = null
        }
      }

      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(body || {})
      } else {
        reject(new Error((body && body.message) || `Upload failed (${xhr.status})`))
      }
    }

    xhr.onerror = () => reject(new Error('Upload failed'))
    xhr.onabort = () => reject(new Error('Upload aborted'))
    xhr.ontimeout = () => reject(new Error('Upload timeout - request took too long'))
    xhr.send(formData)
  })
}

export async function uploadFile(fileOrFormData, payload, onProgress) {
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

  if (typeof onProgress === 'function') {
    return requestWithProgress(`${BASE_URL}/upload`, formData, onProgress, {})
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

export async function initializeUploadSession(sessionId, totalFiles, totalBytes) {
  return request(`${BASE_URL}/upload/session/initialize`, {
    method: 'POST',
    body: JSON.stringify({ sessionId, totalFiles, totalBytes })
  })
}

export function uploadSessionFile(sessionId, file, payload, onProgress, requestOptions = {}) {
  const formData = new FormData()
  formData.append('file', file, payload.clientPath || file.name)
  for (const [key, value] of Object.entries(payload || {})) {
    if (value !== undefined && value !== null) {
      formData.append(key, String(value))
    }
  }
  return requestWithProgress(
    `${BASE_URL}/upload/session/${encodeURIComponent(sessionId)}/file`,
    formData,
    onProgress,
    requestOptions
  )
}

export async function completeUploadSession(sessionId) {
  return request(`${BASE_URL}/upload/session/${encodeURIComponent(sessionId)}/complete`, {
    method: 'POST'
  })
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
