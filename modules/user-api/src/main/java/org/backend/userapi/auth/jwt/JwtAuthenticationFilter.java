package org.backend.userapi.auth.jwt;

import java.io.IOException;
import java.util.Collections; // 추가됨

import org.backend.userapi.auth.dto.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        String token = resolveToken(request);

        if (token != null) {
            // 토큰 파싱
            Long userId = Long.valueOf(com.auth0.jwt.JWT.decode(token).getSubject()); 
            UserPrincipal principal = new UserPrincipal(userId, null);
            
            // [핵심] null 대신 emptyList() 사용
            UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
            
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}