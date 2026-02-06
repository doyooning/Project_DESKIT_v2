package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.dto.SellerMyPageResponse;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.service.SellerMyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/mypage")
public class SellerMyPageController {

	private final SellerMyPageService sellerMyPageService;

	@GetMapping
	public ResponseEntity<?> getSellerMyPage(@AuthenticationPrincipal CustomOAuth2User user) {
		if (user == null) {
			return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
		}

		String role = user.getAuthorities().iterator().next().getAuthority();
		if (role == null || !role.startsWith("ROLE_SELLER")) {
			return new ResponseEntity<>("seller role required", HttpStatus.FORBIDDEN);
		}

		SellerMyPageResponse response = sellerMyPageService.buildSellerMyPage(user, role);
		return new ResponseEntity<>(response, HttpStatus.OK);
	}
}
