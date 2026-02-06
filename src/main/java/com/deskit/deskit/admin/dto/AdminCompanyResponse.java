package com.deskit.deskit.admin.dto;

public record AdminCompanyResponse(
		String id,
		String companyName,
		String ownerName,
		String businessNumber,
		String grade,
		String gradeExpiredAt,
		String status,
		String joinedAt
) {
}
