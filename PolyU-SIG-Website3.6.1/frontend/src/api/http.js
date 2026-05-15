export async function request(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase()
  const csrfToken = getCookie('XSRF-TOKEN')
  const headers = {
    ...(method !== 'GET' && method !== 'HEAD' && csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
    ...(options.headers || {})
  }
  if (!(options.body instanceof FormData) && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }

  let response
  try {
    response = await fetch(url, {
      credentials: 'include',
      headers,
      ...options
    })
  } catch (e) {
    const msg = String(e?.message || '')
    if (
      e instanceof TypeError &&
      (/failed to fetch|networkerror|load failed/i.test(msg) || msg === '')
    ) {
      throw new Error(
        'Network error: API unreachable. If you use Docker, confirm `app` is running and nginx proxies /api to the backend. Disable extensions that block `/api/admin/` or try another browser tab.'
      )
    }
    throw e
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = await response.json()
      if (body?.message) {
        message = body.message
      }
    } catch {
      // ignore parsing errors
    }
    throw new Error(message)
  }

  if (response.status === 204) {
    return null
  }
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    return null
  }
  return response.json()
}

function getCookie(name) {
  const value = `; ${document.cookie}`
  const parts = value.split(`; ${name}=`)
  if (parts.length === 2) return parts.pop().split(';').shift()
  return ''
}
