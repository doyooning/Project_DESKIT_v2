import { http } from './http'
import { endpoints } from './endpoints'

const withCredentials = { withCredentials: true }

export type TossConfirmRequest = {
  paymentKey: string
  orderId: string
  amount: number
}

export const confirmTossPayment = async (request: TossConfirmRequest) => {
  const response = await http.post(endpoints.tossConfirm, request, withCredentials)
  return response.data
}
