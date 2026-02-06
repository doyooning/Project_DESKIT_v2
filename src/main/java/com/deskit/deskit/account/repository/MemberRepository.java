package com.deskit.deskit.account.repository;

import com.deskit.deskit.account.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // Lookup user by social login username.
    Member findByLoginId(String loginId);
}
