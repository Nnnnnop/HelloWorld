import { request } from './http'

const BASE_URL = '/api/files/favourites'

export function listFavourites() {
  return request(BASE_URL)
}

export function listFavouriteIds() {
  return request(`${BASE_URL}/ids`)
}

export function addFavourite(resourceId) {
  return request(`${BASE_URL}/${resourceId}`, { method: 'POST' })
}

export function removeFavourite(resourceId) {
  return request(`${BASE_URL}/${resourceId}`, { method: 'DELETE' })
}
