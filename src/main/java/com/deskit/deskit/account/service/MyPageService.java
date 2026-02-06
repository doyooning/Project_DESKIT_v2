package com.deskit.deskit.account.service;

import com.deskit.deskit.account.controller.MyPageController;
import com.deskit.deskit.account.dto.MyPageResponse;
import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.enums.JobCategory;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.admin.entity.Admin;
import com.deskit.deskit.admin.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class MyPageService {

	private final MemberRepository memberRepository;
	private final SellerRepository sellerRepository;
	private final AdminRepository adminRepository;

	public MyPageResponse buildMyPageResponse(CustomOAuth2User user, String role) {
		String normalizedRole = role == null ? "" : role.trim();
		String loginId = safe(user.getUsername());
		String name = safe(user.getName());
		String email = safe(user.getEmail());
		String mbti = "";
		String job = "";
		String phone = "";
		String createdAt = "";
		String resolvedRole = normalizedRole;

		Member member = null;
		if ("ROLE_MEMBER".equals(normalizedRole) && !loginId.isBlank()) {
			member = memberRepository.findByLoginId(loginId);
			if (member != null) {
				mbti = resolveMbti(member.getMbti());
				job = resolveJobCategory(member.getJobCategory());
			}
		}

		if ("ROLE_ADMIN".equals(normalizedRole) && !loginId.isBlank()) {
			Admin admin = adminRepository.findByLoginId(loginId);
			if (admin != null) {
				phone = safe(admin.getPhone());
				resolvedRole = safe(admin.getRole());
				createdAt = formatAdminCreatedAt(admin.getCreatedAt());
			}
		}
		String profileUrl = safe(user.getProfileUrl());

		if (email.isEmpty() && !loginId.isEmpty()) {
			email = loginId;
		}

		if (name.isEmpty() && !loginId.isEmpty()) {
			name = member == null ? resolveName(normalizedRole, loginId) : safe(member.getName());
		}

		if (profileUrl.isEmpty() && !loginId.isEmpty()) {
			profileUrl = resolveProfileUrl(normalizedRole, loginId);
		}

		return new MyPageResponse(
				name,
				email,
				resolvedRole,
				resolveMemberCategory(normalizedRole),
				resolveSellerRole(normalizedRole),
				mbti,
				job,
				profileUrl,
				phone,
				createdAt
		);
	}

	private String resolveName(String role, String loginId) {
		return switch (role) {
			case "ROLE_ADMIN" -> {
				Admin admin = adminRepository.findByLoginId(loginId);
				yield admin == null ? "" : safe(admin.getName());
			}
			case "ROLE_MEMBER" -> {
				Member member = memberRepository.findByLoginId(loginId);
				yield member == null ? "" : safe(member.getName());
			}
			default -> {
				if (role.startsWith("ROLE_SELLER")) {
					Seller seller = sellerRepository.findByLoginId(loginId);
					yield seller == null ? "" : safe(seller.getName());
				}
				yield "";
			}
		};
	}

	private String resolveMemberCategory(String role) {
		if (role == null || role.isBlank()) {
			return "";
		}
		return switch (role) {
			case "ROLE_ADMIN" -> "관리자";
			case "ROLE_MEMBER" -> "일반회원";
			case "ROLE_SELLER", "ROLE_SELLER_OWNER", "ROLE_SELLER_MANAGER" -> "판매자";
			default -> "";
		};
	}

	private String resolveProfileUrl(String role, String loginId) {
		return switch (role) {
			case "ROLE_MEMBER" -> {
				Member member = memberRepository.findByLoginId(loginId);
				yield member == null ? "" : safe(member.getProfile());
			}
			default -> {
				if (role != null && role.startsWith("ROLE_SELLER")) {
					Seller seller = sellerRepository.findByLoginId(loginId);
					yield seller == null ? "" : safe(seller.getProfile());
				}
				yield "";
			}
		};
	}

	private String mapJobCategory(JobCategory category) {
		return switch (category) {
			case CREATIVE_TYPE -> "크리에이티브";
			case FLEXIBLE_TYPE -> "프리랜서/유연근무";
			case EDU_RES_TYPE -> "교육/연구";
			case MED_PRO_TYPE -> "의료/전문직";
			case ADMIN_PLAN_TYPE -> "기획/관리";
			default -> "";
		};
	}

	private String resolveSellerRole(String role) {
		if (role == null || role.isBlank()) {
			return "";
		}
		return switch (role) {
			case "ROLE_SELLER_OWNER" -> "대표자";
			case "ROLE_SELLER_MANAGER" -> "매니저";
			default -> "";
		};
	}

	private String resolveMbti(com.deskit.deskit.account.enums.MBTI mbti) {
		if (mbti == null || mbti == com.deskit.deskit.account.enums.MBTI.NONE) {
			return "";
		}
		return mbti.name();
	}

	private String resolveJobCategory(com.deskit.deskit.account.enums.JobCategory jobCategory) {
		if (jobCategory == null || jobCategory == com.deskit.deskit.account.enums.JobCategory.NONE) {
			return "";
		}
		return switch (jobCategory) {
			case CREATIVE_TYPE -> "크리에이티브";
			case FLEXIBLE_TYPE -> "프리랜서/유연근무";
			case EDU_RES_TYPE -> "교육/연구";
			case MED_PRO_TYPE -> "의료/전문직";
			case ADMIN_PLAN_TYPE -> "기획/관리";
			default -> "";
		};
	}

	private String formatAdminCreatedAt(LocalDateTime value) {
		if (value == null) {
			return "";
		}
		return value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
	}

	private String safe(String value) {
		return value == null ? "" : value.trim();
	}
}
