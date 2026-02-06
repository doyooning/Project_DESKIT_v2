import { type StoredCartItem } from '../cart/cart-storage'

export const CHECKOUT_STORAGE_KEY = 'deskit_checkout_v1'

export type CheckoutItem = {
  productId: string
  name: string
  imageUrl: string
  price: number
  originalPrice: number
  discountRate: number
  quantity: number
  stock: number
}

export type ShippingInfo = {
  buyerName: string
  zipcode: string
  address1: string
  address2: string
  isDefault?: boolean
}

export type PaymentMethod = 'CARD' | 'EASY_PAY' | 'TRANSFER'

export type CheckoutDraft = {
  source: 'CART' | 'BUY_NOW'
  items: CheckoutItem[]
  shipping: ShippingInfo
  paymentMethod: PaymentMethod | null
}

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))

const emitUpdated = () => {
  window.dispatchEvent(new CustomEvent('deskit-checkout-updated'))
}

const safeParse = (raw: string | null): any | null => {
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

const normalizeItem = (raw: any): CheckoutItem | null => {
  if (!raw || typeof raw !== 'object') return null
  const productId = raw.productId ?? raw.product_id ?? raw.id
  if (!productId) return null
  const name = typeof raw.name === 'string' ? raw.name : ''
  if (!name) return null
  const stock = Math.max(1, Number(raw.stock ?? 99) || 99)
  const quantity = clamp(Number(raw.quantity ?? 1) || 1, 1, stock)
  return {
    productId: String(productId),
    name,
    imageUrl: typeof raw.imageUrl === 'string' ? raw.imageUrl : '',
    price: Number(raw.price ?? 0) || 0,
    originalPrice: Number(raw.originalPrice ?? raw.price ?? 0) || 0,
    discountRate: Number(raw.discountRate ?? 0) || 0,
    quantity,
    stock,
  }
}

const normalizeShipping = (raw: any): ShippingInfo => ({
  buyerName: typeof raw?.buyerName === 'string' ? raw.buyerName : '',
  zipcode: typeof raw?.zipcode === 'string' ? raw.zipcode : '',
  address1: typeof raw?.address1 === 'string' ? raw.address1 : '',
  address2: typeof raw?.address2 === 'string' ? raw.address2 : '',
  isDefault: typeof raw?.isDefault === 'boolean' ? raw.isDefault : undefined,
})

const normalizeDraft = (raw: any): CheckoutDraft | null => {
  if (!raw || typeof raw !== 'object') return null
  const items = Array.isArray(raw.items)
    ? raw.items.map(normalizeItem).filter((v: CheckoutItem | null): v is CheckoutItem => Boolean(v))
    : []
  if (items.length === 0) return null
  const source = raw.source === 'BUY_NOW' ? 'BUY_NOW' : 'CART'
  const paymentMethod: PaymentMethod | null =
    raw.paymentMethod === 'CARD' ||
    raw.paymentMethod === 'EASY_PAY' ||
    raw.paymentMethod === 'TRANSFER'
      ? raw.paymentMethod
      : null
  const shipping = normalizeShipping(raw.shipping)
  return {
    source,
    items,
    shipping,
    paymentMethod,
  }
}

export const loadCheckout = (): CheckoutDraft | null => {
  const raw = safeParse(localStorage.getItem(CHECKOUT_STORAGE_KEY))
  return normalizeDraft(raw)
}

export const saveCheckout = (draft: CheckoutDraft) => {
  localStorage.setItem(CHECKOUT_STORAGE_KEY, JSON.stringify(draft))
  emitUpdated()
}

export const clearCheckout = () => {
  localStorage.removeItem(CHECKOUT_STORAGE_KEY)
  emitUpdated()
}

export const updateShipping = (patch: Partial<ShippingInfo>): CheckoutDraft | null => {
  const current = loadCheckout()
  if (!current) return null
  const nextPatch: Partial<ShippingInfo> = {}

  if ('buyerName' in patch) {
    const raw = (patch.buyerName ?? '').trim().replace(/\s+/g, ' ')
    nextPatch.buyerName = raw.slice(0, 6)
  }
  if ('zipcode' in patch) {
    const digits = String(patch.zipcode ?? '').replace(/[^0-9]/g, '').slice(0, 5)
    nextPatch.zipcode = digits
  }
  if ('address1' in patch) {
    nextPatch.address1 = (patch.address1 ?? '').trim()
  }
  if ('address2' in patch) {
    nextPatch.address2 = (patch.address2 ?? '').trim()
  }
  if ('isDefault' in patch) {
    nextPatch.isDefault = Boolean(patch.isDefault)
  }

  current.shipping = {
    ...current.shipping,
    ...nextPatch,
  }
  saveCheckout(current)
  return current
}

export const updatePaymentMethod = (method: PaymentMethod | null): CheckoutDraft | null => {
  const current = loadCheckout()
  if (!current) return null
  current.paymentMethod = method
  saveCheckout(current)
  return current
}

export const updateCheckoutItemsPricing = (
  patches: Array<{
    productId: string
    price: number
    originalPrice: number
    discountRate: number
    stock: number
  }>,
): CheckoutDraft | null => {
  if (!Array.isArray(patches) || patches.length === 0) return loadCheckout()
  const current = loadCheckout()
  if (!current) return null
  const patchMap = new Map(patches.map((patch) => [String(patch.productId), patch]))
  current.items = current.items.map((item) => {
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
  saveCheckout(current)
  return current
}

export const createCheckoutFromCart = (selectedCartItems: StoredCartItem[]): CheckoutDraft => {
  const items: CheckoutItem[] = selectedCartItems
    .filter((item) => item.isSelected)
    .map((item) => ({
      productId: item.productId,
      name: item.name,
      imageUrl: item.imageUrl,
      price: item.price,
      originalPrice: item.originalPrice,
      discountRate: item.discountRate,
      quantity: clamp(item.quantity, 1, item.stock),
      stock: item.stock,
    }))

  return {
    source: 'CART',
    items,
    shipping: {
      buyerName: '',
      zipcode: '',
      address1: '',
      address2: '',
    },
    paymentMethod: null,
  }
}

export const createCheckoutFromBuyNow = (productLike: any, quantity: number): CheckoutDraft => {
  const productId = productLike?.product_id ?? productLike?.id
  const name = productLike?.name ?? ''
  const imageUrl =
    productLike?.thumbnailUrl ||
    productLike?.imageUrl ||
    productLike?.image_url ||
    productLike?.images?.[0] ||
    ''
  const salePrice =
    Number(productLike?.salePrice ?? productLike?.sale_price ?? productLike?.price ?? 0) || 0
  const candidateListPrice =
    Number(
      productLike?.originalPrice ??
        productLike?.original_price ??
        productLike?.cost_price ??
        productLike?.listPrice ??
        productLike?.list_price ??
        salePrice,
    ) || salePrice
  const listPrice = candidateListPrice > salePrice ? candidateListPrice : salePrice
  const discount =
    listPrice > salePrice ? Math.round((1 - salePrice / listPrice) * 100) : 0
  const stock = Math.max(1, Number(productLike?.stock ?? 99) || 99)
  const qty = clamp(Number(quantity) || 1, 1, stock)

  const items: CheckoutItem[] = [
    {
      productId: String(productId ?? ''),
      name,
      imageUrl,
      price: salePrice,
      originalPrice: listPrice,
      discountRate: discount,
      quantity: qty,
      stock,
    },
  ].filter((item) => item.productId && item.name)

  return {
    source: 'BUY_NOW',
    items,
    shipping: {
      buyerName: '',
      zipcode: '',
      address1: '',
      address2: '',
    },
    paymentMethod: null,
  }
}
