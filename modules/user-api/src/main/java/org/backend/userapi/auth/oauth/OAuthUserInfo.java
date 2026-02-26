package org.backend.userapi.auth.oauth;

/**
 * 소셜 로그인 공통 유저 정보 모델.
 * 각 provider에서 받아온 정보를 이 형태로 통일합니다.
 */
public record OAuthUserInfo(
        String providerSubject,  // 제공자 고유 ID (Google: id, Kakao: id, Naver: id)
        String email,            // 이메일 (동의 안 한 경우 null 가능)
        String nickname          // 표시 이름 (프로필 자동완성용, optional)
) {}
