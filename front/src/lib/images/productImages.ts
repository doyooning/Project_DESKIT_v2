const LEGACY_PLACEHOLDER_IMAGE = '/placeholder-product.jpg'

const ensureTrailingSlash = (value: string) => (value.endsWith('/') ? value : `${value}/`)

const getProductImageBaseUrl = () => {
  const base =
    import.meta.env.VITE_PRODUCT_IMAGE_BASE_URL ??
    'https://dynii-bucket.s3.amazonaws.com/deskit/public/'
  return ensureTrailingSlash(base)
}

export const PLACEHOLDER_IMAGE = `${getProductImageBaseUrl()}placeholder-product.jpg`

export const getStorageBaseUrl = () => {
  const base = import.meta.env.VITE_STORAGE_BASE_URL ??
    'https://kr.object.ncloudstorage.com/live-commerce-bucket/'
  return ensureTrailingSlash(base)
}

const hasImageLikePath = (value: string) =>
  /\.(avif|bmp|gif|jpe?g|png|svg|webp)(\?.*)?$/i.test(value)

export const normalizeProductImageUrl = (rawValue?: string | null): string => {
  const value = String(rawValue ?? '').trim()
  if (!value) return PLACEHOLDER_IMAGE
  if (value === PLACEHOLDER_IMAGE || value === LEGACY_PLACEHOLDER_IMAGE) return PLACEHOLDER_IMAGE
  if (
    value.startsWith('http://') ||
    value.startsWith('https://') ||
    value.startsWith('data:') ||
    value.startsWith('blob:')
  ) {
    return value
  }
  if (value.startsWith('/live-commerce-bucket/')) {
    return `${getStorageBaseUrl()}${value.replace(/^\/+live-commerce-bucket\/+/, '')}`
  }
  if (value.startsWith('live-commerce-bucket/')) {
    return `${getStorageBaseUrl()}${value.replace(/^live-commerce-bucket\/+/, '')}`
  }
  if (value.startsWith('seller_')) {
    return `${getStorageBaseUrl()}${value.replace(/^\/+/, '')}`
  }
  if (value.startsWith('/deskit/public/')) {
    return `${getProductImageBaseUrl()}${value.replace(/^\/+deskit\/+public\/+/, '')}`
  }
  if (value.startsWith('deskit/public/')) {
    return `${getProductImageBaseUrl()}${value.replace(/^deskit\/+public\/+/, '')}`
  }
  if (value.startsWith('/')) {
    const trimmed = value.replace(/^\/+/, '')
    return hasImageLikePath(trimmed) ? `${getProductImageBaseUrl()}${trimmed}` : value
  }
  return hasImageLikePath(value) ? `${getProductImageBaseUrl()}${value}` : value
}

export const extractImageUrl = (image: any): string => {
  if (typeof image === 'string') return normalizeProductImageUrl(image)
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
  if (direct) return normalizeProductImageUrl(direct)
  const storedFileName = image?.stored_file_name ?? image?.storedFileName
  if (!storedFileName) return ''
  return normalizeProductImageUrl(String(storedFileName))
}

export const resolveImageList = (raw: any): string[] => {
  const candidates = [
    raw?.product_images,
    raw?.productImages,
    raw?.productImageList,
    raw?.product_image_list,
    raw?.productImageResponses,
    raw?.image_urls,
    raw?.imageUrls,
    raw?.product_image_urls,
    raw?.productImageUrls,
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
  if (direct) return normalizeProductImageUrl(direct)
  const storedFileName = raw?.stored_file_name ?? raw?.storedFileName
  if (storedFileName) return normalizeProductImageUrl(String(storedFileName))
  const list = resolveImageList(raw)
  return list[0] ?? PLACEHOLDER_IMAGE
}

export const resolveProductImageUrlFromRaw = (raw: any): string => {
  return normalizeProductImageUrl(resolvePrimaryImage(raw) || PLACEHOLDER_IMAGE)
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
