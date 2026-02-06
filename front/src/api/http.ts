import axios, { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { API_BASE_URL, REQUEST_TIMEOUT_MS } from './config'

/**
 * 공통 Axios 인스턴스
 * - baseURL: API 서버 주소 (ex. http://localhost:8080)
 * - 모든 API 요청은 이 인스턴스를 통해 나가도록 강제
 */
export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: REQUEST_TIMEOUT_MS,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

/**
 * Request Interceptor
 *
 * 역할:
 * 1. localStorage에 access_token 이 있으면 Authorization 헤더 자동 주입
 * 2. 단, 공개 API(GET) 요청은 토큰 제외
 *
 * 주의:
 * - /api/orders 는 인증 필수 → 무조건 Authorization 헤더 포함됨
 */
http.interceptors.request.use((config: InternalAxiosRequestConfig) => {

  const baseURL = typeof config.baseURL === 'string' ? config.baseURL.replace(/\/+$/, '') : ''
  const url = config.url ?? ''

  if (baseURL.endsWith('/api') && url.startsWith('/api/')) {
    config.url = url.replace(/^\/api/, '')
  }

  if (baseURL.endsWith('/api') && url.startsWith('/livechats/')) {
    config.baseURL = baseURL.replace(/\/api$/, '')
  }

  const token =
    localStorage.getItem('access_token') ||
    sessionStorage.getItem('access_token') ||
    localStorage.getItem('access') ||
    sessionStorage.getItem('access')

  const method = (config.method ?? 'get').toLowerCase()
  const rawUrl = config.url ?? ''

  // absolute / relative URL 모두 pathname 기준으로 판별
  const path = rawUrl.startsWith('http')
      ? (() => {
        try {
          return new URL(rawUrl).pathname
        } catch {
          return rawUrl
        }
      })()
      : rawUrl

  /**
   * 인증 없이 접근 가능한 공개 GET API 목록
   * 👉 여기에 없는 API는 전부 "로그인 필요"
   */
  const isPublicGet =
      method === 'get' &&
      (path.startsWith('/api/home') ||
          path.startsWith('/api/products') ||
          path.startsWith('/api/setups'))

  // 인증 필요 API → Authorization 헤더 주입
  if (token && !isPublicGet) {
    const headers = AxiosHeaders.from(config.headers)
    headers.set('Authorization', `Bearer ${token}`)
    config.headers = headers
  }

  return config
})
