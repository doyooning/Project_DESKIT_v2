package com.deskit.deskit.account.jwt;

import com.deskit.deskit.account.dto.UserDTO;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

@Log4j2
public class JWTFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;
    public JWTFilter(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 헤더에서 access키에 담긴 토큰을 꺼냄
        String accessToken = resolveToken(request);

        // 토큰이 없다면 다음 필터로 넘김
        if (accessToken == null || accessToken.isBlank()) {
            log.info("access token is null");
            filterChain.doFilter(request, response);
            return;
        }

        // 토큰 만료 여부 확인, 만료시 다음 필터로 넘기지 않음
        try {
            jwtUtil.isExpired(accessToken);
        } catch (ExpiredJwtException e) {

            log.info("expired token");

            //response status code
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // 토큰이 access인지 확인 (발급시 페이로드에 명시)
        String category = jwtUtil.getCategory(accessToken);

        if (!category.equals("access")) {

            log.info("access token is not access");

            //response status code
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        //토큰에서 username과 role 획득
        String username = jwtUtil.getUsername(accessToken);
        String role = jwtUtil.getRole(accessToken);
        log.info("username {} role {}", username, role);

        //userDTO를 생성하여 값 set
        UserDTO userDTO = UserDTO.builder()
                .username(username)
                .role(role)
                .newUser("ROLE_GUEST".equals(role))
                .name(jwtUtil.getName(accessToken))
                .email(jwtUtil.getEmail(accessToken))
                .profileUrl(jwtUtil.getProfileUrl(accessToken))
                .build();

        //UserDetails에 회원 정보 객체 담기
        CustomOAuth2User customOAuth2User = new CustomOAuth2User(userDTO);

        //스프링 시큐리티 인증 토큰 생성
        Authentication authToken = new UsernamePasswordAuthenticationToken(customOAuth2User, null, customOAuth2User.getAuthorities());
        log.info("auth token {}", authToken);
        //세션에 사용자 등록
        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        // 1) Authorization: Bearer xxx
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            log.info("Bearer token found");
            return auth.substring(7);
        }

        // 2) 기존 access 헤더도 지원(필요하면 유지)
        String legacy = request.getHeader("access");
        if (legacy != null && !legacy.isBlank()){
            log.info("legacy token found");
            return legacy;
        }


        // 3) Cookie에서 access 읽기
        if (request.getCookies() == null){
            log.info("cookie is null");
            return null;
        }

        for (Cookie c : request.getCookies()) {
            log.info("cookie found: " + c.getName());
            if ("access".equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    // Filter 스킵
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/oauth2/")
                || uri.startsWith("/login/oauth2/")
                || uri.startsWith("/login")
                || uri.startsWith("/ws/")
                || uri.startsWith("/ws");
    }
}
