package com.deskit.deskit.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemberIdResponse(
        @JsonProperty("member_id")
        Long memberId
) {}
