package com.deskit.deskit.ai.evaluate.service;

import com.deskit.deskit.account.entity.CompanyRegistered;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.entity.SellerGrade;
import com.deskit.deskit.account.entity.SellerRegister;
import com.deskit.deskit.account.enums.SellerGradeEnum;
import com.deskit.deskit.account.enums.SellerGradeStatus;
import com.deskit.deskit.account.enums.SellerStatus;
import com.deskit.deskit.account.repository.CompanyRegisteredRepository;
import com.deskit.deskit.account.repository.SellerGradeRepository;
import com.deskit.deskit.account.repository.SellerRegisterRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.ai.evaluate.dto.AdminEvaluationDetailResponse;
import com.deskit.deskit.ai.evaluate.dto.AdminEvaluationRequest;
import com.deskit.deskit.ai.evaluate.dto.AdminEvaluationResultResponse;
import com.deskit.deskit.ai.evaluate.dto.AiEvaluationDetailResponse;
import com.deskit.deskit.ai.evaluate.dto.AiEvaluationSummaryResponse;
import com.deskit.deskit.ai.evaluate.entity.AdminEvaluation;
import com.deskit.deskit.ai.evaluate.entity.AiEvaluation;
import com.deskit.deskit.ai.evaluate.repository.AdminEvaluationRepository;
import com.deskit.deskit.ai.evaluate.repository.AiEvalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminEvaluationService {

	private final AiEvalRepository aiEvalRepository;
	private final AdminEvaluationRepository adminEvaluationRepository;
	private final SellerRegisterRepository sellerRegisterRepository;
	private final SellerRepository sellerRepository;
	private final CompanyRegisteredRepository companyRegisteredRepository;
	private final SellerGradeRepository sellerGradeRepository;
	private final SellerEvaluationEmailService sellerEvaluationEmailService;

	public List<AiEvaluationSummaryResponse> listEvaluations() {
		List<AiEvaluation> evaluations = aiEvalRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
		return evaluations.stream()
				.map(this::toSummaryResponse)
				.collect(Collectors.toList());
	}

	public AiEvaluationDetailResponse getEvaluation(Long aiEvalId) {
		AiEvaluation evaluation = aiEvalRepository.findById(aiEvalId)
				.orElseThrow(() -> new IllegalArgumentException("ai evaluation not found"));
		SellerRegister register = sellerRegisterRepository.findById(evaluation.getRegisterId()).orElse(null);
		Seller seller = sellerRepository.findById(evaluation.getSellerId()).orElse(null);
		AdminEvaluation adminEvaluation = adminEvaluationRepository.findByAiEvalId(aiEvalId);
		AdminEvaluationDetailResponse adminResponse = adminEvaluation == null ? null : new AdminEvaluationDetailResponse(
				adminEvaluation.getAdminEvalId(),
				adminEvaluation.getSellerGrade(),
				adminEvaluation.getAdminComment(),
				adminEvaluation.getCreatedAt()
		);

		return new AiEvaluationDetailResponse(
				evaluation.getAiEvalId(),
				evaluation.getSellerId(),
				evaluation.getRegisterId(),
				register == null ? "" : nullSafe(register.getCompanyName()),
				register == null ? "" : nullSafe(register.getDescription()),
				evaluation.getBusinessStability(),
				evaluation.getProductCompetency(),
				evaluation.getLiveSuitability(),
				evaluation.getOperationCoop(),
				evaluation.getGrowthPotential(),
				evaluation.getTotalScore(),
				evaluation.getSellerGrade(),
				evaluation.getSummary(),
				evaluation.getCreatedAt(),
				seller == null ? "" : nullSafe(seller.getLoginId()),
				adminResponse
		);
	}

	@Transactional(rollbackFor = IOException.class)
	public AdminEvaluationResultResponse finalizeEvaluation(Long aiEvalId, AdminEvaluationRequest request) throws IOException {
		if (request == null || request.gradeRecommended() == null) {
			throw new IllegalArgumentException("grade recommended is required");
		}

		AiEvaluation evaluation = aiEvalRepository.findById(aiEvalId)
				.orElseThrow(() -> new IllegalArgumentException("ai evaluation not found"));

		AdminEvaluation existing = adminEvaluationRepository.findByAiEvalId(aiEvalId);
		if (existing != null) {
			throw new IllegalStateException("admin evaluation already exists");
		}

		Seller seller = sellerRepository.findById(evaluation.getSellerId())
				.orElseThrow(() -> new IllegalArgumentException("seller not found"));

		SellerGradeEnum finalGrade = request.gradeRecommended();

		AdminEvaluation adminEvaluation = new AdminEvaluation();
		adminEvaluation.setBusinessStability(evaluation.getBusinessStability());
		adminEvaluation.setProductCompetency(evaluation.getProductCompetency());
		adminEvaluation.setLiveSuitability(evaluation.getLiveSuitability());
		adminEvaluation.setOperationCoop(evaluation.getOperationCoop());
		adminEvaluation.setGrowthPotential(evaluation.getGrowthPotential());
		adminEvaluation.setTotalScore(evaluation.getTotalScore());
		adminEvaluation.setSellerGrade(finalGrade);
		adminEvaluation.setAdminComment(request.adminComment());
		adminEvaluation.setAiEvalId(aiEvalId);

		adminEvaluationRepository.save(adminEvaluation);

		updateSellerStatusAndGrade(seller, finalGrade);
		sellerEvaluationEmailService.sendFinalResult(
				seller.getLoginId(),
				finalGrade,
				request.adminComment(),
				nullSafe(seller.getName()),
				evaluation.getTotalScore()
		);

		return new AdminEvaluationResultResponse(adminEvaluation.getAdminEvalId(), finalGrade, seller.getLoginId());
	}

	private AiEvaluationSummaryResponse toSummaryResponse(AiEvaluation evaluation) {
		SellerRegister register = sellerRegisterRepository.findById(evaluation.getRegisterId()).orElse(null);
		Seller seller = sellerRepository.findById(evaluation.getSellerId()).orElse(null);
		boolean finalized = adminEvaluationRepository.findByAiEvalId(evaluation.getAiEvalId()) != null;
		return new AiEvaluationSummaryResponse(
				evaluation.getAiEvalId(),
				evaluation.getSellerId(),
				evaluation.getRegisterId(),
				seller == null ? "" : nullSafe(seller.getName()),
				register == null ? "" : nullSafe(register.getCompanyName()),
				register == null ? "" : nullSafe(register.getDescription()),
				evaluation.getTotalScore(),
				evaluation.getSellerGrade(),
				evaluation.getSummary(),
				evaluation.getCreatedAt(),
				finalized
		);
	}

	private void updateSellerStatusAndGrade(Seller seller, SellerGradeEnum grade) {
		SellerStatus nextStatus = grade == SellerGradeEnum.REJECTED ? SellerStatus.PENDING : SellerStatus.ACTIVE;
		seller.setStatus(nextStatus);
		sellerRepository.save(seller);

		CompanyRegistered company = companyRegisteredRepository.findBySellerId(seller.getSellerId());
		if (company == null) {
			return;
		}

		SellerGrade sellerGrade = sellerGradeRepository.findByCompanyId(company.getCompanyId());
		if (sellerGrade == null) {
			sellerGrade = new SellerGrade();
			sellerGrade.setCompanyId(company.getCompanyId());
		}

		sellerGrade.setGrade(grade);
		sellerGrade.setGradeStatus(resolveGradeStatus(grade));
		sellerGrade.setExpiredAt(resolveExpiredAt(grade));
		sellerGradeRepository.save(sellerGrade);
	}

	private SellerGradeStatus resolveGradeStatus(SellerGradeEnum grade) {
		return grade == SellerGradeEnum.REJECTED ? SellerGradeStatus.TEMP : SellerGradeStatus.ACTIVE;
	}

	private LocalDateTime resolveExpiredAt(SellerGradeEnum grade) {
		if (grade == SellerGradeEnum.REJECTED) {
			return null;
		}
		return LocalDateTime.now().plusMonths(3);
	}

	private String nullSafe(String value) {
		return value == null ? "" : value;
	}
}
