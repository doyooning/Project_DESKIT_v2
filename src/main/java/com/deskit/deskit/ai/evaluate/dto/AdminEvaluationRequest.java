package com.deskit.deskit.ai.evaluate.dto;

import com.deskit.deskit.account.enums.SellerGradeEnum;

public record AdminEvaluationRequest(
		SellerGradeEnum gradeRecommended,
		String adminComment
) {
}
