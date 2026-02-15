package com.deskit.deskit.account.service;

import com.deskit.deskit.account.dto.SellerManagerResponse;
import com.deskit.deskit.account.entity.CompanyRegistered;
import com.deskit.deskit.account.entity.Invitation;
import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.enums.InvitationStatus;
import com.deskit.deskit.account.enums.SellerRole;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.InvitationRepository;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.account.repository.CompanyRegisteredRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private static final int INVITATION_LIMIT = 2;

    private static final long INVITATION_EXPIRY_HOURS = 24L;

    private final InvitationRepository invitationRepository;
    private final SellerRepository sellerRepository;
    private final MemberRepository memberRepository;
    private final CompanyRegisteredRepository companyRegisteredRepository;
    private final InviteEmailService inviteEmailService;
    private final InvitationQueryService invitationQueryService;
    @Value("${app.web-base-url:http://localhost:5173}")
    private String webBaseUrl;

    @Transactional
    public ResponseEntity<?> inviteSeller(CustomOAuth2User user, Map<String, String> payload) {
        if (user == null) {
            return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
        }

        String role = user.getAuthorities().iterator().next().getAuthority();
        if (!SellerRole.ROLE_SELLER_OWNER.name().equals(role)) {
            return new ResponseEntity<>("owner role required", HttpStatus.FORBIDDEN);
        }

        String email = trimToNull(payload.get("email"));
        if (email == null) {
            return new ResponseEntity<>("email required", HttpStatus.BAD_REQUEST);
        }

        if (invitationRepository.existsByEmailAndStatusIn(
                email,
                List.of(InvitationStatus.PENDING.name(), InvitationStatus.ACCEPTED.name()))
        ) {
            return new ResponseEntity<>("duplicate invitation", HttpStatus.CONFLICT);
        }

        Member existingUser = memberRepository.findByLoginId(email);
        if (existingUser != null) {
            return new ResponseEntity<>("email already registered", HttpStatus.CONFLICT);
        }

        Seller ownerSeller = sellerRepository.findByLoginId(user.getUsername());
        if (ownerSeller == null) {
            return new ResponseEntity<>("owner seller not found", HttpStatus.NOT_FOUND);
        }

        long inviteCount = invitationRepository.countBySellerIdAndStatusIn(
                ownerSeller.getSellerId(),
                List.of(InvitationStatus.PENDING.name(), InvitationStatus.ACCEPTED.name())
        );
        if (inviteCount >= INVITATION_LIMIT) {
            return new ResponseEntity<>("invitation limit reached", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusHours(INVITATION_EXPIRY_HOURS);
        String token = UUID.randomUUID() + "-" + expiresAt.toEpochSecond(ZoneOffset.UTC);

        Invitation invitation = Invitation.builder()
                .email(email)
                .createdAt(now)
                .updatedAt(now)
                .expiredAt(expiresAt)
                .status(InvitationStatus.valueOf(InvitationStatus.PENDING.name()))
                .token(token)
                .sellerId(ownerSeller.getSellerId())
                .build();

        invitationRepository.save(invitation);

        String inviteUrl = buildWebUrl("/signup?invite=" +
                URLEncoder.encode(token, StandardCharsets.UTF_8));

        String recipientName = extractNameFromEmail(email);
        CompanyRegistered companyRegistered = companyRegisteredRepository.findBySellerId(ownerSeller.getSellerId());
        String businessName = companyRegistered == null ? "" : companyRegistered.getCompanyName();
        String inviterName = user.getName() == null || user.getName().isBlank()
                ? ownerSeller.getName()
                : user.getName();

        try {
            inviteEmailService.sendInviteMail(
                    email,
                    inviteUrl,
                    recipientName,
                    businessName,
                    inviterName,
                    expiresAt
            );
        } catch (IOException ex) {
            throw new IllegalStateException("invite email send failed", ex);
        }

        return new ResponseEntity<>("invitation sent", HttpStatus.OK);
    }

    public ResponseEntity<?> listManagers(CustomOAuth2User user) {
        if (user == null) {
            return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
        }

        String role = user.getAuthorities().iterator().next().getAuthority();
        if (!SellerRole.ROLE_SELLER_OWNER.name().equals(role)) {
            return new ResponseEntity<>("owner role required", HttpStatus.FORBIDDEN);
        }

        Seller ownerSeller = sellerRepository.findByLoginId(user.getUsername());
        if (ownerSeller == null) {
            return new ResponseEntity<>("owner seller not found", HttpStatus.NOT_FOUND);
        }

        List<SellerManagerResponse> managers = invitationQueryService.findManagersForOwner(ownerSeller.getSellerId());
        return new ResponseEntity<>(managers, HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<?> validateInvitation(String token) {
        String normalizedToken = trimToNull(token);
        if (normalizedToken == null) {
            return new ResponseEntity<>("token required", HttpStatus.BAD_REQUEST);
        }

        Invitation invitation = invitationRepository.findByToken(normalizedToken);
        if (invitation == null) {
            return new ResponseEntity<>("invitation not found", HttpStatus.NOT_FOUND);
        }

        if (!InvitationStatus.PENDING.name().equalsIgnoreCase(String.valueOf(invitation.getStatus()))) {
            return new ResponseEntity<>("invitation already used", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        if (invitation.getExpiredAt() != null && invitation.getExpiredAt().isBefore(now)) {
            invitation.setStatus(InvitationStatus.valueOf(InvitationStatus.EXPIRED.name()));
            invitation.setUpdatedAt(now);
            invitationRepository.save(invitation);
            return new ResponseEntity<>("invitation expired", HttpStatus.GONE);
        }

        Map<String, String> payload = Map.of(
                "email", invitation.getEmail(),
                "expiresAt", invitation.getExpiredAt().toString()
        );

        return new ResponseEntity<>(payload, HttpStatus.OK);
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private String extractNameFromEmail(String email) {
        if (email == null) {
            return "";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        return email.substring(0, atIndex);
    }

    private String buildWebUrl(String path) {
        String base = webBaseUrl == null ? "" : webBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }
}
