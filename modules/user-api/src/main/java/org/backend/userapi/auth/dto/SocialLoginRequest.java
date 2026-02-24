package org.backend.userapi.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
        @NotBlank(message = "인가 코드를 입력해주세요.")
        String code,

        @NotBlank(message = "리다이렉트 URI를 입력해주세요.")
        String redirectUri,

        /** 네이버 로그인 시 필수. 구글/카카오는 null 가능. */
        String state
) {}
