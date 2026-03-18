package org.backend.userapi.upload.controller;

import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.upload.dto.*;
import org.backend.userapi.upload.service.UserUploadConfirmService;
import org.backend.userapi.upload.service.UserUploadDraftService;
import org.backend.userapi.upload.service.UserUploadPresignService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "유저 영상 업로드 API", description = "숏폼 영상 업로드 3단계: Draft 생성 → Presigned URL 발급 → 업로드 완료 확인")
@RestController
@RequiredArgsConstructor
@RequestMapping("api/user/uploads")
public class UserUploadController {

    private final UserUploadDraftService draftService;
    private final UserUploadPresignService presignService;
    private final UserUploadConfirmService confirmService;

    @Operation(summary = "[Step 1] Draft 생성", description = "업로드 전 Draft 레코드를 생성합니다. 연결할 정식 콘텐츠(parentContentId)를 지정하세요.")
    @PostMapping("/draft")
    public UserUploadDraftResponse draft(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody UserUploadDraftRequest req
    ) {
        return draftService.createDraft(principal, req.parentContentId());
    }

    @Operation(summary = "[Step 2] S3 Presigned URL 발급", description = "영상 파일을 S3에 직접 업로드하기 위한 Presigned PUT URL을 발급합니다.")
    @PostMapping("/presign")
    public UserUploadPresignResponse presign(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody UserUploadPresignRequest request
    ) {
        return presignService.presign(principal, request);
    }

    @Operation(summary = "[Step 3] 업로드 완료 확인", description = "S3 업로드 완료 후 호출합니다. Kafka로 트랜스코딩 이벤트를 발행하고 상태를 DRAFT → 처리 중으로 변경합니다.")
    @PostMapping("/confirm")
    public UserUploadConfirmResponse confirm(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestBody UserUploadConfirmRequest request
    ) {
        return confirmService.confirm(principal, request);
    }
}