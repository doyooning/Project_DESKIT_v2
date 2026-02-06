package com.deskit.deskit.ai.evaluate.dto;

import com.deskit.deskit.account.enums.SellerGradeEnum;

import java.time.LocalDateTime;

public record AiEvaluationSummaryResponse(
		Long aiEvalId,
		Long sellerId,
		Long registerId,
		String sellerName,
		String companyName,
		String description,
		Integer totalScore,
		SellerGradeEnum sellerGrade,
		String summary,
		LocalDateTime createdAt,
		boolean finalized
) {
}
