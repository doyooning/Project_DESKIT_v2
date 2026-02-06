export const CART_STORAGE_KEY = 'deskit_cart_v1'

export type StoredCartItem = {
  id: string
  productId: string
  name: string
  imageUrl: string
  price: number
  originalPrice: number
  discountRate: number
  quantity: number
  stock: number
  isSelected: boolean
}

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))
const emitUpdated = () => {
  window.dispatchEvent(new CustomEvent('deskit-cart-updated'))
}

const normalizeItem = (raw: any): StoredCartItem | null => {
  if (!raw || typeof raw !== 'object') return null
  const productId = raw.productId ?? raw.product_id ?? raw.id
  if (!productId) return null
  const name = typeof raw.name === 'string' ? raw.name : ''
  if (!name) return null

  const stock = Math.max(1, Number(raw.stock ?? 99) || 99)
  const quantity = clamp(Number(raw.quantity ?? 1) || 1, 1, stock)

  return {
    id: typeof raw.id === 'string' && raw.id.length ? raw.id : `cart-${productId}`,
    productId: String(productId),
    name,
    imageUrl: typeof raw.imageUrl === 'string' ? raw.imageUrl : '',
    price: Number(raw.price ?? 0) || 0,
    originalPrice: Number(raw.originalPrice ?? raw.original_price ?? raw.price ?? 0) || 0,
    discountRate: Number(raw.discountRate ?? raw.discount_rate ?? 0) || 0,
    quantity,
    stock,
    isSelected: typeof raw.isSelected === 'boolean' ? raw.isSelected : false,
  }
}

const safeParse = (raw: string | null): any[] => {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export const loadCart = (): StoredCartItem[] => {
  const raw = safeParse(localStorage.getItem(CART_STORAGE_KEY))
  return raw.map(normalizeItem).filter((v): v is StoredCartItem => Boolean(v))
}

export const saveCart = (items: StoredCartItem[]) => {
  localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items))
  emitUpdated()
}

export const upsertCartItem = (
  next: Omit<StoredCartItem, 'id' | 'isSelected'> & { isSelected?: boolean },
): StoredCartItem[] => {
  const current = loadCart()
  const productId = next.productId
  const found = current.find((item) => item.productId === productId)

  if (found) {
    const stock = next.stock ?? found.stock ?? 99
    const maxQty = Math.max(1, stock)
    const updatedQty = clamp(found.quantity + next.quantity, 1, maxQty)
    const updated = current.map((item) =>
      item.productId === productId
        ? {
            ...item,
            quantity: updatedQty,
            price: next.price,
            originalPrice: next.originalPrice,
            discountRate: next.discountRate,
            stock: maxQty,
            isSelected: item.isSelected,
          }
        : item,
    )
    saveCart(updated)
    return updated
  }

  const stock = next.stock ?? 99
  const maxQty = Math.max(1, stock)
  const created: StoredCartItem = {
    id: `cart-${productId}`,
    productId,
    name: next.name,
    imageUrl: next.imageUrl,
    price: next.price,
    originalPrice: next.originalPrice,
    discountRate: next.discountRate,
    quantity: clamp(next.quantity, 1, maxQty),
    stock: maxQty,
    isSelected: next.isSelected ?? true,
  }

  const updated = [...current, created]
  saveCart(updated)
  return updated
}

export const removeCartItem = (productId: string): StoredCartItem[] => {
  const updated = loadCart().filter((item) => item.productId !== productId)
  saveCart(updated)
  return updated
}

export const clearCart = () => {
  saveCart([])
}

export const updateSelection = (productId: string, selected: boolean): StoredCartItem[] => {
  const updated = loadCart().map((item) =>
    item.productId === productId ? { ...item, isSelected: selected } : item,
  )
  saveCart(updated)
  return updated
}

export const updateQuantity = (productId: string, quantity: number): StoredCartItem[] => {
  const updated = loadCart().map((item) => {
    if (item.productId !== productId) return item
    const maxQty = Math.max(1, item.stock ?? 99)
    return { ...item, quantity: clamp(quantity, 1, maxQty) }
  })
  saveCart(updated)
  return updated
}

export const setAllSelected = (selected: boolean): StoredCartItem[] => {
  const updated = loadCart().map((item) => ({ ...item, isSelected: selected }))
  saveCart(updated)
  return updated
}

export const updateCartItemsPricing = (
  patches: Array<{
    productId: string
    price: number
    originalPrice: number
    discountRate: number
    stock: number
  }>,
): StoredCartItem[] => {
  if (!Array.isArray(patches) || patches.length === 0) return loadCart()
  const patchMap = new Map(patches.map((patch) => [String(patch.productId), patch]))
  const updated = loadCart().map((item) => {
    const patch = patchMap.get(item.productId)
    if (!patch) return item
    const stock = Math.max(1, Number(patch.stock ?? item.stock) || item.stock)
    return {
      ...item,
      price: patch.price,
      originalPrice: patch.originalPrice,
      discountRate: patch.discountRate,
      stock,
      quantity: clamp(item.quantity, 1, stock),
    }
  })
  saveCart(updated)
  return updated
}

export const removeCartItemsByProductIds = (productIds: string[]): StoredCartItem[] => {
  if (!Array.isArray(productIds) || productIds.length === 0) return loadCart()
  const set = new Set(productIds.map((id) => String(id)))
  const updated = loadCart().filter((item) => !set.has(item.productId))
  saveCart(updated)
  window.dispatchEvent(new CustomEvent('deskit-cart-updated'))
  return updated
}
