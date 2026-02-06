package com.deskit.deskit.account.service;

import com.deskit.deskit.account.dto.MemberIdResponse;
import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.MemberRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MemberIdentityService {

    private final MemberRepository memberRepository;

    public MemberIdResponse getMemberId(CustomOAuth2User user) {
        return new MemberIdResponse(resolveMemberId(user));
    }

    private Long resolveMemberId(CustomOAuth2User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }

        Long memberId = tryExtractMemberId(user);
        if (memberId != null) {
            return memberId;
        }

        String loginId = user.getUsername();
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
        }

        Member member = memberRepository.findByLoginId(loginId);
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
        }

        return member.getMemberId();
    }

    private Long tryExtractMemberId(CustomOAuth2User user) {
        Map<String, Object> attributes = user.getAttributes();
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        Object value = attributes.get("memberId");
        if (value == null) {
            value = attributes.get("member_id");
        }
        if (value == null) {
            value = attributes.get("id");
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        return null;
    }
}
