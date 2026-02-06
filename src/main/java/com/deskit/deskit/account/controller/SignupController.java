package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.dto.SocialSignupRequest;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.service.SignupService;
import com.deskit.deskit.common.util.verification.PhoneSendRequest;
import com.deskit.deskit.common.util.verification.PhoneVerifyRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/signup/social")
public class SignupController {

    private final SignupService signupService;

    @GetMapping("/pending")
    public ResponseEntity<?> pending(@AuthenticationPrincipal CustomOAuth2User user) {
        return signupService.pending(user);
    }

    @PostMapping("/phone/send")
    public ResponseEntity<?> sendPhoneCode(
            @AuthenticationPrincipal CustomOAuth2User user,
            @RequestBody PhoneSendRequest request,
            HttpSession session
    ) {
        return signupService.sendPhoneCode(user, request, session);
    }

    @PostMapping("/phone/verify")
    public ResponseEntity<?> verifyPhoneCode(
            @AuthenticationPrincipal CustomOAuth2User user,
            @RequestBody PhoneVerifyRequest request,
            HttpSession session
    ) {
        return signupService.verifyPhoneCode(user, request, session);
    }

    @PostMapping("/complete")
    public ResponseEntity<?> completeSignup(
            @AuthenticationPrincipal CustomOAuth2User user,
            @RequestBody SocialSignupRequest request,
            HttpServletResponse response,
            HttpSession session
    ) {
        return signupService.completeSignup(user, request, response, session);
    }
}
