package org.backend.userapi.upload.controller;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.upload.dto.*;
import org.backend.userapi.upload.service.UserUploadConfirmService;
import org.backend.userapi.upload.service.UserUploadDraftService;
import org.backend.userapi.upload.service.UserUploadPresignService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user/uploads")
public class UserUploadController {

    private final UserUploadDraftService draftService;
    private final UserUploadPresignService presignService;
    private final UserUploadConfirmService confirmService;

    @PostMapping("/draft")
    public UserUploadDraftResponse draft(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody UserUploadDraftRequest req
    ) {
        return draftService.createDraft(principal, req.parentContentId());
    }

    @PostMapping("/presign")
    public UserUploadPresignResponse presign(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody UserUploadPresignRequest request
    ) {
        return presignService.presign(principal, request);
    }

    @PostMapping("/confirm")
    public UserUploadConfirmResponse confirm(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody UserUploadConfirmRequest request
    ) {
        return confirmService.confirm(principal, request);
    }
}