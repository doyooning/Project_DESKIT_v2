package com.deskit.deskit.ai.evaluate.dto;

import com.deskit.deskit.account.enums.SellerGradeEnum;

import java.time.LocalDateTime;

public record AiEvaluationDetailResponse(
		Long aiEvalId,
		Long sellerId,
		Long registerId,
		String companyName,
		String description,
		Integer businessStability,
		Integer productCompetency,
		Integer liveSuitability,
		Integer operationCoop,
		Integer growthPotential,
		Integer totalScore,
		SellerGradeEnum sellerGrade,
		String summary,
		LocalDateTime createdAt,
		String sellerEmail,
		AdminEvaluationDetailResponse adminEvaluation
) {
}
