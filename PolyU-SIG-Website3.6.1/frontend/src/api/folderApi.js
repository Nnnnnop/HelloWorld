import { request } from './http.js'

export async function listFolders() {
  return request('/api/folders')
}

export async function createFolder(name, parentId, visibility) {
  return request('/api/folders', {
    method: 'POST',
    body: JSON.stringify({ name, parentId, visibility })
  })
}

export async function updateFolder(id, name, parentId, visibility) {
  return request(`/api/folders/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ name, parentId, visibility })
  })
}

export async function deleteFolder(id) {
  return request(`/api/folders/${id}`, { method: 'DELETE' })
}