package org.backend.userapi.content.publish.service;

import content.entity.UserContent;
import content.repository.UserContentRepository;
import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
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
                .orElseThrow(() -> new IllegalArgumentException("USER_CONTENT_NOT_FOUND"));

        if (principal == null || principal.getUserId() == null || !uc.getUploaderId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN");
        }

        uc.requestPublish();
        policyService.applyPolicy(uc);
    }

    @Transactional
    public void cancelPublish(Long userContentId, JwtPrincipal principal) {
        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException("USER_CONTENT_NOT_FOUND"));

        if (principal == null || principal.getUserId() == null || !uc.getUploaderId().equals(principal.getUserId())) {
            throw new IllegalArgumentException("FORBIDDEN");
        }

        uc.cancelPublishRequest();
    }
}