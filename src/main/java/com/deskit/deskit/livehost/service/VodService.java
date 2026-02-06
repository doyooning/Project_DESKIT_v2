package com.deskit.deskit.livehost.service;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.livehost.common.enums.VodStatus;
import com.deskit.deskit.livehost.common.exception.BusinessException;
import com.deskit.deskit.livehost.common.exception.ErrorCode;
import com.deskit.deskit.livehost.entity.Vod;
import com.deskit.deskit.livehost.repository.VodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class VodService {

    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d*)-(\\d*)");

    private final VodRepository vodRepository;
    private final AwsS3Service s3Service;
    private final SellerRepository sellerRepository;

    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> streamVod(Long vodId, String rangeHeader) {
        Vod vod = vodRepository.findById(vodId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOD_NOT_FOUND));

        if (vod.getStatus() != VodStatus.PUBLIC && !canAccessPrivateVod(vod)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS);
        }

        String vodUrl = vod.getVodUrl();
        if (vodUrl == null || vodUrl.isBlank()) {
            throw new BusinessException(ErrorCode.VOD_NOT_FOUND);
        }

        long totalSize = s3Service.getObjectSize(vodUrl);
        Long start = null;
        Long end = null;
        if (rangeHeader != null) {
            Matcher matcher = RANGE_PATTERN.matcher(rangeHeader);
            if (matcher.matches()) {
                String startGroup = matcher.group(1);
                String endGroup = matcher.group(2);
                if (!startGroup.isBlank()) {
                    start = Long.parseLong(startGroup);
                }
                if (!endGroup.isBlank()) {
                    end = Long.parseLong(endGroup);
                }
                if (start != null && end == null) {
                    end = totalSize > 0 ? totalSize - 1 : null;
                }
            }
        }

        InputStream inputStream = s3Service.getObjectStream(vodUrl, start, end);

        long contentLength = totalSize;
        if (start != null && end != null && totalSize > 0) {
            contentLength = end - start + 1;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.add(HttpHeaders.CONTENT_TYPE, "video/mp4");
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));

        if (start != null && end != null && totalSize > 0) {
            headers.add(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalSize);
            return new ResponseEntity<>(new InputStreamResource(inputStream), headers, HttpStatus.PARTIAL_CONTENT);
        }

        return new ResponseEntity<>(new InputStreamResource(inputStream), headers, HttpStatus.OK);
    }

    private boolean canAccessPrivateVod(Vod vod) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities != null && authorities.stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            return true;
        }

        if (authorities != null && authorities.stream()
                .anyMatch(authority -> authority.getAuthority().startsWith("ROLE_SELLER"))) {
            String loginId = resolveLoginId(authentication.getPrincipal());
            if (loginId == null || loginId.isBlank()) {
                return false;
            }
            Seller seller = sellerRepository.findByLoginId(loginId);
            if (seller == null) {
                return false;
            }
            return vod.getBroadcast().getSeller().getSellerId().equals(seller.getSellerId());
        }

        return false;
    }

    private String resolveLoginId(Object principal) {
        if (principal == null) {
            return null;
        }
        if (principal instanceof CustomOAuth2User oauthUser) {
            String username = oauthUser.getUsername();
            if (username != null && !username.isBlank()) {
                return username;
            }
            return oauthUser.getEmail();
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal.toString();
    }
}
