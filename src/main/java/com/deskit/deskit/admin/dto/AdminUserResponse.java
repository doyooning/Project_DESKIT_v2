package com.deskit.deskit.admin.dto;

public record AdminUserResponse(
		String id,
		String email,
		String name,
		String type,
		String status,
		String phone,
		String joinedAt,
		String provider,
		boolean marketingAgreed
) {
}
