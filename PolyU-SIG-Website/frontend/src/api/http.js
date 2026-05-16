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
        'Network error: API unreachable. If you use Docker, confirm `app` is running and nginx proxies /api (e.g. /api/auth/) to the backend. Disable extensions that block API routes or try another browser tab.'
      )
    }
    throw e
  }

  if (!response.ok) {
    const ct = response.headers.get('content-type') || ''
    let message = `Request failed (${response.status})`
    try {
      if (ct.includes('application/json')) {
        const body = await response.json()
        if (body?.message) {
          message = body.message
        }
      } else {
        const text = (await response.text()).trim()
        if (text) {
          message = `${message}: ${text.slice(0, 240)}`
        }
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
