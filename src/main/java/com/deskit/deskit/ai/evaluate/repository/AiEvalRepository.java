package com.deskit.deskit.ai.evaluate.repository;

import com.deskit.deskit.ai.evaluate.entity.AiEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiEvalRepository extends JpaRepository<AiEvaluation, Long> {
    List<AiEvaluation> findByAiEvalIdOrderByCreatedAt(Long aiEvalId);
}
