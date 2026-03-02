package org.backend.userapi.membership.controller;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.membership.dto.SubscriptionMeResponse;
import org.backend.userapi.membership.service.UplusMembershipService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import core.security.principal.JwtPrincipal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final UplusMembershipService uplusMembershipService;

    @GetMapping("/me")
    public ApiResponse<SubscriptionMeResponse> getMySubscription(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        Long userId = principal.getUserId();
        SubscriptionMeResponse response = uplusMembershipService.getMySubscription(userId);
        return ApiResponse.success(response);
    }
}