package org.backend.userapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lombok.RequiredArgsConstructor;

import core.security.handler.JwtAccessDeniedHandler;
import core.security.handler.JwtAuthenticationEntryPoint;
import core.security.jwt.JwtAuthenticationFilter;
import core.security.jwt.JwtTokenProvider;


@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    // 이메일 가입 - 다단계
                    "/api/auth/signup/email/send-code",
                    "/api/auth/signup/email/verify-code",
                    // 프로필 설정 (이메일/소셜 공통) - Setup Token은 서비스 레이어에서 검증
                    "/api/auth/signup/profile/nickname",
                    "/api/auth/signup/profile/tags",
                    // 소셜 로그인
                    "/api/auth/login/google",
                    "/api/auth/login/kakao",
                    "/api/auth/login/naver",
                    // 이메일 로그인 / 토큰 재발급
                    "/api/auth/login/email",
                    "/api/auth/reissue"
                ).permitAll()
                .requestMatchers("/api/auth/logout").authenticated()
                .requestMatchers("/api/membership/**").authenticated()
                .requestMatchers(
                    "/api/histories/bookmarks/**",
                    "/api/users/me/bookmarks/**",
                    "/api/users/me/preferred-tags"
                ).authenticated()
                .anyRequest().permitAll()
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint
    ) {
        return new JwtAuthenticationFilter(jwtTokenProvider, jwtAuthenticationEntryPoint);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}