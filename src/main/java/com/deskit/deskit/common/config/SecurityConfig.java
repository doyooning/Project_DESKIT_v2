package com.deskit.deskit.common.config;

import com.deskit.deskit.account.jwt.CustomLogoutFilter;
import com.deskit.deskit.account.jwt.JWTFilter;
import com.deskit.deskit.account.jwt.JWTUtil;
import com.deskit.deskit.account.oauth.CustomOAuth2FailureHandler;
import com.deskit.deskit.account.oauth.CustomSuccessHandler;
import com.deskit.deskit.account.repository.RefreshRepository;
import com.deskit.deskit.account.service.CustomOAuth2UserService;
import com.deskit.deskit.admin.security.AdminSecondFactorFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomSuccessHandler customSuccessHandler;
    private final CustomOAuth2FailureHandler customOAuth2FailureHandler;
    private final JWTUtil jwtUtil;
    private final RefreshRepository refreshRepository;
    private final List<String> allowedOrigins;
    private final boolean cookieSecure;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          CustomSuccessHandler customSuccessHandler,
                          CustomOAuth2FailureHandler customOAuth2FailureHandler,
                          JWTUtil jwtUtil,
                          RefreshRepository refreshRepository,
                          @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOriginsRaw,
                          @Value("${app.cookie.secure:false}") boolean cookieSecure) {

        this.customOAuth2UserService = customOAuth2UserService;
        this.customSuccessHandler = customSuccessHandler;
        this.customOAuth2FailureHandler = customOAuth2FailureHandler;
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
        this.allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());
        this.cookieSecure = cookieSecure;
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        Map<RequestMatcher, AuthenticationEntryPoint> map = new LinkedHashMap<>();

        // API는 무조건 401(JSON)
        map.put(new AntPathRequestMatcher("/api/**"), (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"unauthorized\"}");
        });

        // 기본은 웹 앱 동작: /login으로 리다이렉트 (oauth2Login이 이 경로를 사용)
        DelegatingAuthenticationEntryPoint delegating = new DelegatingAuthenticationEntryPoint((LinkedHashMap<RequestMatcher, AuthenticationEntryPoint>) map);
        delegating.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return delegating;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(corsCustomizer -> corsCustomizer.configurationSource(new CorsConfigurationSource() {

                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {

                        CorsConfiguration configuration = new CorsConfiguration();

                        configuration.setAllowedOrigins(allowedOrigins);
                        configuration.setAllowedMethods(Collections.singletonList("*"));
                        configuration.setAllowCredentials(true);
                        configuration.setAllowedHeaders(Collections.singletonList("*"));
                        configuration.setMaxAge(3600L);

                        configuration.setExposedHeaders(
                                java.util.List.of("Set-Cookie", "Authorization", "access")
                        );

                        return configuration;
                    }
                }));

        //csrf disable
        http
                .csrf((auth) -> auth.disable());

        //From 로그인 방식 disable
        http
                .formLogin((auth) -> auth.disable());

        //HTTP Basic 인증 방식 disable
        http
                .httpBasic((auth) -> auth.disable());

        //JWTFilter 추가
        http
                .addFilterBefore(new JWTFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new AdminSecondFactorFilter(), JWTFilter.class);

        http
                .addFilterBefore(new CustomLogoutFilter(jwtUtil, refreshRepository, cookieSecure), LogoutFilter.class);

        //oauth2
        http
                .oauth2Login((oauth2) -> oauth2
                        .userInfoEndpoint((userInfoEndpointConfig) -> userInfoEndpointConfig
                                .userService(customOAuth2UserService))
                        .successHandler(customSuccessHandler)
                        .failureHandler(customOAuth2FailureHandler)
                );

        http.exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()));

        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"unauthorized\"}");
                })
        );

        //경로별 인가 작업
        http
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers("/ws/info/**", "/ws/**", "/ws", "/api/ws/**", "/api/ws").permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/assets/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/*.js",
                                "/*.css",
                                "/*.png",
                                "/*.jpg",
                                "/*.svg"
                        ).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/orders", "/api/orders/**").hasAuthority("ROLE_MEMBER")
                        .requestMatchers("/api/payments/toss/**").hasAuthority("ROLE_MEMBER")
                        .requestMatchers("/api/addresses/**").hasAuthority("ROLE_MEMBER")
                        .requestMatchers(HttpMethod.PATCH, "/api/seller/products/**")
                        .hasAnyAuthority("ROLE_SELLER", "ROLE_SELLER_OWNER", "ROLE_SELLER_MANAGER")
                        .requestMatchers("/api/chat/**").hasAuthority("ROLE_MEMBER")
                        .requestMatchers("/api/direct-chats/**").hasAnyAuthority("ROLE_MEMBER", "ROLE_ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/products/**",
                                "/api/setups/**",
                                "/api/setup/**",
                                "/api/products",
                                "/api/setups",
                                "/api/home/**",
                                "/livechats/**",
                                "/products/**",
                                "/setups/**"
                        ).permitAll()
                        .requestMatchers(
                                "/",
                                "/chat",
                                "/chat/**",
                                "/reissue",
                                "/api/home/**",
                                "/api/broadcasts/**",
                                "/api/categories",
                                "/api/vods/**",
                                "/api/webhook/**",
                                "/api/admin/auth/**",
                                "/api/invitations/validate",
                                "/oauth/**",
                                "/login",
                                "/login/**",
                                "/login/oauth2/**",
                                "/ws/**",
                                "/api/signup/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/quit").hasAnyAuthority(
                                "ROLE_MEMBER",
                                "ROLE_SELLER_OWNER",
                                "ROLE_SELLER_MANAGER"
                        )
                        .requestMatchers("/api/my/member-id").hasAuthority("ROLE_MEMBER")
                        .requestMatchers("/api/my/settings/**").hasAuthority("ROLE_MEMBER")
                        .requestMatchers("/api/recommendations/**").hasAuthority("ROLE_MEMBER")
                        .requestMatchers("/api/seller/**").hasAnyAuthority(
                                "ROLE_SELLER_OWNER",
                                "ROLE_SELLER_MANAGER")
                        .requestMatchers("/api/my").hasAnyAuthority(
                                "ROLE_MEMBER",
                                "ROLE_SELLER",
                                "ROLE_SELLER_OWNER",
                                "ROLE_SELLER_MANAGER",
                                "ROLE_ADMIN"
                        )
                );

        //세션 설정 : STATELESS -> IF_REQUIRED
        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        return http.build();
    }

}
