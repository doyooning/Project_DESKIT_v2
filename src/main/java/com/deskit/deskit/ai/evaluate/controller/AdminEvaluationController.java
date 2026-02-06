package com.deskit.deskit.ai.evaluate.controller;

import com.deskit.deskit.ai.evaluate.dto.AdminEvaluationRequest;
import com.deskit.deskit.ai.evaluate.dto.AdminEvaluationResultResponse;
import com.deskit.deskit.ai.evaluate.dto.AiEvaluationDetailResponse;
import com.deskit.deskit.ai.evaluate.dto.AiEvaluationSummaryResponse;
import com.deskit.deskit.ai.evaluate.service.AdminEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/evaluations")
public class AdminEvaluationController {

	private final AdminEvaluationService adminEvaluationService;

	@GetMapping
	public ResponseEntity<?> listEvaluations(Authentication authentication) {
		if (!isAdmin(authentication)) {
			return new ResponseEntity<>("forbidden", HttpStatus.FORBIDDEN);
		}
		List<AiEvaluationSummaryResponse> responses = adminEvaluationService.listEvaluations();
		return new ResponseEntity<>(responses, HttpStatus.OK);
	}

	@GetMapping("/{aiEvalId}")
	public ResponseEntity<?> getEvaluation(@PathVariable Long aiEvalId, Authentication authentication) {
		if (!isAdmin(authentication)) {
			return new ResponseEntity<>("forbidden", HttpStatus.FORBIDDEN);
		}
		try {
			AiEvaluationDetailResponse response = adminEvaluationService.getEvaluation(aiEvalId);
			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (IllegalArgumentException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@PostMapping("/{aiEvalId}/finalize")
	public ResponseEntity<?> finalizeEvaluation(
			@PathVariable Long aiEvalId,
			@RequestBody AdminEvaluationRequest request,
			Authentication authentication
	) {
		if (!isAdmin(authentication)) {
			return new ResponseEntity<>("forbidden", HttpStatus.FORBIDDEN);
		}
		try {
			AdminEvaluationResultResponse response = adminEvaluationService.finalizeEvaluation(aiEvalId, request);
			return new ResponseEntity<>(response, HttpStatus.OK);
		} catch (IllegalArgumentException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
		} catch (IllegalStateException ex) {
			return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
		} catch (IOException ex) {
			log.error("Finalize evaluation email send failed", ex);
			return new ResponseEntity<>("email send failed", HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private boolean isAdmin(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return false;
		}
		Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
		return authorities.stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
	}
}
