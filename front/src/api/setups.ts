import { http } from './http'
import { endpoints } from './endpoints'
import { type SetupWithProducts } from '../lib/setups-data'
import {
  fetchDetailTextJson,
  fetchListTextJsonWithRetry,
  isPlainObject,
} from './api-text-json'

const storageBase = (() => {
  const base = import.meta.env.VITE_STORAGE_BASE_URL ?? 'https://kr.object.ncloudstorage.com/live-commerce-bucket/'
  return base.endsWith('/') ? base : `${base}/`
})()

export const resolveSetupImageUrl = (rawValue?: string) => {
  const value = (rawValue ?? '').trim()
  if (!value) return '/placeholder-setup.jpg'
  if (value.startsWith('http://') || value.startsWith('https://')) return value
  if (value.startsWith('/live-commerce-bucket/')) {
    return storageBase + value.replace(/^\/live-commerce-bucket\/+/, '')
  }
  if (value.startsWith('live-commerce-bucket/')) {
    return storageBase + value.replace(/^live-commerce-bucket\/+/, '')
  }
  if (value.startsWith('seller_')) {
    return storageBase + value
  }
  if (value.startsWith('/')) return value
  return value
}

const normalizeSetup = (raw: any): SetupWithProducts => {
  const productIdsRaw = raw?.product_ids ?? raw?.productIds ?? raw?.productIdsRaw
  const setupProducts = raw?.setup_products ?? raw?.setupProducts
  const products = raw?.products
  const collectIds = (items: any[]) =>
    items
      .map((item) => {
        if (item && typeof item === 'object') {
          const source = item?.product && typeof item.product === 'object' ? item.product : item
          return Number(source?.product_id ?? source?.productId ?? source?.id)
        }
        return Number(item)
      })
      .filter((id) => Number.isFinite(id))
  const productIdsFromRelations = Array.isArray(setupProducts)
    ? collectIds(setupProducts)
    : Array.isArray(products)
      ? collectIds(products)
      : []
  const product_ids = [
    ...(Array.isArray(productIdsRaw) ? collectIds(productIdsRaw) : []),
    ...productIdsFromRelations,
  ]
  const uniqueProductIds = Array.from(new Set(product_ids))
  const tags = Array.isArray(raw?.tags) ? raw.tags : []
  return {
    setup_id: raw?.setup_id ?? raw?.setupId ?? raw?.id ?? 0,
    title: raw?.title ?? raw?.setupName ?? raw?.setup_name ?? raw?.setup_title ?? raw?.name ?? '',
    short_desc: raw?.short_desc ?? raw?.shortDesc ?? raw?.description ?? raw?.shortDescription ?? '',
    imageUrl: resolveSetupImageUrl(
      raw?.imageUrl ?? raw?.image_url ?? raw?.setupImageUrl ?? raw?.setup_image_url
    ),
    product_ids: uniqueProductIds,
    tags,
    tip: raw?.tip_text ?? raw?.tipText ?? raw?.tip ?? '',
    created_dt: raw?.created_dt ?? raw?.created_at ?? '',
    updated_dt: raw?.updated_dt ?? raw?.updated_at ?? '',
  }
}

export const listSetups = async (): Promise<SetupWithProducts[]> => {
  const payload = await fetchListTextJsonWithRetry(http, endpoints.setups, {
    validateStatus: (status) => (status >= 200 && status < 300) || status === 304,
  })
  return (payload as any[]).map(normalizeSetup)
}

export const getSetupDetail = async (
  id: string | number
): Promise<SetupWithProducts | null> => {
  const item = await fetchDetailTextJson(http, endpoints.setupDetail(id), {
    validateStatus: (status) => (status >= 200 && status < 300) || status === 404,
  })

  return isPlainObject(item) ? normalizeSetup(item) : null
}
