package com.deskit.deskit.cart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record CartResponse(
    @JsonProperty("cart_id") Long cartId,
    @JsonProperty("items") List<CartItemResponse> items
) {}
