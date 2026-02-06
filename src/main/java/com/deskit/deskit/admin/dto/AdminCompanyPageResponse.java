package com.deskit.deskit.admin.dto;

import java.util.List;

public record AdminCompanyPageResponse(
		List<AdminCompanyResponse> items,
		int page,
		int size,
		long total,
		int totalPages
) {
}
