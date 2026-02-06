export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
export const USE_MOCK_API = import.meta.env.VITE_USE_MOCK_API === 'true'
const rawTimeout = Number.parseInt(import.meta.env.VITE_API_TIMEOUT_MS ?? '', 10)
export const REQUEST_TIMEOUT_MS = Number.isFinite(rawTimeout) && rawTimeout > 0 ? rawTimeout : 10000
