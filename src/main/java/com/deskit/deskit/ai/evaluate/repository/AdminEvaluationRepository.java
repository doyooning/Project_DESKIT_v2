package com.deskit.deskit.ai.evaluate.repository;

import com.deskit.deskit.ai.evaluate.entity.AdminEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminEvaluationRepository extends JpaRepository<AdminEvaluation, Long> {
	AdminEvaluation findByAiEvalId(Long aiEvalId);
}
