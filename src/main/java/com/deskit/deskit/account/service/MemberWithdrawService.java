package com.deskit.deskit.account.service;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.enums.MemberStatus;
import com.deskit.deskit.account.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberWithdrawService {

    private final MemberRepository memberRepository;

    public void withdraw(String loginId) {
        Member member = memberRepository.findByLoginId(loginId);
        if (member == null) {
            throw new EntityNotFoundException("member not found");
        }
        if (member.getStatus() == MemberStatus.INACTIVE) {
            throw new IllegalStateException("이미 탈퇴된 회원입니다.");
        }

        member.setStatus(MemberStatus.INACTIVE);
    }
}
