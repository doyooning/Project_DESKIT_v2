package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.dto.MySettingsResponse;
import com.deskit.deskit.account.dto.MySettingsUpdateRequest;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.service.MySettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/my/settings")
public class MySettingsController {

    private final MySettingsService mySettingsService;

    @GetMapping
    public ResponseEntity<MySettingsResponse> getSettings(
            @AuthenticationPrincipal CustomOAuth2User user
    ) {
        return ResponseEntity.ok(mySettingsService.getSettings(user));
    }

    @PatchMapping
    public ResponseEntity<MySettingsResponse> updateSettings(
            @AuthenticationPrincipal CustomOAuth2User user,
            @RequestBody MySettingsUpdateRequest request
    ) {
        return ResponseEntity.ok(mySettingsService.updateSettings(user, request));
    }
}
