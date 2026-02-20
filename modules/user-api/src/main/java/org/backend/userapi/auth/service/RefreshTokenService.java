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

        RefreshToken rt = refreshTokenRepository.findByUserId(userId)
                .orElseGet(() -> RefreshToken.builder()
                        .userId(userId)
                        .build());

        rt.rotate(refreshToken, expiresAt);
        refreshTokenRepository.save(rt);
    }


    @Transactional(readOnly = true)
    public RefreshToken validateStoredTokenAndGet(Long userId, String presentedRefreshToken) {
        // 만료 검증 X (DB 저장값과 일치 검증을 위함)
    	RefreshToken saved = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다."));

        if (!saved.getToken().equals(presentedRefreshToken)) {
            throw new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다.");
        }

        return saved;
    }


    @Transactional
    public void rotate(RefreshToken saved, String newRefreshToken) {
        LocalDateTime newExpiresAt = jwtTokenProvider.getRefreshTokenExpiresAt();
        saved.rotate(newRefreshToken, newExpiresAt);
        refreshTokenRepository.save(saved);
    }

    @Transactional
    public void deleteByUserId(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
