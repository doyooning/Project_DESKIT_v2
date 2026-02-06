import type { ProductTags } from './products-data'
import { productsData } from './products-data'
import { isSoldOut, isVisibleToUser } from '../utils/productStatusPolicy'
import { setupsData } from './setups-data'

export type SetupItem = {
  id: string
  title: string
  description: string
  imageUrl: string
}

export type ProductItem = {
  id: string
  name: string
  imageUrl: string
  price: number
  originalPrice?: number
  tags: ProductTags
  isSoldOut?: boolean
}

export const popularSetups: SetupItem[] = setupsData.slice(0, 6).map((setup) => ({
  id: String(setup.setup_id),
  title: setup.title,
  description: setup.short_desc,
  imageUrl: setup.imageUrl || '/placeholder-setup.jpg',
}))

export const popularProducts: ProductItem[] = productsData
  .filter((product) => isVisibleToUser(product.status))
  .sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0))
  .slice(0, 12)
  .map((product) => ({
    id: String(product.product_id),
    name: product.name,
    imageUrl: product.imageUrl ?? '/placeholder-product.jpg',
    price: product.price,
    originalPrice: product.cost_price > product.price ? product.cost_price : undefined,
    tags: product.tags ?? { space: [], tone: [], situation: [], mood: [] },
    isSoldOut: isSoldOut(product.status),
  }))
