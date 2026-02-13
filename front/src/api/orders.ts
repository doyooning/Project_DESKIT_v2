import { http } from './http'
import { endpoints } from './endpoints'
import type {
  CreateOrderRequest,
  CreateOrderResponse,
  OrderDetailResponse,
  OrderSummaryResponse,
} from './types/orders'

const withCredentials = { withCredentials: true }
type OrderCancelResponse = {
  order_id: number
  status: string
}

export const createOrder = async (
  request: CreateOrderRequest,
): Promise<CreateOrderResponse> => {
  const response = await http.post<CreateOrderResponse>(endpoints.orders, request, withCredentials)
  return response.data
}

export const getMyOrders = async (): Promise<OrderSummaryResponse[]> => {
  const response = await http.get<OrderSummaryResponse[]>(endpoints.orders, withCredentials)
  return response.data
}

export const getMyOrderDetail = async (orderId: number): Promise<OrderDetailResponse> => {
  const response = await http.get<OrderDetailResponse>(endpoints.orderDetail(orderId), withCredentials)
  return response.data
}

export const cancelOrder = async (orderId: number, reason: string): Promise<OrderCancelResponse> => {
  const response = await http.patch<OrderCancelResponse>(
    endpoints.orderCancel(orderId),
    { reason },
    withCredentials,
  )
  return response.data
}
