package org.backend.userapi.membership.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.membership.dto.SubscriptionMeResponse;
import org.backend.userapi.membership.service.UplusMembershipService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import core.security.principal.JwtPrincipal;

@Tag(name = "구독 API", description = "현재 구독 상태 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final UplusMembershipService uplusMembershipService;

    @Operation(summary = "내 구독 정보 조회", description = "현재 구독 상태(ACTIVE/EXPIRED/CANCELED), 만료일, 요금제 정보를 반환합니다.")
    @GetMapping("/me")
    public ApiResponse<SubscriptionMeResponse> getMySubscription(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long userId = principal.getUserId();
        SubscriptionMeResponse response = uplusMembershipService.getMySubscription(userId);
        return ApiResponse.success(response);
    }
}