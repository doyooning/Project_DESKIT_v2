import type { AxiosInstance, AxiosRequestConfig } from 'axios'

export const isPlainObject = (value: unknown) => {
  if (!value || typeof value !== 'object') return false
  if (Array.isArray(value)) return false
  return Object.prototype.toString.call(value) === '[object Object]'
}

export const parseJsonIfString = (data: unknown) => {
  let parsed: unknown = data
  let parseFailed = false
  if (typeof parsed === 'string' && parsed.trim().length > 0) {
    try {
      parsed = JSON.parse(parsed)
    } catch {
      parsed = undefined
      parseFailed = true
    }
  }
  return { parsed, parseFailed }
}

export const pickListPayload = (parsed: unknown) => {
  if (Array.isArray(parsed)) return parsed
  if (isPlainObject(parsed) && Array.isArray((parsed as { data?: unknown }).data)) {
    return (parsed as { data: unknown[] }).data
  }
  return []
}

export const pickDetailItem = (parsed: unknown) => {
  if (Array.isArray(parsed)) return parsed[0]
  if (isPlainObject(parsed) && Array.isArray((parsed as { data?: unknown }).data)) {
    return (parsed as { data: unknown[] }).data?.[0]
  }
  if (isPlainObject(parsed) && isPlainObject((parsed as { data?: unknown }).data)) {
    return (parsed as { data: unknown }).data
  }
  return parsed
}

export const fetchTextNoCache = <T = unknown>(
  client: AxiosInstance,
  url: string,
  options: AxiosRequestConfig = {}
) =>
  client.get<T>(url, {
    ...options,
    params: { _ts: Date.now(), ...(options.params ?? {}) },
    headers: { 'Cache-Control': 'no-store', Pragma: 'no-cache', ...(options.headers ?? {}) },
    responseType: 'text',
    transformResponse: (data) => data,
  })

export const fetchListTextJsonWithRetry = async (
  client: AxiosInstance,
  url: string,
  options: AxiosRequestConfig = {}
) => {
  const fetchOnce = () => fetchTextNoCache(client, url, options)
  let response = await fetchOnce()
  let { parsed, parseFailed } = parseJsonIfString(response.data)
  let payload = pickListPayload(parsed)
  const isRawEmpty =
    response.data == null || (typeof response.data === 'string' && response.data.trim() === '')
  if (response.status === 304 || isRawEmpty || parseFailed || payload.length === 0) {
    response = await fetchOnce()
    ;({ parsed, parseFailed } = parseJsonIfString(response.data))
    payload = pickListPayload(parsed)
  }
  return payload
}

export const fetchDetailTextJson = async (
  client: AxiosInstance,
  url: string,
  options: AxiosRequestConfig = {}
) => {
  const response = await fetchTextNoCache(client, url, options)
  if (response.status === 404) return null
  const { parsed } = parseJsonIfString(response.data)
  return pickDetailItem(parsed)
}
