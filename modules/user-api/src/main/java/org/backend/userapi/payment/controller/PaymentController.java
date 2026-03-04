package org.backend.userapi.payment.controller;

import lombok.RequiredArgsConstructor;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.payment.dto.SubscribeRequest;
import org.backend.userapi.payment.dto.SubscribeResponse;
import org.backend.userapi.payment.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import core.security.principal.JwtPrincipal;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/subscribe")
    public ApiResponse<SubscribeResponse> subscribe(
        @AuthenticationPrincipal JwtPrincipal principal,
        @Valid @RequestBody SubscribeRequest request
    ) {
        SubscribeResponse response = paymentService.subscribe(principal.getUserId(), request);
        return ApiResponse.success(response);
    }
}