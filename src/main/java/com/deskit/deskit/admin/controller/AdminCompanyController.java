package com.deskit.deskit.admin.controller;

import com.deskit.deskit.admin.dto.AdminCompanyPageResponse;
import com.deskit.deskit.admin.service.AdminCompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/companies")
public class AdminCompanyController {

	private final AdminCompanyService adminCompanyService;

	@GetMapping
	public ResponseEntity<?> listCompanies(
			Authentication authentication,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String companyName,
			@RequestParam(required = false) String businessNumber,
			@RequestParam(required = false) String grade,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String fromDate,
			@RequestParam(required = false) String toDate
	) {
		if (!isAdmin(authentication)) {
			return new ResponseEntity<>("forbidden", HttpStatus.FORBIDDEN);
		}
		AdminCompanyPageResponse response = adminCompanyService.listCompanies(
				page,
				size,
				keyword,
				companyName,
				businessNumber,
				grade,
				status,
				fromDate,
				toDate
		);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	private boolean isAdmin(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			return false;
		}
		Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
		return authorities.stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
	}
}
