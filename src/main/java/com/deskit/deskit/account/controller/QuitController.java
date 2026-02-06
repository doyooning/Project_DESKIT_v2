package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.service.QuitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class QuitController {

	private final QuitService quitService;

	@PostMapping("/api/quit")
	public ResponseEntity<?> quit(
			@AuthenticationPrincipal CustomOAuth2User user,
			Authentication authentication,
			HttpServletRequest request,
			HttpServletResponse response
	) {
		if (user == null || authentication == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("message", "unauthorized"));
		}

		quitService.quit(
				user.getUsername(),
				authentication.getAuthorities(),
				request,
				response
		);

		return ResponseEntity.ok(Map.of("message", "회원 탈퇴가 완료되었습니다."));
	}
}
