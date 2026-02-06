package com.deskit.deskit.ai.evaluate.dto;

import com.deskit.deskit.account.enums.SellerGradeEnum;

public record AdminEvaluationResultResponse(
		Long adminEvalId,
		SellerGradeEnum gradeRecommended,
		String sellerEmail
) {
}
