export const ORDER_STORAGE_KEY = 'deskit_order_last_v1'
export const ORDER_LIST_STORAGE_KEY = 'deskit_orders_v1'

export type OrderReceiptItem = {
  productId: string
  name: string
  quantity: number
  price: number
  originalPrice: number
  discountRate: number
}

export type OrderReceipt = {
  orderId: string
  createdAt: string
  items: OrderReceiptItem[]
  status?: 'CREATED' | 'PAID' | 'CANCEL_REQUESTED' | 'CANCELED' | 'REFUND_REJECTED' | 'REFUNDED'
  cancelReason?: string
  shipping: {
    buyerName: string
    zipcode: string
    address1: string
    address2: string
  }
  paymentMethodLabel: string
  totals: {
    listPriceTotal: number
    salePriceTotal: number
    discountTotal: number
    shippingFee: number
    total: number
  }
}

const emitUpdated = () => {
  window.dispatchEvent(new CustomEvent('deskit-order-updated'))
}

const safeParse = (raw: string | null): any => {
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

const normalizeReceipt = (raw: any): OrderReceipt | null => {
  if (!raw || typeof raw !== 'object') return null

  const orderId = typeof raw?.orderId === 'string' && raw.orderId.trim() ? raw.orderId : ''
  const createdAt = typeof raw?.createdAt === 'string' && raw.createdAt.trim() ? raw.createdAt : ''
  if (!orderId || !createdAt) return null

  const items = Array.isArray(raw.items)
    ? raw.items
        .map((item: any) => {
          const productId = item?.productId ?? item?.id
          const name = item?.name ?? ''
          if (!productId || !name) return null
          const quantity = Math.max(1, Number(item?.quantity ?? 0) || 0)
          const price = Math.max(0, Number(item?.price ?? 0) || 0)
          const originalPrice = Math.max(0, Number(item?.originalPrice ?? item?.price ?? 0) || 0)
          return {
            productId: String(productId),
            name,
            quantity,
            price,
            originalPrice,
            discountRate: Number(item?.discountRate ?? 0) || 0,
          } as OrderReceiptItem
        })
        .filter((v: OrderReceiptItem | null): v is OrderReceiptItem => Boolean(v))
    : []

  if (items.length === 0) return null

  const listPriceTotal = items.reduce((sum: number, item: OrderReceiptItem) => {
    const baseRaw = (item as any).originalPrice ?? (item as any).listPrice ?? item.price
    const base = Number(baseRaw) || 0
    const effectiveBase = base > item.price ? base : item.price
    return sum + effectiveBase * item.quantity
  }, 0)
  const salePriceTotal = items.reduce(
    (sum: number, item: OrderReceiptItem) => sum + item.price * item.quantity,
    0,
  )

  const shipping = {
    buyerName: raw?.shipping?.buyerName ?? '',
    zipcode: raw?.shipping?.zipcode ?? '',
    address1: raw?.shipping?.address1 ?? '',
    address2: raw?.shipping?.address2 ?? '',
  }

  const totalsRaw = {
    listPriceTotal: Number(raw?.totals?.listPriceTotal ?? listPriceTotal) || 0,
    salePriceTotal: Number(raw?.totals?.salePriceTotal ?? salePriceTotal) || 0,
    discountTotal: Number(raw?.totals?.discountTotal ?? 0) || 0,
    shippingFee: Number(raw?.totals?.shippingFee ?? 0) || 0,
    total: Number(raw?.totals?.total ?? 0) || 0,
  }

  const listTotal = Math.max(0, totalsRaw.listPriceTotal)
  const saleTotal = Math.max(0, totalsRaw.salePriceTotal)
  const shippingFee = Math.max(0, totalsRaw.shippingFee)
  const discountTotal = Math.max(0, listTotal - saleTotal)
  const total = saleTotal + shippingFee

  const paymentMethodLabel =
    typeof raw?.paymentMethodLabel === 'string' && raw.paymentMethodLabel.trim()
      ? raw.paymentMethodLabel
      : '토스페이'
  const status: OrderReceipt['status'] =
    raw?.status &&
    ['CREATED', 'PAID', 'CANCEL_REQUESTED', 'CANCELED', 'REFUND_REJECTED', 'REFUNDED'].includes(
      raw.status,
    )
      ? raw.status
      : 'PAID'
  const cancelReason =
    typeof raw?.cancelReason === 'string' && raw.cancelReason.trim()
      ? raw.cancelReason.trim()
      : undefined

  return {
    orderId,
    createdAt,
    items,
    status,
    cancelReason,
    shipping,
    paymentMethodLabel,
    totals: {
      listPriceTotal: listTotal,
      salePriceTotal: saleTotal,
      discountTotal,
      shippingFee,
      total,
    },
  }
}

export const loadLastOrder = (): OrderReceipt | null => {
  const parsed = safeParse(localStorage.getItem(ORDER_STORAGE_KEY))
  return normalizeReceipt(parsed)
}

export const saveLastOrder = (receipt: OrderReceipt) => {
  localStorage.setItem(ORDER_STORAGE_KEY, JSON.stringify(receipt))
  emitUpdated()
}

export const clearLastOrder = () => {
  localStorage.removeItem(ORDER_STORAGE_KEY)
  emitUpdated()
}

export const loadOrders = (): OrderReceipt[] => {
  const parsed = safeParse(localStorage.getItem(ORDER_LIST_STORAGE_KEY))
  if (!Array.isArray(parsed)) return []
  return parsed
    .map((item: any) => normalizeReceipt(item))
    .filter((v): v is OrderReceipt => Boolean(v))
}

export const saveOrders = (list: OrderReceipt[]) => {
  localStorage.setItem(ORDER_LIST_STORAGE_KEY, JSON.stringify(list))
  emitUpdated()
}

export const appendOrder = (receipt: OrderReceipt) => {
  const normalized = normalizeReceipt(receipt)
  if (!normalized) return
  const current = loadOrders().filter((o) => o.orderId !== normalized.orderId)
  const next = [normalized, ...current]
  saveOrders(next)
}

export const updateOrder = (orderId: string, patch: Partial<OrderReceipt>) => {
  if (!orderId) return
  const current = loadOrders()
  const next = current.map((order) => {
    if (order.orderId !== orderId) return order
    const merged = normalizeReceipt({ ...order, ...patch })
    return merged ?? order
  })
  saveOrders(next)
}
