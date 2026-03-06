package org.backend.userapi.payment.controller;

import lombok.RequiredArgsConstructor;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.payment.dto.SubscribeRequest;
import org.backend.userapi.payment.dto.SubscribeResponse;
import org.backend.userapi.payment.exception.PaymentIdempotencyException;
import org.backend.userapi.payment.service.PaymentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
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
        @Valid @RequestBody SubscribeRequest request,
        // required=false로 받아서 직접 검증 → 더 명확한 400 메시지 반환
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new PaymentIdempotencyException("Idempotency-Key 헤더가 필요합니다. 클라이언트에서 UUID를 생성하여 전달해주세요.");
        }

        SubscribeResponse response = paymentService.subscribe(principal.getUserId(), request, idempotencyKey);
        return ApiResponse.success(response);
    }
}
