import type { CheckoutItem, PaymentMethod, ShippingInfo } from './checkout-storage'

export const TOSS_PENDING_STORAGE_KEY = 'deskit_toss_pending_v1'

export type PendingTossPayment = {
  orderId: number
  orderNumber?: string
  tossOrderId: string
  orderAmount: number
  paymentMethod: PaymentMethod
  paymentMethodLabel?: string
  createdAt: string
  items: CheckoutItem[]
  shipping: ShippingInfo
  totals: {
    listPriceTotal: number
    salePriceTotal: number
    discountTotal: number
    shippingFee: number
    total: number
  }
}

const safeParse = (raw: string | null): any => {
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

export const savePendingTossPayment = (pending: PendingTossPayment) => {
  sessionStorage.setItem(TOSS_PENDING_STORAGE_KEY, JSON.stringify(pending))
}

export const loadPendingTossPayment = (): PendingTossPayment | null => {
  const parsed = safeParse(sessionStorage.getItem(TOSS_PENDING_STORAGE_KEY))
  if (!parsed || typeof parsed !== 'object') return null
  if (!Number.isFinite(Number(parsed.orderId))) return null
  return parsed as PendingTossPayment
}

export const clearPendingTossPayment = () => {
  sessionStorage.removeItem(TOSS_PENDING_STORAGE_KEY)
}
