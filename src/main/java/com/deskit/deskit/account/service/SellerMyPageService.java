package com.deskit.deskit.account.service;

import com.deskit.deskit.account.dto.SellerManagerStatusResponse;
import com.deskit.deskit.account.dto.SellerMyPageResponse;
import com.deskit.deskit.account.entity.CompanyRegistered;
import com.deskit.deskit.account.entity.Invitation;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.entity.SellerGrade;
import com.deskit.deskit.account.enums.InvitationStatus;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.CompanyRegisteredRepository;
import com.deskit.deskit.account.repository.InvitationRepository;
import com.deskit.deskit.account.repository.SellerGradeRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class SellerMyPageService {

	private final CompanyRegisteredRepository companyRegisteredRepository;
	private final InvitationRepository invitationRepository;
	private final SellerRepository sellerRepository;
	private final SellerGradeRepository sellerGradeRepository;
	private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	public SellerMyPageResponse buildSellerMyPage(CustomOAuth2User user, String role) {
		String loginId = safe(user.getUsername());
		String normalizedRole = role == null ? "" : role.trim();

		if (loginId.isEmpty()) {
			return new SellerMyPageResponse("", "", "", List.of());
		}

		if ("ROLE_SELLER_OWNER".equals(normalizedRole)) {
			return buildOwnerPage(loginId);
		}

		if ("ROLE_SELLER_MANAGER".equals(normalizedRole)) {
			return buildManagerPage(loginId);
		}

		return new SellerMyPageResponse("", "", "", List.of());
	}

	private SellerMyPageResponse buildOwnerPage(String loginId) {
		Seller owner = sellerRepository.findByLoginId(loginId);
		if (owner == null) {
			return new SellerMyPageResponse("", "", "", List.of());
		}

		CompanyRegistered company = resolveCompany(owner.getSellerId());
		String companyName = resolveCompanyName(company);
		SellerGrade sellerGrade = resolveCompanyGrade(company);
		String companyGrade = resolveCompanyGradeLabel(sellerGrade);
		String gradeExpiredAt = resolveGradeExpiredAt(sellerGrade);
		List<SellerManagerStatusResponse> managers = resolveManagers(owner.getSellerId());
		return new SellerMyPageResponse(companyName, companyGrade, gradeExpiredAt, managers);
	}

	private SellerMyPageResponse buildManagerPage(String loginId) {
		Invitation invitation = invitationRepository.findFirstByEmailAndStatusIn(
				loginId,
				List.of(InvitationStatus.ACCEPTED.name())
		);
		if (invitation == null) {
			return new SellerMyPageResponse("", "", "", List.of());
		}

		CompanyRegistered company = resolveCompany(invitation.getSellerId());
		String companyName = resolveCompanyName(company);
		SellerGrade sellerGrade = resolveCompanyGrade(company);
		String companyGrade = resolveCompanyGradeLabel(sellerGrade);
		String gradeExpiredAt = resolveGradeExpiredAt(sellerGrade);
		return new SellerMyPageResponse(companyName, companyGrade, gradeExpiredAt, List.of());
	}

	private CompanyRegistered resolveCompany(Long sellerId) {
		return companyRegisteredRepository.findBySellerId(sellerId);
	}

	private String resolveCompanyName(CompanyRegistered company) {
		if (company == null || company.getCompanyName() == null) {
			return "";
		}
		return company.getCompanyName();
	}

	private SellerGrade resolveCompanyGrade(CompanyRegistered company) {
		if (company == null || company.getCompanyId() == null) {
			return null;
		}
		return sellerGradeRepository.findByCompanyId(company.getCompanyId());
	}

	private String resolveCompanyGradeLabel(SellerGrade grade) {
		if (grade == null || grade.getGrade() == null) {
			return "";
		}
		return grade.getGrade().name();
	}

	private String resolveGradeExpiredAt(SellerGrade grade) {
		if (grade == null || grade.getExpiredAt() == null) {
			return "";
		}
		return grade.getExpiredAt().format(dateFormatter);
	}

	private List<SellerManagerStatusResponse> resolveManagers(Long ownerSellerId) {
		List<Invitation> invitations = invitationRepository.findBySellerIdAndStatusIn(
				ownerSellerId,
				List.of(InvitationStatus.ACCEPTED.name())
		);

		if (invitations.isEmpty()) {
			return List.of();
		}

		List<SellerManagerStatusResponse> managers = new ArrayList<>();
		for (Invitation invitation : invitations) {
			String email = invitation.getEmail();
			if (email == null) {
				continue;
			}
			Seller seller = sellerRepository.findByLoginId(email);
			if (seller == null) {
				continue;
			}

			String role = seller.getRole() == null ? "" : seller.getRole().name();
			String status = seller.getStatus() == null ? "" : seller.getStatus().name();
			managers.add(new SellerManagerStatusResponse(
					seller.getSellerId(),
					seller.getName(),
					seller.getLoginId(),
					role,
					status
			));
		}

		return managers;
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}
}
