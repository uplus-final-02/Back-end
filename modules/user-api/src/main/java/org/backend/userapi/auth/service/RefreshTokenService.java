package org.backend.userapi.auth.service;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.auth.jwt.JwtTokenProvider;
import org.backend.userapi.common.exception.InvalidCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
                        .token(refreshToken)
                        .expiresAt(expiresAt)
                        .build());

        rt.rotate(refreshToken, expiresAt);
        refreshTokenRepository.save(rt);
    }


    @Transactional(readOnly = true)
    public RefreshToken validateAndGet(Long userId, String refreshToken) {
        RefreshToken saved = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다."));

        if (!saved.getToken().equals(refreshToken)) {
            throw new InvalidCredentialsException("리프레시 토큰이 유효하지 않습니다.");
        }

        if (saved.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidCredentialsException("리프레시 토큰이 만료되었습니다.");
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
