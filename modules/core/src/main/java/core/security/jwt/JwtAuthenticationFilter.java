package core.security.jwt;

import java.io.IOException;
import java.util.Collections; // 추가됨
import java.util.List;

import core.security.principal.JwtPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.authentication.BadCredentialsException;

import core.security.exception.JwtInvalidTokenException;
import core.security.exception.JwtTokenExpiredException;
import core.security.handler.JwtAuthenticationEntryPoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String token = resolveToken(request);

        if (token != null) {
        	try {
            // 토큰 파싱
//            Long userId = Long.valueOf(com.auth0.jwt.JWT.decode(token).getSubject());
//        	Long userId = jwtTokenProvider.extractUserId(token); // verify 포함 버전이면 OK
        	 DecodedJWT jwt = jwtTokenProvider.validateAndGet(token, null);

        	// refresh / setup 토큰은 필터에서 처리하지 않음
        	// - refresh: 재발급 전용, 일반 요청에 사용 불가
        	// - setup: 회원가입 단계 전용, 서비스 레이어에서 직접 검증
			String type = jwt.getClaim("type").asString();
			if (type != null) {
				filterChain.doFilter(request, response);
				return;
			}

		 	Long userId = Long.parseLong(jwt.getSubject());
            String role = jwt.getClaim("role").asString();
                	
            log.info("JWT validated subject={}, role={}", userId, role);
            
            JwtPrincipal principal = new JwtPrincipal(userId);
            
            List<GrantedAuthority> authorities =
                    (role == null || role.isBlank())
                            ? Collections.emptyList()
                            : List.of(new SimpleGrantedAuthority("ROLE_" + role));

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            
            SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
                log.error("JWT validation failed", e);

                jwtAuthenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid JWT", e)
                );
                return;
            }
        }   // ✅ 이거 추가 (if 닫기)

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { // admin 테스트용
        String path = request.getServletPath();

        return path.startsWith("/admin/users")
                || path.startsWith("/admin/storage")
                || path.startsWith("/admin/uploads/videos")
                || path.startsWith("/admin/videos")
                || path.startsWith("/admin/series");
    }
}