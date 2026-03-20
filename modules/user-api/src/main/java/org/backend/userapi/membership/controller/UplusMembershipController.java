package org.backend.userapi.membership.controller;

import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.membership.dto.CancelSubscriptionResponse;
import org.backend.userapi.membership.dto.UplusVerificationRequest;
import org.backend.userapi.membership.dto.UplusVerificationResponse;
import org.backend.userapi.membership.service.UplusMembershipService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "U+ 멤버십 API", description = "LG U+ 통신사 멤버십 인증, 구독 해지")
@RestController
@RequestMapping("/api/membership")
@RequiredArgsConstructor
public class UplusMembershipController {

	private final UplusMembershipService uplusMembershipService;

	@Operation(summary = "U+ 멤버십 인증", description = "전화번호로 LG U+ 가입 여부를 인증합니다. 인증 성공 시 JWT의 uplus 플래그가 갱신됩니다.")
	@PostMapping("/uplus/verify")
	public ResponseEntity<ApiResponse<UplusVerificationResponse>> verify(
	        @Valid @RequestBody UplusVerificationRequest request,
	        @AuthenticationPrincipal JwtPrincipal principal) {
		
		Long userId = principal.getUserId();
		
		UplusVerificationResponse response = uplusMembershipService.verify(userId, request);

		return ResponseEntity.ok(ApiResponse.success(response));
	}
	
	@Operation(summary = "구독 해지", description = "현재 구독을 해지합니다. 상태가 CANCELED로 변경되며 만료일까지는 서비스를 이용할 수 있습니다.")
	@PostMapping("/cancel")
	public ResponseEntity<ApiResponse<CancelSubscriptionResponse>> cancel(
	        @AuthenticationPrincipal JwtPrincipal principal) {

	    Long userId = principal.getUserId();

	    CancelSubscriptionResponse response =
	            uplusMembershipService.cancelSubscription(userId);

	    return ResponseEntity.ok(ApiResponse.success(response));
	}
}
