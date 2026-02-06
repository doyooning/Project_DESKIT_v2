package com.deskit.deskit.account.address.dto;

import com.deskit.deskit.account.address.entity.Address;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AddressResponse(
  @JsonProperty("address_id")
  Long addressId,

  @JsonProperty("receiver")
  String receiver,

  @JsonProperty("postcode")
  String postcode,

  @JsonProperty("addr_detail")
  String addrDetail,

  @JsonProperty("is_default")
  Boolean isDefault
) {
  public static AddressResponse from(Address address) {
    if (address == null) {
      return new AddressResponse(null, null, null, null, null);
    }
    return new AddressResponse(
      address.getId(),
      address.getReceiver(),
      address.getPostcode(),
      address.getAddrDetail(),
      Boolean.TRUE.equals(address.getIsDefault())
    );
  }
}
