// import { productsData, type DbProduct } from '../products-data'
//
// export type CartItem = {
//   id: string
//   productId: string
//   name: string
//   imageUrl: string
//   price: number
//   originalPrice: number
//   discountRate: number
//   quantity: number
//   stock: number
//   isSelected: boolean
// }
//
// type ProductLike = DbProduct & {
//   id?: string | number
//   salePrice?: number
//   originalPrice?: number
//   images?: string[]
//   image_url?: string
//   thumbnailUrl?: string
// }
//
// const getImage = (product: ProductLike) =>
//   product.thumbnailUrl || product.imageUrl || product.image_url || product.images?.[0] || ''
//
// const derivePricing = (product: ProductLike) => {
//   const salePrice = product.salePrice ?? product.price
//   const original = product.originalPrice ?? salePrice
//   const discount =
//     original > 0 && original > salePrice
//       ? Math.round(((original - salePrice) / original) * 100)
//       : 0
//   return {
//     price: salePrice,
//     originalPrice: original,
//     discountRate: discount,
//   }
// }
//
// export const createMockCartItems = (): CartItem[] => {
//   const base = productsData.slice(0, 3)
//   if (base.length === 0) return []
//
//   return base.map((product, index) => {
//     const productId = product.product_id ?? product.id
//     const { price, originalPrice, discountRate } = derivePricing(product)
//     return {
//       id: `cart-${productId}`,
//       productId: String(productId),
//       name: product.name,
//       imageUrl: getImage(product),
//       price,
//       originalPrice,
//       discountRate,
//       quantity: index === 1 ? 2 : 1,
//       stock: product.stock ?? 99,
//       isSelected: false,
//     }
//   })
// }
//
// export const clampQuantity = (item: CartItem, nextQty: number) =>
//   Math.min(item.stock, Math.max(1, nextQty))
//
// export const computeItemSubtotal = (item: CartItem) => item.price * item.quantity
