export const PLACEHOLDER_IMAGE = '/placeholder-product.jpg'

export const getStorageBaseUrl = () => {
  const base = import.meta.env.VITE_STORAGE_BASE_URL ??
    'https://kr.object.ncloudstorage.com/live-commerce-bucket/'
  return base.endsWith('/') ? base : `${base}/`
}

export const extractImageUrl = (image: any): string => {
  if (typeof image === 'string') return image
  const direct =
    image?.product_image_url ??
    image?.productImageUrl ??
    image?.product_thumbnail_url ??
    image?.productThumbnailUrl ??
    image?.image_url ??
    image?.imageUrl ??
    image?.thumb_url ??
    image?.thumbUrl ??
    image?.thumbnail_url ??
    image?.thumbnailUrl
  if (direct) return direct
  const storedFileName = image?.stored_file_name ?? image?.storedFileName
  if (!storedFileName) return ''
  const trimmed = String(storedFileName).replace(/^\/+/, '')
  return `${getStorageBaseUrl()}${trimmed}`
}

export const resolveImageList = (raw: any): string[] => {
  const candidates = [
    raw?.product_images,
    raw?.productImages,
    raw?.productImageList,
    raw?.product_image_list,
    raw?.productImageResponses,
    raw?.images,
  ].filter(Array.isArray)
  return candidates
    .flat()
    .map(extractImageUrl)
    .filter((url) => typeof url === 'string' && url.trim().length > 0)
}

export const resolvePrimaryImage = (raw: any): string => {
  const direct =
    raw?.product_image_url ??
    raw?.productImageUrl ??
    raw?.image_url ??
    raw?.imageUrl ??
    raw?.thumbnail_url ??
    raw?.thumbnailUrl
  if (direct) return direct
  const storedFileName = raw?.stored_file_name ?? raw?.storedFileName
  if (storedFileName) {
    const trimmed = String(storedFileName).replace(/^\/+/, '')
    return `${getStorageBaseUrl()}${trimmed}`
  }
  const list = resolveImageList(raw)
  return list[0] ?? PLACEHOLDER_IMAGE
}

export const resolveProductImageUrlFromRaw = (raw: any): string => {
  const primary = resolvePrimaryImage(raw) || PLACEHOLDER_IMAGE
  if (primary.startsWith('http://') || primary.startsWith('https://')) return primary
  if (primary.startsWith('/live-commerce-bucket/')) {
    return `${getStorageBaseUrl()}${primary.replace(/^\/+live-commerce-bucket\/+/, '')}`
  }
  if (primary.startsWith('live-commerce-bucket/')) {
    return `${getStorageBaseUrl()}${primary.replace(/^live-commerce-bucket\/+/, '')}`
  }
  if (primary.startsWith('seller_')) {
    return `${getStorageBaseUrl()}${primary.replace(/^\/+/, '')}`
  }
  if (primary.startsWith('/')) return primary
  return primary || PLACEHOLDER_IMAGE
}

const warnedImageSources = new Set<string>()

export const createImageErrorHandler = () => {
  const handleImageError = (event: Event) => {
    const target = event.target as HTMLImageElement | null
    if (!target) return
    if (target.dataset.fallbackApplied) return
    const failedSrc = target.currentSrc || target.src || ''
    if (failedSrc && !warnedImageSources.has(failedSrc)) {
      console.warn('[Image] failed to load:', failedSrc)
      warnedImageSources.add(failedSrc)
    }
    target.dataset.fallbackApplied = 'true'
    if (target.src !== PLACEHOLDER_IMAGE) {
      target.src = PLACEHOLDER_IMAGE
    }
  }

  return { handleImageError, warnedSet: warnedImageSources }
}
