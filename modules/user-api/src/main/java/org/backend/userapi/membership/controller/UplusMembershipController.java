package org.backend.userapi.membership.controller;

import core.security.principal.JwtPrincipal;
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

@RestController
@RequestMapping("/api/membership")
@RequiredArgsConstructor
public class UplusMembershipController {

	private final UplusMembershipService uplusMembershipService;

	@PostMapping("/uplus/verify")
	public ResponseEntity<ApiResponse<UplusVerificationResponse>> verify(
	        @Valid @RequestBody UplusVerificationRequest request,
	        @AuthenticationPrincipal JwtPrincipal principal) {
		
		Long userId = principal.getUserId();
		
		UplusVerificationResponse response = uplusMembershipService.verify(userId, request);

		return ResponseEntity.ok(ApiResponse.success(response));
	}
	
	@PostMapping("/cancel")
	public ResponseEntity<ApiResponse<CancelSubscriptionResponse>> cancel(
	        @AuthenticationPrincipal JwtPrincipal principal) {

	    Long userId = principal.getUserId();

	    CancelSubscriptionResponse response =
	            uplusMembershipService.cancelSubscription(userId);

	    return ResponseEntity.ok(ApiResponse.success(response));
	}
}
