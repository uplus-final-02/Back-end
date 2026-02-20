package org.backend.userapi.auth.service;

import lombok.RequiredArgsConstructor;

import org.backend.userapi.common.exception.InvalidCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import core.security.jwt.JwtTokenProvider;
import user.entity.RefreshToken;
import user.repository.RefreshTokenRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void upsert(Long userId, String refreshToken) {
        LocalDateTime expiresAt = jwtTokenProvider.getRefreshTokenExpiresAt();

        refreshTokenRepository.findByUserId(userId)
            .ifPresentOrElse(
                saved -> {
                    saved.rotate(refreshToken, expiresAt); // UPDATE
                },
                () -> {
                    RefreshToken created = RefreshToken.builder()
                        .userId(userId)
                        .token(refreshToken)      // INSERT 초기값은 생성에서
                        .expiresAt(expiresAt)
                        .build();
                    refreshTokenRepository.save(created);
                }
            );
    }


    @Transactional(readOnly = true)
    public RefreshToken validateStoredTokenAndGet(Long userId, String presentedRefreshToken) {
    	// JWT 서명/exp 만료 검증은 JwtTokenProvider.validateAndGet()에서 이미 수행됨.
    	// 여기서는 DB에 저장된 refreshToken과 요청 토큰의 "일치 여부"만 검증한다.
    	RefreshToken saved = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다."));

        if (!saved.getToken().equals(presentedRefreshToken)) {
            throw new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다.");
        }

        return saved;
    }


    @Transactional
    public void deleteByUserId(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
