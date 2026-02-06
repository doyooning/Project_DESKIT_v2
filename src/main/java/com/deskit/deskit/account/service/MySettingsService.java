package com.deskit.deskit.account.service;

import com.deskit.deskit.account.dto.MySettingsResponse;
import com.deskit.deskit.account.dto.MySettingsUpdateRequest;
import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.enums.JobCategory;
import com.deskit.deskit.account.enums.MBTI;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.MemberRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MySettingsService {

    private final MemberRepository memberRepository;

    public MySettingsResponse getSettings(CustomOAuth2User user) {
        Member member = resolveMember(user);
        return toResponse(member);
    }

    public MySettingsResponse updateSettings(CustomOAuth2User user, MySettingsUpdateRequest request) {
        Member member = resolveMember(user);

        if (request.mbti() != null) {
            member.setMbti(request.mbti());
        }
        if (request.jobCategory() != null) {
            member.setJobCategory(request.jobCategory());
        }
        if (request.marketingAgreed() != null) {
            member.setAgreed(request.marketingAgreed());
        }

        memberRepository.save(member);
        return toResponse(member);
    }

    private Member resolveMember(CustomOAuth2User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
        }

        Long memberId = tryExtractMemberId(user);
        if (memberId != null) {
            return memberRepository.findById(memberId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found"));
        }

        String loginId = user.getUsername();
        if (loginId == null || loginId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
        }

        Member member = memberRepository.findByLoginId(loginId);
        if (member == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "member not found");
        }

        return member;
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

    private MySettingsResponse toResponse(Member member) {
        MBTI mbti = member.getMbti() == null ? MBTI.NONE : member.getMbti();
        JobCategory jobCategory = member.getJobCategory() == null ? JobCategory.NONE : member.getJobCategory();
        return new MySettingsResponse(mbti, jobCategory, member.isAgreed());
    }
}
