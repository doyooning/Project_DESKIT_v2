export interface AddressResponse {
  address_id: number
  receiver: string
  postcode: string
  addr_detail: string
  is_default: boolean
}

export interface AddressCreateRequest {
  receiver: string
  postcode: string
  addr_detail: string
  is_default?: boolean
}

export interface AddressUpdateRequest {
  receiver: string
  postcode: string
  addr_detail: string
  is_default?: boolean
}
