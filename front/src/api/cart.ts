import { http } from './http'
import { endpoints } from './endpoints'

export type CartItemPayload = {
  cart_item_id: number
  product_id: number
  quantity: number
  price_snapshot: number
}

export type CartResponsePayload = {
  cart_id: number
  items: CartItemPayload[]
}

type CartItemCreatePayload = {
  product_id: number
  quantity: number
}

type CartItemUpdatePayload = {
  quantity: number
}

const withCredentials = { withCredentials: true }

export const getCart = async (): Promise<CartResponsePayload> => {
  const response = await http.get<CartResponsePayload>(endpoints.cart, withCredentials)
  return response.data
}

export const addCartItem = async (payload: CartItemCreatePayload): Promise<CartResponsePayload> => {
  const response = await http.post<CartResponsePayload>(endpoints.cartItems, payload, withCredentials)
  return response.data
}

export const updateCartItemQuantity = async (
  cartItemId: number,
  payload: CartItemUpdatePayload,
): Promise<CartResponsePayload> => {
  const response = await http.patch<CartResponsePayload>(
    `${endpoints.cartItems}/${cartItemId}`,
    payload,
    withCredentials,
  )
  return response.data
}

export const deleteCartItem = async (cartItemId: number): Promise<void> => {
  await http.delete(`${endpoints.cartItems}/${cartItemId}`, withCredentials)
}
