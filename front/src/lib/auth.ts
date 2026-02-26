export type AuthUser = {
  name: string
  email: string
  signupType: string
  memberCategory: string
  profileUrl?: string
  role?: string
  phone?: string
  createdAt?: string
  sellerRole?: string
  mbti?: string
  job?: string
  seller_id?: number
  sellerId?: number
  id?: number
  user_id?: number
  userId?: number
}

type SessionPayload = Partial<AuthUser> & {
  role?: string
  sellerRole?: string
  memberCategory?: string
  phone?: string
  createdAt?: string
}

const webBase = import.meta.env.VITE_WEB_BASE_URL || window.location.origin
const apiBase = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
let sessionUser: AuthUser | null = null

const clearStoredAuthTokens = (): void => {
  ;['access', 'access_token'].forEach((key) => {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  })
}

export const clearClientAuthState = (): void => {
  clearStoredAuthTokens()
  setAuthUser(null)
}

export const setAuthUser = (next: AuthUser | null): void => {
  sessionUser = next
  window.dispatchEvent(new Event('deskit-user-updated'))
}

export const getAuthUser = (): AuthUser | null => sessionUser

export const isLoggedIn = (): boolean => sessionUser !== null

export const isEmailAddressLike = (value?: string): boolean => {
  const candidate = (value ?? '').trim()
  if (!candidate) return false
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(candidate)
}

export const normalizeDisplayName = (value: string | null | undefined, fallback = ''): string => {
  const candidate = (value ?? '').trim()
  if (!candidate || isEmailAddressLike(candidate)) {
    return fallback.trim()
  }
  return candidate
}

const normalizeRole = (value: string): string => value.trim().toUpperCase()

const isSellerCategory = (value: string): boolean => {
  const normalized = value.trim()
  if (!normalized) return false
  if (normalized === '판매자') return true
  const upper = normalized.toUpperCase()
  return upper === 'SELLER' || upper.startsWith('ROLE_SELLER')
}

const isAdminCategory = (value: string): boolean => {
  const normalized = value.trim()
  if (!normalized) return false
  if (normalized === '관리자') return true
  const upper = normalized.toUpperCase()
  return upper === 'ADMIN' || upper === 'ROLE_ADMIN'
}

const deriveMemberCategoryFromRole = (role: string): string => {
  const normalized = normalizeRole(role)
  if (normalized === 'ROLE_ADMIN') return '관리자'
  if (normalized === 'ROLE_MEMBER') return '일반회원'
  if (normalized.startsWith('ROLE_SELLER')) return '판매자'
  return ''
}

const deriveSellerRoleFromRole = (role: string): string => {
  const normalized = normalizeRole(role)
  if (normalized === 'ROLE_SELLER_OWNER') return '대표자'
  if (normalized === 'ROLE_SELLER_MANAGER') return '매니저'
  return ''
}

const resolveMemberCategory = (value?: string, role?: string): string => {
  if (value && value.trim()) return value
  if (role) return deriveMemberCategoryFromRole(role)
  return ''
}

const resolveSellerRole = (value?: string, role?: string): string => {
  if (value && value.trim()) return value
  if (role) return deriveSellerRoleFromRole(role)
  return ''
}

const isSellerRole = (role?: string): boolean => {
  if (!role) return false
  const normalized = normalizeRole(role)
  return normalized === 'SELLER' || normalized.startsWith('ROLE_SELLER')
}

const isAdminRole = (role?: string): boolean => {
  if (!role) return false
  const normalized = normalizeRole(role)
  return normalized === 'ADMIN' || normalized === 'ROLE_ADMIN'
}

export const isSeller = (): boolean => {
  const user = getAuthUser()
  return isSellerCategory(user?.memberCategory ?? '') || isSellerRole(user?.role)
}

export const isAdmin = (): boolean => {
  const user = getAuthUser()
  return isAdminCategory(user?.memberCategory ?? '') || isAdminRole(user?.role)
}

export const loginSeller = (): void => {
  const sellerUser: AuthUser = {
    name: '홍길동 판매자',
    email: 'honggildong+seller@test.com',
    signupType: '판매자(임시)',
    memberCategory: '판매자',
    sellerRole: '오너',
    seller_id: 101,
  }

  setAuthUser(sellerUser)
  // localStorage.setItem('deskit-user', JSON.stringify(sellerUser))
  // localStorage.setItem('deskit-auth', 'seller')
}

export const loginAdmin = (): void => {
  const adminUser: AuthUser = {
    name: '관리자',
    email: 'admin@test.com',
    signupType: '관리자(임시)',
    memberCategory: '관리자',
  }

  setAuthUser(adminUser)
  // localStorage.setItem('deskit-user', JSON.stringify(adminUser))
  // localStorage.setItem('deskit-auth', 'admin')
}

export const logout = (): void => {
  clearStoredAuthTokens()
  setAuthUser(null)
  ;['deskit-user', 'deskit-auth', 'token'].forEach((key) => localStorage.removeItem(key))
}

export const requestLogout = async (): Promise<boolean> => {
  const access = localStorage.getItem('access') || sessionStorage.getItem('access')
  const headers: Record<string, string> = {}
  if (access) {
    headers.access = access
  }
  let success = false
  try {
    const response = await fetch(`${webBase}/logout`, {
      method: 'POST',
      credentials: 'include',
      headers,
    })
    success = response.ok
  } catch (error) {
    console.error('logout failed', error)
  } finally {
    logout()
  }
  return success
}

export const requestWithdraw = async (): Promise<{ ok: boolean; message?: string }> => {
  const access = localStorage.getItem('access') || sessionStorage.getItem('access')
  const headers: Record<string, string> = {}
  if (access) {
    headers.access = access
  }

  try {
    const response = await fetch(`${apiBase}/quit`, {
      method: 'POST',
      credentials: 'include',
      headers,
    })
    const payload = await response.json().catch(() => null)
    const message =
      typeof payload?.message === 'string'
        ? payload.message
        : typeof payload === 'string'
          ? payload
          : undefined

    if (!response.ok) {
      return { ok: false, message }
    }

    return { ok: true, message }
  } catch (error) {
    console.error('withdraw failed', error)
    return { ok: false, message: 'withdraw failed' }
  }
}

export const hydrateSessionUser = async (): Promise<boolean> => {
  try {
    const access =
      localStorage.getItem('access') ||
      sessionStorage.getItem('access') ||
      localStorage.getItem('access_token') ||
      sessionStorage.getItem('access_token')
    const headers: Record<string, string> = {}
    if (access) {
      headers.Authorization = `Bearer ${access}`
      headers.access = access
    }
    let response = await fetch(`/api/my`, { credentials: 'include', headers })
    if (!response.ok) {
      if (response.status === 401) {
        const reissue = await fetch(`${webBase}/api/reissue`, {
          method: 'POST',
          credentials: 'include',
        })
        if (!reissue.ok) return false
        response = await fetch(`/api/my`, { credentials: 'include', headers })
      }
      if (!response.ok) return false
    }

    const payload = (await response.json().catch(() => null)) as SessionPayload | null
    if (!payload || typeof payload !== 'object') {
      return true
    }

    const memberCategory = resolveMemberCategory(payload.memberCategory, payload.role)
    const sellerRole = resolveSellerRole(payload.sellerRole, payload.role)

    const email = typeof payload.email === 'string' ? payload.email : ''
    const name = normalizeDisplayName(typeof payload.name === 'string' ? payload.name : '')

    setAuthUser({
      name,
      email,
      signupType: typeof payload.signupType === 'string' ? payload.signupType : '',
      memberCategory,
      profileUrl: typeof payload.profileUrl === 'string' ? payload.profileUrl : '',
      role: typeof payload.role === 'string' ? payload.role : '',
      phone: typeof payload.phone === 'string' ? payload.phone : '',
      createdAt: typeof payload.createdAt === 'string' ? payload.createdAt : '',
      sellerRole: sellerRole || undefined,
      mbti: typeof payload.mbti === 'string' ? payload.mbti : '',
      job: typeof payload.job === 'string' ? payload.job : '',
      seller_id: typeof payload.seller_id === 'number' ? payload.seller_id : undefined,
      sellerId: typeof payload.sellerId === 'number' ? payload.sellerId : undefined,
      id: typeof payload.id === 'number' ? payload.id : undefined,
      user_id: typeof payload.user_id === 'number' ? payload.user_id : undefined,
      userId: typeof payload.userId === 'number' ? payload.userId : undefined,
    })

    return true
  } catch {
    return false
  }
}
