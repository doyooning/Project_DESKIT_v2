package com.deskit.deskit.account.controller;

import com.deskit.deskit.account.entity.Seller;
import com.deskit.deskit.account.enums.SellerRole;
import com.deskit.deskit.account.enums.SellerStatus;
import com.deskit.deskit.account.jwt.JWTUtil;
import com.deskit.deskit.account.repository.RefreshRepository;
import com.deskit.deskit.account.repository.SellerRepository;
import com.deskit.deskit.product.entity.Product;
import com.deskit.deskit.product.repository.ProductRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.test-auth", name = "enabled", havingValue = "true")
@RequestMapping("/api/internal/test-auth")
public class TestAuthController {

    private static final long DEFAULT_ACCESS_TTL_MS = 600_000L;
    private static final long DEFAULT_REFRESH_TTL_MS = 86_400_000L;

    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;

    @org.springframework.beans.factory.annotation.Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @org.springframework.beans.factory.annotation.Value("${app.test-auth.secret:}")
    private String configuredSecret;

    @org.springframework.beans.factory.annotation.Value("${app.test-auth.private-network-only:true}")
    private boolean privateNetworkOnly;

    @PostMapping("/seller-token")
    public ResponseEntity<Map<String, Object>> issueSellerToken(
            @RequestHeader(value = "X-Test-Auth-Secret", required = false) String providedSecret,
            @RequestBody(required = false) SellerTokenIssueRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        validateSecret(providedSecret);
        validateClientIp(httpRequest);

        SellerTokenIssueRequest body = request != null ? request : new SellerTokenIssueRequest();
        Seller seller = resolveSeller(body);

        long accessTtlMs = normalizeTtl(body.getAccessTtlMs(), DEFAULT_ACCESS_TTL_MS);
        long refreshTtlMs = normalizeTtl(body.getRefreshTtlMs(), DEFAULT_REFRESH_TTL_MS);

        String loginId = seller.getLoginId();
        String role = seller.getRole().name();
        String access = jwtUtil.createJwt("access", loginId, role, accessTtlMs);
        String refresh = jwtUtil.createJwt("refresh", loginId, role, refreshTtlMs);
        refreshRepository.save(loginId, refresh, refreshTtlMs);

        httpResponse.setHeader("access", access);
        httpResponse.addCookie(createCookie("access", access, (int) (accessTtlMs / 1000)));
        httpResponse.addCookie(createCookie("refresh", refresh, (int) (refreshTtlMs / 1000)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("issuedAt", Instant.now().toString());
        result.put("sellerId", seller.getSellerId());
        result.put("loginId", loginId);
        result.put("role", role);
        result.put("accessToken", access);
        result.put("refreshToken", refresh);
        result.put("accessExpiresInMs", accessTtlMs);
        result.put("refreshExpiresInMs", refreshTtlMs);

        log.warn("test-auth token issued sellerId={} loginId={} role={} ip={}",
                seller.getSellerId(), loginId, role, resolveClientIp(httpRequest));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sellers/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrapSellers(
            @RequestHeader(value = "X-Test-Auth-Secret", required = false) String providedSecret,
            @RequestBody(required = false) SellerBootstrapRequest request,
            HttpServletRequest httpRequest
    ) {
        validateSecret(providedSecret);
        validateClientIp(httpRequest);

        SellerBootstrapRequest body = request != null ? request : new SellerBootstrapRequest();
        int count = normalizeCount(body.getCount());
        int startIndex = body.getStartIndex() == null ? 1 : Math.max(1, body.getStartIndex());
        String loginPrefix = StringUtils.hasText(body.getLoginPrefix()) ? body.getLoginPrefix().trim() : "k6-temp-seller";
        String namePrefix = StringUtils.hasText(body.getNamePrefix()) ? body.getNamePrefix().trim() : "K6 Temp Seller";
        boolean createProduct = body.getCreateProduct() == null || body.getCreateProduct();

        List<Map<String, Object>> sellers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int sequence = startIndex + i;
            String loginId = loginPrefix + "-" + sequence + "@perf.local";

            Seller seller = sellerRepository.findByLoginId(loginId);
            if (seller == null) {
                seller = Seller.builder()
                        .status(SellerStatus.ACTIVE)
                        .name(namePrefix + " " + sequence)
                        .loginId(loginId)
                        .profile("k6 temp seller")
                        .phone(String.format("010-99%06d", sequence))
                        .role(SellerRole.ROLE_SELLER_OWNER)
                        .isAgreed(true)
                        .build();
                seller = sellerRepository.save(seller);
            }

            Long productId = null;
            if (createProduct) {
                productId = ensureSellerProduct(seller.getSellerId(), sequence);
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sellerId", seller.getSellerId());
            row.put("loginId", seller.getLoginId());
            row.put("role", seller.getRole().name());
            row.put("productId", productId);
            sellers.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", sellers.size());
        result.put("sellers", sellers);
        result.put("issuedAt", Instant.now().toString());
        log.warn("test-auth bootstrap sellers count={} ip={}", sellers.size(), resolveClientIp(httpRequest));
        return ResponseEntity.ok(result);
    }

    private void validateSecret(String providedSecret) {
        if (!StringUtils.hasText(configuredSecret)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "test-auth secret not configured");
        }
        if (!configuredSecret.equals(providedSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "invalid test-auth secret");
        }
    }

    private void validateClientIp(HttpServletRequest request) {
        if (!privateNetworkOnly) {
            return;
        }
        String clientIp = resolveClientIp(request);
        if (!isPrivateOrLoopback(clientIp)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "test-auth endpoint allows private IP only");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isPrivateOrLoopback(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    private Seller resolveSeller(SellerTokenIssueRequest request) {
        if (request.getSellerId() != null) {
            return sellerRepository.findById(request.getSellerId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "seller not found"));
        }
        if (StringUtils.hasText(request.getLoginId())) {
            Seller seller = sellerRepository.findByLoginId(request.getLoginId());
            if (seller == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "seller not found");
            }
            return seller;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sellerId or loginId is required");
    }

    private long normalizeTtl(Long value, long defaultValue) {
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return value;
    }

    private int normalizeCount(Integer count) {
        if (count == null || count <= 0) {
            return 10;
        }
        return Math.min(count, 100);
    }

    private Long ensureSellerProduct(Long sellerId, int sequence) {
        List<Product.Status> allowed = List.of(Product.Status.ON_SALE, Product.Status.READY, Product.Status.LIMITED_SALE);
        List<Product> existing = productRepository.findAllBySellerIdAndStatusInAndDeletedAtIsNullOrderByIdAsc(sellerId, allowed);
        if (!existing.isEmpty()) {
            return existing.get(0).getId();
        }

        Product product = new Product(
                sellerId,
                "K6 Temp Product " + sequence,
                "Temporary product for reservation load test",
                "<p>Temporary product for reservation load test</p>",
                10000,
                7000,
                200,
                5
        );
        product.changeStatus(Product.Status.READY);
        product.changeStatus(Product.Status.ON_SALE);
        Product saved = productRepository.save(product);
        return saved.getId();
    }

    private Cookie createCookie(String key, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        return cookie;
    }

    public static class SellerTokenIssueRequest {
        private Long sellerId;
        private String loginId;
        private Long accessTtlMs;
        private Long refreshTtlMs;

        public Long getSellerId() {
            return sellerId;
        }

        public void setSellerId(Long sellerId) {
            this.sellerId = sellerId;
        }

        public String getLoginId() {
            return loginId;
        }

        public void setLoginId(String loginId) {
            this.loginId = loginId;
        }

        public Long getAccessTtlMs() {
            return accessTtlMs;
        }

        public void setAccessTtlMs(Long accessTtlMs) {
            this.accessTtlMs = accessTtlMs;
        }

        public Long getRefreshTtlMs() {
            return refreshTtlMs;
        }

        public void setRefreshTtlMs(Long refreshTtlMs) {
            this.refreshTtlMs = refreshTtlMs;
        }
    }

    public static class SellerBootstrapRequest {
        private Integer count;
        private Integer startIndex;
        private String loginPrefix;
        private String namePrefix;
        private Boolean createProduct;

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public Integer getStartIndex() {
            return startIndex;
        }

        public void setStartIndex(Integer startIndex) {
            this.startIndex = startIndex;
        }

        public String getLoginPrefix() {
            return loginPrefix;
        }

        public void setLoginPrefix(String loginPrefix) {
            this.loginPrefix = loginPrefix;
        }

        public String getNamePrefix() {
            return namePrefix;
        }

        public void setNamePrefix(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        public Boolean getCreateProduct() {
            return createProduct;
        }

        public void setCreateProduct(Boolean createProduct) {
            this.createProduct = createProduct;
        }
    }
}
