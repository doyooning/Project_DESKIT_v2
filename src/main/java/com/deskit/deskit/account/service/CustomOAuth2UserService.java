package com.deskit.deskit.account.service;

import com.deskit.deskit.account.dto.UserDTO;
import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.enums.MemberStatus;
import com.deskit.deskit.account.enums.SellerStatus;
import com.deskit.deskit.account.oauth.*;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.admin.entity.Admin;
import com.deskit.deskit.admin.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;
    private final AdminRepository adminRepository;
    private final Environment environment;
    private final long rejoinBlockDays;

    public CustomOAuth2UserService(
            MemberRepository memberRepository,
            SellerRepository sellerRepository,
            AdminRepository adminRepository,
            Environment environment,
            @Value("${deskit.rejoin.block-days:30}") long rejoinBlockDays
    ) {

        this.memberRepository = memberRepository;
        this.sellerRepository = sellerRepository;
        this.adminRepository = adminRepository;
        this.environment = environment;
        this.rejoinBlockDays = rejoinBlockDays;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest);
        System.out.println(oAuth2User);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2Response oAuth2Response = null;

        switch (registrationId) {
            case "google" -> oAuth2Response = new GoogleResponse(oAuth2User.getAttributes());

            case "naver" -> oAuth2Response = new NaverResponse(oAuth2User.getAttributes());

            case "kakao" -> oAuth2Response = new KakaoResponse(oAuth2User.getAttributes());

            default -> {
                return null;
            }
        }

        String username = oAuth2Response.getProvider()+" "+oAuth2Response.getProviderId();
        Admin existAdmin = adminRepository.findByLoginId(oAuth2Response.getEmail());
        Member existMember = memberRepository.findByLoginId(oAuth2Response.getEmail());
        Seller existSeller = sellerRepository.findByLoginId(oAuth2Response.getEmail());

        if (existAdmin != null) {
            UserDTO userDTO = UserDTO.builder()
                    .username(existAdmin.getLoginId())
                    .name(existAdmin.getName())
                    .email(existAdmin.getLoginId())
                    .role(existAdmin.getRole())
                    .newUser(false)
                    .profileUrl(oAuth2Response.getProfileUrl() == null ? "" : oAuth2Response.getProfileUrl())
                    .build();

            return new CustomOAuth2User(userDTO);
        }

        // Member에도 없고 Seller에도 없는 경우 -> 신규 등록, role 임시 부여(GUEST)
        if (existMember == null && existSeller == null) {

            // Build user info for a pending signup user.
            UserDTO userDTO = UserDTO.builder()
                    .username(username)
                    .name(oAuth2Response.getName())
                    .email(oAuth2Response.getEmail())
                    .role("ROLE_GUEST")
                    .newUser(true)
                    .profileUrl(oAuth2Response.getProfileUrl() == null ? "" : oAuth2Response.getProfileUrl())
                    .build();

            return new CustomOAuth2User(userDTO);
        }

        // Member에 존재할 경우 -> Member 로그인
        else if (existMember != null) {

            if (existMember.getStatus() == MemberStatus.INACTIVE) {
                ensureRejoinAllowed(existMember.getUpdatedAt());
                existMember.setStatus(MemberStatus.ACTIVE);
            }

            existMember.setLoginId(oAuth2Response.getEmail());
            existMember.setName(oAuth2Response.getName());

            memberRepository.save(existMember);

            UserDTO userDTO = UserDTO.builder()
                    .username(existMember.getLoginId())
                    .name(oAuth2Response.getName())
                    .email(oAuth2Response.getEmail())
                    .role(existMember.getRole())
                    .newUser(false)
                    .profileUrl(oAuth2Response.getProfileUrl() == null ? "" : oAuth2Response.getProfileUrl())
                    .build();

            return new CustomOAuth2User(userDTO);
        }

        // Seller에 존재할 경우 -> Seller 로그인
        else {
            if (existSeller.getStatus() == SellerStatus.PENDING) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("seller_pending", "관리자 승인 후 로그인 가능합니다.", null)
                );
            }
            if (existSeller.getStatus() == SellerStatus.INACTIVE) {
                ensureRejoinAllowed(existSeller.getUpdatedAt());
                existSeller.setStatus(SellerStatus.ACTIVE);
            }

            existSeller.setLoginId(oAuth2Response.getEmail());
            existSeller.setName(oAuth2Response.getName());

            sellerRepository.save(existSeller);

            UserDTO userDTO = UserDTO.builder()
                    .username(existSeller.getLoginId())
                    .name(existSeller.getName())
                    .email(oAuth2Response.getEmail())
                    .role(existSeller.getRole().toString())
                    .newUser(false)
                    .profileUrl(oAuth2Response.getProfileUrl() == null ? "" : oAuth2Response.getProfileUrl())
                    .build();

            return new CustomOAuth2User(userDTO);
        }
    }

    private void ensureRejoinAllowed(LocalDateTime updatedAt) {
        if (!isRejoinRestrictionEnabled()) {
            return;
        }

        if (updatedAt == null) {
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(rejoinBlockDays);
        if (updatedAt.isAfter(cutoff)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("rejoin_blocked", "탈퇴 후 1개월 이내에는 재가입할 수 없습니다.", null)
            );
        }
    }

    private boolean isRejoinRestrictionEnabled() {
        if (rejoinBlockDays <= 0) {
            return false;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equalsIgnoreCase(profile)) {
                return false;
            }
        }
        return true;
    }
}
