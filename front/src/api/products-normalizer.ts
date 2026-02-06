import { type DbProduct } from '../lib/products-data'
import { PLACEHOLDER_IMAGE, resolvePrimaryImage } from '../lib/images/productImages'

const resolveTimestamp = (primary: any, fallback: any) => {
  return primary ?? fallback ?? ''
}

const resolveThumbnailUrl = (product: any) => {
  const images =
    product?.product_images ??
    product?.productImages ??
    product?.product_image ??
    product?.images
  if (!Array.isArray(images)) return undefined
  const thumbnail = images.find((image) => {
    const type = image?.image_type ?? image?.imageType
    const slot = image?.slot_index ?? image?.slotIndex ?? 0
    return type === 'THUMBNAIL' && Number(slot) === 0
  })
  return (
    thumbnail?.product_image_url ??
    thumbnail?.productImageUrl ??
    thumbnail?.image_url ??
    thumbnail?.imageUrl
  )
}

export const normalizeProduct = (raw: any): DbProduct => {
  const thumbnailUrl = resolveThumbnailUrl(raw)
  const imageSource = thumbnailUrl ? { ...raw, thumbnailUrl } : raw
  return {
    ...(raw ?? {}),
    product_id: raw?.product_id ?? raw?.id ?? 0,
    name: raw?.name ?? raw?.product_name ?? '',
    imageUrl: resolvePrimaryImage(imageSource) || PLACEHOLDER_IMAGE,
    detailHtml: raw?.detailHtml ?? raw?.detail_html ?? '',
    created_dt: resolveTimestamp(
      raw?.created_at ?? raw?.created_dt ?? raw?.createdAt,
      raw?.created_dt ?? raw?.createdAt
    ) || new Date().toISOString(),
    updated_dt: resolveTimestamp(
      raw?.updated_at ?? raw?.updated_dt ?? raw?.updatedAt,
      raw?.updated_dt ?? raw?.updatedAt
    ) || new Date().toISOString(),
    ...(thumbnailUrl ? { thumbnailUrl } : {}),
  }
}

export const normalizeProducts = (items: any[]): DbProduct[] => {
  if (!Array.isArray(items)) return []
  return items.map(normalizeProduct)
}
