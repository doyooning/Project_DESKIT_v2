import { productsData, type DbProduct } from '../products-data'

export const SELLER_PRODUCTS_EVENT = 'deskit-seller-products-updated'

const STORAGE_KEY = 'deskit_mock_seller_products_v1'

const safeParse = <T>(raw: string | null, fallback: T): T => {
  if (!raw) return fallback
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

const seedProducts = (): DbProduct[] => {
  return productsData.map((item) => ({ ...item }))
}

const readAll = (): DbProduct[] => {
  const parsed = safeParse<DbProduct[]>(localStorage.getItem(STORAGE_KEY), [])
  if (parsed.length > 0) return parsed
  const seeded = seedProducts()
  localStorage.setItem(STORAGE_KEY, JSON.stringify(seeded))
  return seeded
}

const writeAll = (items: DbProduct[]) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
  window.dispatchEvent(new Event(SELLER_PRODUCTS_EVENT))
}

const resolveOwnerId = (product: any) => {
  const candidates = [
    product?.seller_id,
    product?.sellerId,
    product?.owner_id,
    product?.ownerId,
    product?.user_id,
    product?.userId,
  ]
  for (const value of candidates) {
    if (typeof value === 'number' && Number.isFinite(value)) return value
    if (typeof value === 'string') {
      const parsed = Number.parseInt(value, 10)
      if (!Number.isNaN(parsed)) return parsed
    }
  }
  return null
}

export const getSellerMockProducts = (sellerId: number): DbProduct[] => {
  return readAll().filter((product) => resolveOwnerId(product) === sellerId)
}

export const getAllMockProducts = (): DbProduct[] => {
  return readAll()
}

export const deleteSellerMockProduct = (id: string | number): void => {
  const key = String(id)
  const next = readAll().filter((item) => String(item.product_id) !== key)
  writeAll(next)
}

export const deleteMockProduct = (id: string | number): void => {
  deleteSellerMockProduct(id)
}
