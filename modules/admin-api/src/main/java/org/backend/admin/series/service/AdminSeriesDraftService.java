package org.backend.admin.series.service;

import common.enums.ContentAccessLevel;
import common.enums.ContentStatus;
import common.enums.ContentType;
import content.entity.Content;
import content.repository.ContentRepository;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.admin.series.dto.AdminSeriesDraftCreateResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminSeriesDraftService {

    private final ContentRepository contentRepository;

    @Transactional
    public AdminSeriesDraftCreateResponse createDraft(JwtPrincipal principal, Authentication authentication) {
        if (principal == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다.");
        }

        Long uploaderId = resolveUploaderId(principal, authentication);

        Content content = Content.builder()
                .type(ContentType.SERIES)
                .title("TEMP_SERIES_TITLE")
                .description(null)
                .thumbnailUrl("TEMP_THUMBNAIL_URL")
                .status(ContentStatus.HIDDEN)
                .uploaderId(uploaderId)
                .accessLevel(ContentAccessLevel.FREE)
                .build();

        Content saved = contentRepository.save(content);
        return new AdminSeriesDraftCreateResponse(saved.getId());
    }

    private Long resolveUploaderId(JwtPrincipal principal, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null) {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equals("ROLE_ADMIN"));
            if (isAdmin) return null;
        }
        return principal.getUserId();
    }
}