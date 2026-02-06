export type ScheduledProduct = {
  id: string
  name: string
  option: string
  price: number
  broadcastPrice: number
  stock: number
  quantity: number
  thumb?: string
}

export type ScheduledBroadcast = {
  id: string
  title: string
  subtitle: string
  thumb: string
  datetime: string
  ctaLabel: string
  products?: ScheduledProduct[]
  standbyThumb?: string
  termsAgreed?: boolean
}

const STORAGE_KEY = 'deskit_seller_scheduled_broadcasts_v1'

const normalizeProduct = (value: any): ScheduledProduct | null => {
  if (!value || typeof value.id !== 'string') return null
  const option = typeof value.option === 'string' ? value.option : typeof value.title === 'string' ? value.title : ''
  const name = typeof value.name === 'string' ? value.name : typeof value.title === 'string' ? value.title : ''
  if (!name || !option) return null
  const price = typeof value.price === 'number' ? value.price : 0
  const broadcastPrice = typeof value.broadcastPrice === 'number' ? value.broadcastPrice : price
  const stock = typeof value.stock === 'number' ? value.stock : 0
  const quantity = typeof value.quantity === 'number' ? value.quantity : 1

  return {
    id: value.id,
    name,
    option,
    price,
    broadcastPrice,
    stock,
    quantity,
    thumb: typeof value.thumb === 'string' ? value.thumb : undefined,
  }
}

const isScheduledBroadcast = (value: any): value is ScheduledBroadcast => {
  if (
    !value ||
    typeof value.id !== 'string' ||
    typeof value.title !== 'string' ||
    typeof value.subtitle !== 'string' ||
    typeof value.thumb !== 'string' ||
    typeof value.datetime !== 'string' ||
    typeof value.ctaLabel !== 'string'
  ) {
    return false
  }
  if (value.standbyThumb !== undefined && typeof value.standbyThumb !== 'string') {
    return false
  }
  if (value.termsAgreed !== undefined && typeof value.termsAgreed !== 'boolean') {
    return false
  }
  if (value.products !== undefined) {
    if (!Array.isArray(value.products)) return false
    if (!value.products.every((item: any) => normalizeProduct(item))) return false
  }
  return true
}

export const getScheduledBroadcasts = (): ScheduledBroadcast[] => {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(isScheduledBroadcast)
      .map((item) => ({
        ...item,
        products:
          (item.products
            ?.map((product: any) => normalizeProduct(product))
            .filter(Boolean) as ScheduledProduct[]) ?? [],
      }))
  } catch {
    return []
  }
}
