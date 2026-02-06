package com.deskit.deskit.account.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
@RequiredArgsConstructor
@Transactional
public class QuitService {

    private final MemberWithdrawService memberWithdrawService;
    private final SellerWithdrawService sellerWithdrawService;
    private final TokenCleanupService tokenCleanupService;

    public void quit(
            String loginId,
            Collection<? extends GrantedAuthority> authorities,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String role = authorities.iterator().next().getAuthority();

        switch (role) {
            case "ROLE_MEMBER" -> memberWithdrawService.withdraw(loginId);
            case "ROLE_SELLER_MANAGER" -> sellerWithdrawService.withdraw(loginId);
            case "ROLE_SELLER_OWNER" ->
                    throw new AccessDeniedException("OWNER 권한은 탈퇴할 수 없습니다.");
            default ->
                    throw new IllegalStateException("지원하지 않는 권한입니다.");
        }

        tokenCleanupService.clear(request, response);
    }
}

