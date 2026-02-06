package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.service.InvitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    public ResponseEntity<?> inviteSeller(
            @AuthenticationPrincipal CustomOAuth2User user,
            @RequestBody Map<String, String> payload
    ) {
        return invitationService.inviteSeller(user, payload);
    }

    @GetMapping("/managers")
    public ResponseEntity<?> listManagers(@AuthenticationPrincipal CustomOAuth2User user) {
        return invitationService.listManagers(user);
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateInvitation(@RequestParam("token") String token) {
        return invitationService.validateInvitation(token);
    }
}
