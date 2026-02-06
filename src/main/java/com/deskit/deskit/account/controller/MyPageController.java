package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.dto.MyPageResponse;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.service.MyPageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/api/my")
    @ResponseBody
    public MyPageResponse myAPI(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomOAuth2User user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("");

        return myPageService.buildMyPageResponse(user, role);
    }

}
