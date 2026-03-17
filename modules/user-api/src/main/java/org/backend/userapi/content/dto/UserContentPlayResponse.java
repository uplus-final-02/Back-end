package org.backend.userapi.content.dto;

/**
 * 유저 업로드 콘텐츠 재생 정보 응답.
 *
 * <p>프론트엔드는 {@code hlsUrl}을 HLS 플레이어에 전달하여 재생한다.
 * 로컬(MinIO) 환경에서는 서명된 프록시 URL, 운영(CloudFront) 환경에서는
 * 서명된 CloudFront URL이 반환된다.
 */
public record UserContentPlayResponse(
        Long   userContentId,
        String title,
        String thumbnailUrl,
        String hlsUrl,
        int    durationSec
) {}
