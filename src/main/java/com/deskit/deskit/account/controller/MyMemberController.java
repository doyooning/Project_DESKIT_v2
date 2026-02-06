package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.dto.MemberIdResponse;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.service.MemberIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/my")
public class MyMemberController {

    private final MemberIdentityService memberIdentityService;

    @GetMapping("/member-id")
    public ResponseEntity<MemberIdResponse> getMemberId(
            @AuthenticationPrincipal CustomOAuth2User user
    ) {
        return ResponseEntity.ok(memberIdentityService.getMemberId(user));
    }
}
