package org.backend.admin.auth.service;

import core.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import user.entity.AuthAccount;
import user.entity.User;
import user.repository.AuthAccountRepository;
import user.repository.UserRepository;

import org.backend.admin.auth.dto.AdminLoginRequest;
import org.backend.admin.auth.dto.AdminLoginResponse;
import org.backend.admin.controller.AdminAuthController;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import common.enums.AuthProvider;
import common.enums.UserRole;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {
	
	private final AuthAccountRepository authAccountRepository;
	private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public AdminLoginResponse login(AdminLoginRequest request) {
    	AuthAccount account = authAccountRepository
    	        .findByAuthProviderAndEmail(AuthProvider.EMAIL, request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("관리자 계정을 찾을 수 없습니다."));

    	log.info("matches={}", passwordEncoder.matches(request.getPassword(), account.getPasswordHash()));
    	
        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        User user = userRepository.findById(account.getId())
        		.orElseThrow(() -> new IllegalArgumentException("관리자 계정을 찾을 수 없습니다."));
        
        
        // ROLE_ADMIN 확인
        if (user.getUserRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("관리자 권한이 없습니다.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                account.getEmail(),
                user.getNickname(),
                String.valueOf(user.getUserRole()),
                false,
                false
        );

        return new AdminLoginResponse(accessToken);
    }
    
    private boolean isAdmin(User user) {
        return "ADMIN".equals(String.valueOf(user.getUserRole()));
    }
}
