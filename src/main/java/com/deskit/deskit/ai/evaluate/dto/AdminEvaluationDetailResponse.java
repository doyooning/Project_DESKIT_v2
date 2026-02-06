package com.deskit.deskit.ai.evaluate.dto;

import com.deskit.deskit.account.enums.SellerGradeEnum;

import java.time.LocalDateTime;

public record AdminEvaluationDetailResponse(
		Long adminEvalId,
		SellerGradeEnum gradeRecommended,
		String adminComment,
		LocalDateTime createdAt
) {
}
