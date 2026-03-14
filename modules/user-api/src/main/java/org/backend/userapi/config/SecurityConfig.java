package org.backend.userapi.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import core.security.handler.JwtAccessDeniedHandler;
import core.security.handler.JwtAuthenticationEntryPoint;
import core.security.jwt.JwtAuthenticationFilter;
import core.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Value("${cors.allowed-origins}")
	private List<String> allowedOrigins;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
			throws Exception {
		http.cors(cors -> cors.configurationSource(corsConfigurationSource())).csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
						.accessDeniedHandler(jwtAccessDeniedHandler))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/signup/email/send-code", "/api/auth/signup/email/verify-code",
								"/api/auth/signup/profile/nickname", "/api/auth/signup/profile/tags",
								"/api/auth/login/google", "/api/auth/login/kakao", "/api/auth/login/naver",
								"/api/auth/login/email", "/api/auth/reissue")
						.permitAll().requestMatchers("/api/auth/logout").authenticated()
						.requestMatchers("/api/membership/**").authenticated().requestMatchers("/api/payments/**")
						.authenticated()
						.requestMatchers("/api/histories/bookmarks/**", "/api/users/me/bookmarks/**",
								"/api/users/me/preferred-tags", "/api/contents/recommended")
						.authenticated()

						// 💡 [최종 결정] 와일드카드 없이, 정확히 어드민 전용 4개 API만 콕 집어서 방어!
						// 이렇게 하면 /api/index/status 같은 조회용 API는 안전하게 뚫려있게 됩니다.
						.requestMatchers("/api/index/rebuild", "/api/index/status", "/api/search/log/zero-results",
								"/api/search/log/top-keywords", "/api/search/dlq/retry")
						.hasRole("ADMIN")

						.anyRequest().permitAll())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
			JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
		return new JwtAuthenticationFilter(jwtTokenProvider, jwtAuthenticationEntryPoint);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setAllowCredentials(true);
		configuration.setExposedHeaders(List.of("Authorization"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);

		return source;
	}
}