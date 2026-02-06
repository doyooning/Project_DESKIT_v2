let isRedirecting = false

export const getCurrentRedirectPath = (): string => {
  if (typeof window === 'undefined') return ''
  return `${window.location.pathname}${window.location.search}`
}

export const toLoginWithRedirect = (): void => {
  if (typeof window === 'undefined') return
  if (isRedirecting) return
  if (window.location.pathname === '/login') return
  isRedirecting = true
  const redirect = getCurrentRedirectPath()
  window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`)
}
