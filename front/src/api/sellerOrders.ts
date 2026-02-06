import { http } from './http'
import type { OrderItemResponse } from './types/orders'

const withCredentials = { withCredentials: true }

export type SellerOrderSummaryResponse = {
  order_id: number
  order_number: string
  status: string
  order_amount: number
  created_at: string
  paid_at?: string
  cancelled_at?: string
  refunded_at?: string
  item_count?: number
  first_product_name?: string
}

export type SellerOrderDetailResponse = {
  order_id: number
  order_number: string
  status: string
  order_amount: number
  created_at: string
  paid_at?: string
  cancelled_at?: string
  refunded_at?: string
  items: OrderItemResponse[]
}

export type PageResponse<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export const getSellerOrders = async (params: {
  status?: string | null
  page?: number
  size?: number
}): Promise<PageResponse<SellerOrderSummaryResponse>> => {
  const response = await http.get<PageResponse<SellerOrderSummaryResponse>>(
    '/api/seller/orders',
    {
      ...withCredentials,
      params: {
        status: params.status || undefined,
        page: params.page ?? 0,
        size: params.size ?? 10,
      },
    },
  )
  return response.data
}

export const getSellerOrderDetail = async (
  orderId: number,
): Promise<SellerOrderDetailResponse> => {
  const response = await http.get<SellerOrderDetailResponse>(
    `/api/seller/orders/${orderId}`,
    withCredentials,
  )
  return response.data
}
