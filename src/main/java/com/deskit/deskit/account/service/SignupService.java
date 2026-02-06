package com.deskit.deskit.account.service;

import com.deskit.deskit.account.dto.PendingSignupResponse;
import com.deskit.deskit.account.dto.SocialSignupRequest;
import com.deskit.deskit.account.entity.CompanyRegistered;
import com.deskit.deskit.account.entity.Invitation;
import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.entity.SellerGrade;
import com.deskit.deskit.account.entity.SellerRegister;
import com.deskit.deskit.account.enums.CompanyStatus;
import com.deskit.deskit.account.enums.InvitationStatus;
import com.deskit.deskit.account.enums.JobCategory;
import com.deskit.deskit.account.enums.MBTI;
import com.deskit.deskit.account.enums.MemberStatus;
import com.deskit.deskit.account.enums.SellerGradeEnum;
import com.deskit.deskit.account.enums.SellerGradeStatus;
import com.deskit.deskit.account.enums.SellerRole;
import com.deskit.deskit.account.enums.SellerStatus;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.CompanyRegisteredRepository;
import com.deskit.deskit.account.repository.InvitationRepository;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.account.repository.SellerGradeRepository;
import com.deskit.deskit.account.repository.SellerRegisterRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.ai.evaluate.service.SellerPlanEvaluationService;
import com.deskit.deskit.common.util.verification.PhoneSendRequest;
import com.deskit.deskit.common.util.verification.PhoneSendResponse;
import com.deskit.deskit.common.util.verification.PhoneVerifyRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SignupService {

    // 세션 키: 인증받을 번호
    private static final String SESSION_PHONE_NUMBER = "pendingPhoneNumber";

    // 세션 키: 인증코드
    private static final String SESSION_PHONE_CODE = "pendingPhoneCode";

    // 세션 키: 인증 확인 여부
    private static final String SESSION_PHONE_VERIFIED = "pendingPhoneVerified";

    private final MemberRepository memberRepository;
    private final SellerRepository sellerRepository;
    private final CompanyRegisteredRepository companyRegisteredRepository;
    private final SellerRegisterRepository sellerRegisterRepository;
    private final SellerGradeRepository sellerGradeRepository;
    private final InvitationRepository invitationRepository;
    private final SellerPlanEvaluationService sellerPlanEvaluationService;

    public ResponseEntity<?> pending(CustomOAuth2User user) {
        if (user == null) {
            return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
        }

        if (!user.isNewUser()) {
            return new ResponseEntity<>("already signed up", HttpStatus.CONFLICT);
        }

        PendingSignupResponse response = PendingSignupResponse.builder()
                .username(user.getUsername())
                .name(user.getName())
                .email(user.getEmail())
                .build();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<?> sendPhoneCode(
            CustomOAuth2User user,
            PhoneSendRequest request,
            HttpSession session
    ) {
        if (user == null) {
            return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
        }

        if (!user.isNewUser()) {
            return new ResponseEntity<>("already signed up", HttpStatus.CONFLICT);
        }

        String phoneNumber = normalizePhoneDigits(request.getPhoneNumber());
        if (phoneNumber == null) {
            return new ResponseEntity<>("phone number required", HttpStatus.BAD_REQUEST);
        }
        if (!isValidPhoneDigits(phoneNumber)) {
            return new ResponseEntity<>("invalid phone number", HttpStatus.BAD_REQUEST);
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));

        session.setAttribute(SESSION_PHONE_NUMBER, phoneNumber);
        session.setAttribute(SESSION_PHONE_CODE, code);
        session.setAttribute(SESSION_PHONE_VERIFIED, false);

        PhoneSendResponse response = PhoneSendResponse.builder()
                .message("verification code generated")
                .code(code)
                .build();

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    public ResponseEntity<?> verifyPhoneCode(
            CustomOAuth2User user,
            PhoneVerifyRequest request,
            HttpSession session
    ) {
        if (user == null) {
            return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
        }

        if (!user.isNewUser()) {
            return new ResponseEntity<>("already signed up", HttpStatus.CONFLICT);
        }

        String phoneNumber = normalizePhoneDigits(request.getPhoneNumber());
        String code = request.getCode();
        if (phoneNumber == null || !isValidPhoneDigits(phoneNumber)) {
            return new ResponseEntity<>("invalid phone number", HttpStatus.BAD_REQUEST);
        }

        String storedPhone = (String) session.getAttribute(SESSION_PHONE_NUMBER);
        String storedCode = (String) session.getAttribute(SESSION_PHONE_CODE);

        if (!Objects.equals(phoneNumber, storedPhone) || !Objects.equals(code, storedCode)) {
            return new ResponseEntity<>("verification failed", HttpStatus.BAD_REQUEST);
        }

        session.setAttribute(SESSION_PHONE_VERIFIED, true);

        return new ResponseEntity<>("verified", HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<?> completeSignup(
            CustomOAuth2User user,
            SocialSignupRequest request,
            HttpServletResponse response,
            HttpSession session
    ) {
        if (user == null) {
            return new ResponseEntity<>("unauthorized", HttpStatus.UNAUTHORIZED);
        }

        if (!user.isNewUser()) {
            return new ResponseEntity<>("already signed up", HttpStatus.CONFLICT);
        }

        Member existMember = memberRepository.findByLoginId(user.getEmail());
        if (existMember != null) {
            return new ResponseEntity<>("already signed up", HttpStatus.CONFLICT);
        }

        Seller existSeller = sellerRepository.findByLoginId(user.getEmail());
        if (existSeller != null) {
            return new ResponseEntity<>("already signed up", HttpStatus.CONFLICT);
        }

        Boolean verified = (Boolean) session.getAttribute(SESSION_PHONE_VERIFIED);
        if (verified == null || !verified) {
            return new ResponseEntity<>("phone verification required", HttpStatus.BAD_REQUEST);
        }

        String storedPhone = (String) session.getAttribute(SESSION_PHONE_NUMBER);
        String requestPhone = normalizePhoneDigits(request.getPhoneNumber());
        if (requestPhone == null || !isValidPhoneDigits(requestPhone)) {
            return new ResponseEntity<>("invalid phone number", HttpStatus.BAD_REQUEST);
        }
        if (!Objects.equals(storedPhone, requestPhone)) {
            return new ResponseEntity<>("phone number mismatch", HttpStatus.BAD_REQUEST);
        }

        String memberTypeRaw = trimToNull(request.getMemberType());
        if (memberTypeRaw == null) {
            return new ResponseEntity<>("member type required", HttpStatus.BAD_REQUEST);
        }

        String memberType = normalizeMemberType(memberTypeRaw);
        if (memberType == null) {
            return new ResponseEntity<>("unsupported member type", HttpStatus.BAD_REQUEST);
        }

        if ("GENERAL".equals(memberType)) {
            return completeGeneralSignup(user, request, response, session, storedPhone);
        }

        if ("SELLER".equals(memberType)) {
            return completeSellerSignup(user, request, response, session, storedPhone);
        }

        return new ResponseEntity<>("unsupported member type", HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<?> completeGeneralSignup(
            CustomOAuth2User user,
            SocialSignupRequest request,
            HttpServletResponse response,
            HttpSession session,
            String storedPhone
    ) {
        Member member = Member.builder()
                .loginId(user.getEmail())
                .name(user.getName())
                .role("ROLE_MEMBER")
                .phone(storedPhone)
                .profile(user.getProfileUrl())
                .status(MemberStatus.ACTIVE)
                .mbti(MBTI.valueOf(trimToNull(String.valueOf(request.getMbti()))))
                .jobCategory(JobCategory.valueOf(trimToNull(String.valueOf(request.getJobCategory()))))
                .isAgreed(request.isAgreed())
                .build();

        memberRepository.save(member);

        clearAuthCookies(response);
        clearPhoneSession(session);

        return new ResponseEntity<>("회원가입이 완료되었습니다!", HttpStatus.OK);
    }

    private ResponseEntity<?> completeSellerSignup(
            CustomOAuth2User user,
            SocialSignupRequest request,
            HttpServletResponse response,
            HttpSession session,
            String storedPhone
    ) {
        String inviteToken = trimToNull(request.getInviteToken());
        if (inviteToken != null) {
            return completeInvitedSellerSignup(user, request, response, session, storedPhone, inviteToken);
        }

        String businessNumber = normalizeBusinessDigits(request.getBusinessNumber());
        if (businessNumber == null) {
            return new ResponseEntity<>("business number required", HttpStatus.BAD_REQUEST);
        }
        if (!isValidBusinessDigits(businessNumber)) {
            return new ResponseEntity<>("invalid business number", HttpStatus.BAD_REQUEST);
        }

        String companyName = trimToNull(request.getCompanyName());
        if (companyName == null) {
            return new ResponseEntity<>("company name required", HttpStatus.BAD_REQUEST);
        }

        String planFileBase64 = trimToNull(request.getPlanFileBase64());
        if (planFileBase64 == null) {
            return new ResponseEntity<>("plan file required", HttpStatus.BAD_REQUEST);
        }

        CompanyRegistered existingCompany = companyRegisteredRepository.findByBusinessNumber(businessNumber);
        if (existingCompany != null
                && CompanyStatus.ACTIVE.name().equalsIgnoreCase(existingCompany.getCompanyStatus().toString())) {
            return new ResponseEntity<>("business number already registered", HttpStatus.CONFLICT);
        }

        String description = trimToNull(request.getDescription());

        byte[] planFile;

        try {
            planFile = decodePlanFile(planFileBase64);
        } catch (IllegalArgumentException ex) {
            return new ResponseEntity<>("invalid plan file payload", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();

        Seller seller = Seller.builder()
                .loginId(user.getEmail())
                .name(user.getName())
                .phone(storedPhone)
                .profile(user.getProfileUrl())
                .role(SellerRole.valueOf(SellerRole.ROLE_SELLER_OWNER.name()))
                .status(SellerStatus.valueOf(SellerStatus.PENDING.name()))
                .createdAt(now)
                .updatedAt(now)
                .isAgreed(request.isAgreed())
                .build();

        sellerRepository.save(seller);

        CompanyRegistered companyRegistered = CompanyRegistered.builder()
                .companyName(companyName)
                .businessNumber(businessNumber)
                .sellerId(seller.getSellerId())
                .createdAt(now)
                .companyStatus(CompanyStatus.valueOf(CompanyStatus.ACTIVE.name()))
                .build();

        companyRegisteredRepository.save(companyRegistered);

        SellerRegister sellerRegister = SellerRegister.builder()
                .planFile(planFile)
                .sellerId(seller.getSellerId())
                .description(description)
                .companyName(companyName)
                .build();

        SellerRegister savedRegister = sellerRegisterRepository.save(sellerRegister);

        sellerPlanEvaluationService.evaluateAndSave(savedRegister);

        SellerGrade sellerGrade = SellerGrade.builder()
                .grade(SellerGradeEnum.valueOf(SellerGradeEnum.C.name()))
                .gradeStatus(SellerGradeStatus.valueOf(SellerGradeStatus.REVIEW.name()))
                .createdAt(now)
                .updatedAt(now)
                .expiredAt(now.plusYears(1))
                .companyId(companyRegistered.getCompanyId())
                .build();

        sellerGradeRepository.save(sellerGrade);

        clearAuthCookies(response);
        clearPhoneSession(session);

        return new ResponseEntity<>("판매자 회원가입 신청이 완료되었습니다.", HttpStatus.OK);
    }

    private ResponseEntity<?> completeInvitedSellerSignup(
            CustomOAuth2User user,
            SocialSignupRequest request,
            HttpServletResponse response,
            HttpSession session,
            String storedPhone,
            String inviteToken
    ) {
        Invitation invitation = invitationRepository.findByToken(inviteToken);
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

        String inviteEmail = trimToNull(invitation.getEmail());
        String signupEmail = trimToNull(user.getEmail());
        if (inviteEmail == null || signupEmail == null || !inviteEmail.equalsIgnoreCase(signupEmail)) {
            return new ResponseEntity<>("invitation email mismatch", HttpStatus.BAD_REQUEST);
        }

        Seller ownerSeller = sellerRepository.findById(invitation.getSellerId()).orElse(null);
        if (ownerSeller == null) {
            return new ResponseEntity<>("invitation owner not found", HttpStatus.NOT_FOUND);
        }

        String businessNumber = normalizeBusinessDigits(request.getBusinessNumber());
        if (businessNumber == null) {
            return new ResponseEntity<>("business number required", HttpStatus.BAD_REQUEST);
        }
        if (!isValidBusinessDigits(businessNumber)) {
            return new ResponseEntity<>("invalid business number", HttpStatus.BAD_REQUEST);
        }

        String companyName = trimToNull(request.getCompanyName());
        if (companyName == null) {
            return new ResponseEntity<>("company name required", HttpStatus.BAD_REQUEST);
        }

        CompanyRegistered ownerCompany = companyRegisteredRepository.findBySellerId(ownerSeller.getSellerId());
        if (ownerCompany == null) {
            return new ResponseEntity<>("owner company not found", HttpStatus.NOT_FOUND);
        }

        String ownerBusinessNumber = normalizeBusinessDigits(ownerCompany.getBusinessNumber());
        String ownerCompanyName = trimToNull(ownerCompany.getCompanyName());
        if (!businessNumber.equals(ownerBusinessNumber)) {
            return new ResponseEntity<>("business number mismatch", HttpStatus.BAD_REQUEST);
        }

        if (!companyName.equals(ownerCompanyName)) {
            return new ResponseEntity<>("company name mismatch", HttpStatus.BAD_REQUEST);
        }

        Seller seller = Seller.builder()
                .loginId(user.getEmail())
                .name(user.getName())
                .phone(storedPhone)
                .profile(user.getProfileUrl())
                .role(SellerRole.valueOf(SellerRole.ROLE_SELLER_MANAGER.name()))
                .status(SellerStatus.valueOf(SellerStatus.ACTIVE.name()))
                .createdAt(now)
                .updatedAt(now)
                .isAgreed(true)
                .build();

        sellerRepository.save(seller);

        invitation.setStatus(InvitationStatus.valueOf(InvitationStatus.ACCEPTED.name()));
        invitation.setUpdatedAt(now);
        invitationRepository.save(invitation);

        clearAuthCookies(response);
        clearPhoneSession(session);

        return new ResponseEntity<>("회원가입이 완료되었습니다.", HttpStatus.OK);
    }

    private void clearAuthCookies(HttpServletResponse response) {
        response.addCookie(expireCookie("access"));
        response.addCookie(expireCookie("refresh"));
    }

    private Cookie expireCookie(String key) {
        Cookie cookie = new Cookie(key, "");
        cookie.setMaxAge(0);
        // cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }

    private void clearPhoneSession(HttpSession session) {
        session.removeAttribute(SESSION_PHONE_NUMBER);
        session.removeAttribute(SESSION_PHONE_CODE);
        session.removeAttribute(SESSION_PHONE_VERIFIED);
        session.invalidate();
        SecurityContextHolder.clearContext();
    }

    private byte[] decodePlanFile(String encodedPlanFile) {
        int commaIndex = encodedPlanFile.indexOf(',');
        String payload = commaIndex >= 0 ? encodedPlanFile.substring(commaIndex + 1) : encodedPlanFile;
        return Base64.getDecoder().decode(payload);
    }

    private String trimToNull(String value) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private String normalizeMemberType(String memberType) {
        String normalized = memberType == null ? null : memberType.trim().toUpperCase();
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if ("GENERAL".equals(normalized) || "SELLER".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String normalizePhoneDigits(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        String digits = phoneNumber.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private boolean isValidPhoneDigits(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("^\\d{11}$");
    }

    private String normalizeBusinessDigits(String businessNumber) {
        if (businessNumber == null) {
            return null;
        }
        String digits = businessNumber.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private boolean isValidBusinessDigits(String businessNumber) {
        return businessNumber != null && businessNumber.matches("^\\d{10}$");
    }
}
