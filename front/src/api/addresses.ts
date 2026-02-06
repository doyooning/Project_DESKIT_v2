import { http } from './http'
import { endpoints } from './endpoints'
import type { AddressCreateRequest, AddressResponse, AddressUpdateRequest } from './types/addresses'

const withCredentials = { withCredentials: true }

export const getMyAddresses = async (): Promise<AddressResponse[]> => {
  const response = await http.get<AddressResponse[]>(endpoints.addresses, withCredentials)
  return response.data
}

export const createAddress = async (
  request: AddressCreateRequest,
): Promise<AddressResponse> => {
  const response = await http.post<AddressResponse>(endpoints.addresses, request, withCredentials)
  return response.data
}

export const updateAddress = async (
  addressId: number,
  request: AddressUpdateRequest,
): Promise<AddressResponse> => {
  const response = await http.patch<AddressResponse>(
    endpoints.addressDetail(addressId),
    request,
    withCredentials,
  )
  return response.data
}

export const deleteAddress = async (addressId: number): Promise<void> => {
  await http.delete(endpoints.addressDetail(addressId), withCredentials)
}
