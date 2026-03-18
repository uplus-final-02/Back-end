package org.backend.userapi.payment.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "결제 API", description = "구독 결제. Idempotency-Key 헤더(UUID) 필수 — 중복 결제 방지용")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "구독 결제", description = "요금제를 선택하여 구독 결제를 진행합니다. 'Idempotency-Key' 헤더에 클라이언트 생성 UUID를 전달해야 합니다.")
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
