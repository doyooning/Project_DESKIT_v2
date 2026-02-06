package com.deskit.deskit.livehost.common.utils;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.common.exception.ErrorCode; // Deskit의 에러코드로 매핑 필요
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LiveAuthUtils {

    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;

    // 현재 로그인한 일반 회원(Member) 반환
    @Transactional(readOnly = true)
    public Member getCurrentMember() {
        String loginId = getCurrentUserLoginId();
        Member member = memberRepository.findByLoginId(loginId);

        if (member == null) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }

    // 현재 로그인한 판매자(Seller) 반환
    @Transactional(readOnly = true)
    public Seller getCurrentSeller() {
        String loginId = getCurrentUserLoginId();
        Seller seller = sellerRepository.findByLoginId(loginId);

        if (seller == null) {
            throw new BusinessException(ErrorCode.SELLER_NOT_FOUND);
        }
        return seller;
    }

    // SecurityContext에서 사용자 식별자 추출
    private String getCurrentUserLoginId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED_MEMBER);
        }

        Object principal = authentication.getPrincipal();

        // 1. 소셜 로그인 (CustomOAuth2User)
        if (principal instanceof CustomOAuth2User) {
            CustomOAuth2User oauthUser = (CustomOAuth2User) principal;
            String username = oauthUser.getUsername();
            if (username != null && !username.isBlank()) {
                return username;
            }
            return oauthUser.getEmail();
        }

        // 2. 일반 로그인 (UserDetails)
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }

        // 3. 그 외 (String 등)
        return principal.toString();
    }
}