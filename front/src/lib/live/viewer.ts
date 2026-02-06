import type { AuthUser } from '../auth'

const VIEWER_ID_KEY = 'deskit_live_viewer_id_v1'
const memoryViewerIds = new Map<string, string>()

const buildViewerId = () =>
  window.crypto?.randomUUID
    ? window.crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(16).slice(2)}`

const getStoredViewerId = (keySuffix?: string): string | null => {
  const storageKey = keySuffix ? `${VIEWER_ID_KEY}:${keySuffix}` : VIEWER_ID_KEY
  try {
    const existing = localStorage.getItem(storageKey)
    if (existing) {
      return existing
    }
    const next = buildViewerId()
    localStorage.setItem(storageKey, next)
    return next
  } catch {
    const cached = memoryViewerIds.get(storageKey)
    if (cached) {
      return cached
    }
    const next = buildViewerId()
    memoryViewerIds.set(storageKey, next)
    return next
  }
}

export const resolveViewerId = (user: AuthUser | null): string | null => {
  const directId =
    user?.id ??
    user?.userId ??
    user?.user_id ??
    (user as { memberId?: number | string })?.memberId ??
    (user as { member_id?: number | string })?.member_id ??
    user?.sellerId ??
    user?.seller_id
  if (directId !== null && directId !== undefined) {
    return String(directId)
  }
  if (user?.email) {
    return user.email
  }

  const access =
    localStorage.getItem('access') ||
    sessionStorage.getItem('access') ||
    localStorage.getItem('access_token') ||
    sessionStorage.getItem('access_token')
  if (!access) {
    const categoryKey = user?.memberCategory?.trim().toLowerCase()
    return getStoredViewerId(categoryKey || undefined)
  }
  const tokenParts = access.split('.')
  const tokenPart = tokenParts[1]
  if (!tokenPart) {
    const categoryKey = user?.memberCategory?.trim().toLowerCase()
    return getStoredViewerId(categoryKey || undefined)
  }
  try {
    const normalized = tokenPart.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=')
    const payload = JSON.parse(atob(padded))
    const tokenId =
      payload?.memberId ??
      payload?.member_id ??
      payload?.id ??
      payload?.userId ??
      payload?.user_id ??
      payload?.sellerId ??
      payload?.seller_id ??
      payload?.sub
    if (tokenId === null || tokenId === undefined) {
      const categoryKey = user?.memberCategory?.trim().toLowerCase()
      return getStoredViewerId(categoryKey || undefined)
    }
    return String(tokenId)
  } catch {
    const categoryKey = user?.memberCategory?.trim().toLowerCase()
    const storedId = getStoredViewerId(categoryKey || undefined)
    if (storedId) {
      return storedId
    }
    return null
  }
}
