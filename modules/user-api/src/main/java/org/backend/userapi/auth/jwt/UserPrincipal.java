package org.backend.userapi.auth.jwt;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private final Long userId; // 토큰의 subject에서 추출한 값
    private final String email; // 토큰의 email claim에서 추출한 값
}