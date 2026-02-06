package com.deskit.deskit.ai.evaluate.dto;

import com.deskit.deskit.account.enums.SellerGradeEnum;

public record EvaluateDTO(int businessStability, int productCompetency,
                          int liveSuitability, int operationCoop,
                          int growthPotential, int total_score,
                          SellerGradeEnum gradeRecommended, String summary) {
}
