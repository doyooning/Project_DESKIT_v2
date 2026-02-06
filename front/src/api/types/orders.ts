export type OrderStatus =
  | 'CREATED'
  | 'PAID'
  | 'CANCEL_REQUESTED'
  | 'CANCELLED'
  | 'REFUND_REQUESTED'
  | 'REFUND_REJECTED'
  | 'REFUNDED'
  | 'COMPLETED'

export interface CreateOrderItemRequest {
  product_id: number
  quantity: number
}

export interface CreateOrderRequest {
  items: CreateOrderItemRequest[]
  receiver: string
  postcode: string
  addr_detail: string
  is_default?: boolean
}

export interface CreateOrderResponse {
  order_id: number
  order_number: string
  status: OrderStatus
  order_amount: number
}

export interface OrderSummaryResponse {
  order_id: number
  order_number: string
  status: OrderStatus
  order_amount: number
  created_at: string
  cancel_reason?: string
  cancel_requested_at?: string
}

export interface OrderItemResponse {
  order_item_id?: number
  product_id: number
  product_name?: string
  quantity: number
  unit_price: number
  subtotal_price: number
}

export interface OrderDetailResponse {
  order_id: number
  order_number: string
  status: OrderStatus
  order_amount: number
  created_at: string
  items: OrderItemResponse[]
}
