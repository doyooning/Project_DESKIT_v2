package com.deskit.deskit.account.repository;

import com.deskit.deskit.account.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    // Find invitation by token from the email link.
    Invitation findByToken(String token);

    // Check for duplicate invitations for the same email.
    boolean existsByEmailAndStatusIn(String email, Collection<String> statuses);

    // Count invitations for a seller to enforce the invite limit.
    long countBySellerIdAndStatusIn(Long sellerId, Collection<String> statuses);

    List<Invitation> findBySellerIdAndStatusIn(Long sellerId, Collection<String> statuses);

    Invitation findFirstByEmailAndStatusIn(String email, Collection<String> statuses);
}
