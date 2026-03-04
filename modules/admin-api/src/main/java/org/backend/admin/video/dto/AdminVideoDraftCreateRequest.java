package org.backend.admin.video.dto;

public record AdminVideoDraftCreateRequest(
        Long uploaderId // 지금은 테스트를 위해 받음(추후 토큰에서 추출 권장)
) {}