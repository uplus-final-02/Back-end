package org.backend.userapi.content.publish.service;

import content.entity.UserContent;
import content.repository.UserContentRepository;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.exception.ContentNotFoundException;
import org.backend.userapi.common.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserContentPublishService {

    private final UserContentRepository userContentRepository;
    private final UserContentPublishPolicyService policyService;

    @Transactional
    public void requestPublish(Long userContentId, JwtPrincipal principal) {
        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new ContentNotFoundException("유저 콘텐츠를 찾을 수 없습니다."));

        if (principal == null || principal.getUserId() == null || !uc.getUploaderId().equals(principal.getUserId())) {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }

        uc.requestPublish();
        policyService.applyPolicy(uc);
    }

    @Transactional
    public void cancelPublish(Long userContentId, JwtPrincipal principal) {
        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new ContentNotFoundException("유저 콘텐츠를 찾을 수 없습니다."));

        if (principal == null || principal.getUserId() == null || !uc.getUploaderId().equals(principal.getUserId())) {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }

        uc.cancelPublishRequest();
    }
}