import { http } from '../api/http'

export type SellerProduct = {
  id: string
  sellerId?: number
  name: string
  shortDesc: string
  costPrice: number
  price: number
  stock: number
  images: string[]
  detailHtml: string
  createdAt: string
  updatedAt: string
}

export type SellerProductDraft = {
  id?: string
  sellerId?: number
  name: string
  shortDesc: string
  costPrice: number
  price: number
  stock: number
  images: string[]
  detailHtml: string
  tags?: string[]
}

const STORAGE_KEY = 'deskit_seller_products_v1'
const DRAFT_KEY = 'deskit_seller_product_draft_v1'

const safeParse = <T>(raw: string | null, fallback: T): T => {
  if (!raw) return fallback
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

const isSellerProduct = (value: any): value is SellerProduct => {
  return (
    value &&
    typeof value.id === 'string' &&
    (value.sellerId == null || typeof value.sellerId === 'number') &&
    typeof value.name === 'string' &&
    typeof value.shortDesc === 'string' &&
    typeof value.costPrice === 'number' &&
    typeof value.price === 'number' &&
    typeof value.stock === 'number' &&
    Array.isArray(value.images) &&
    typeof value.detailHtml === 'string' &&
    typeof value.createdAt === 'string' &&
    typeof value.updatedAt === 'string'
  )
}

const readAll = (): SellerProduct[] => {
  const parsed = safeParse<SellerProduct[]>(localStorage.getItem(STORAGE_KEY), [])
  return parsed.filter(isSellerProduct)
}

const writeAll = (items: SellerProduct[]) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
}

export const getSellerProducts = (): SellerProduct[] => {
  return readAll()
}

export const getProductById = (id: string): SellerProduct | null => {
  return readAll().find((item) => item.id === id) ?? null
}

export const upsertProduct = (product: SellerProduct): void => {
  const current = readAll()
  const idx = current.findIndex((item) => item.id === product.id)
  if (idx >= 0) {
    current[idx] = product
  } else {
    current.unshift(product)
  }
  writeAll(current)
}

export const deleteProduct = (id: string): void => {
  const next = readAll().filter((item) => item.id !== id)
  writeAll(next)
}

export const saveProductDraft = (draft: SellerProductDraft): void => {
  localStorage.setItem(DRAFT_KEY, JSON.stringify(draft))
}

export const loadProductDraft = (): SellerProductDraft | null => {
  const draft = safeParse<SellerProductDraft | null>(localStorage.getItem(DRAFT_KEY), null)
  if (!draft) return null
  if (
    !Array.isArray(draft.images)
  ) {
    return null
  }
  return draft
}

export const clearProductDraft = (): void => {
  localStorage.removeItem(DRAFT_KEY)
}

export type SellerTag = {
  tagId: number
  tagName: string
}

export const fetchSellerTags = async (): Promise<SellerTag[]> => {
  const response = await http.get('/api/seller/tags')
  const data = response.data
  if (!Array.isArray(data)) return []
  return data
    .map((raw) => {
      if (!raw || typeof raw !== 'object') return null
      const record = raw as Record<string, unknown>
      const tagId = typeof record.tag_id === 'number' ? record.tag_id : null
      const tagName = typeof record.tag_name === 'string' ? record.tag_name : null
      if (tagId == null || tagName == null) return null
      return { tagId, tagName }
    })
    .filter((item): item is SellerTag => Boolean(item))
}

export const updateProductTags = async (productId: number, tagIds: number[]): Promise<void> => {
  await http.put(`/api/seller/products/${productId}/tags`, {
    tag_ids: tagIds,
  })
}
