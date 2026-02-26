package org.backend.userapi.membership.controller;

import core.security.principal.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.membership.dto.UplusVerificationRequest;
import org.backend.userapi.membership.dto.UplusVerificationResponse;
import org.backend.userapi.membership.service.UplusMembershipService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/membership/uplus")
@RequiredArgsConstructor
public class UplusMembershipController {

	private final UplusMembershipService uplusMembershipService;

	@PostMapping("/verify")
	public ResponseEntity<ApiResponse<UplusVerificationResponse>> verify(
	        @Valid @RequestBody UplusVerificationRequest request,
	        @AuthenticationPrincipal JwtPrincipal principal) {
		
		Long userId = principal.getUserId();
		
		UplusVerificationResponse response = uplusMembershipService.verify(userId, request);

		return ResponseEntity.ok(ApiResponse.success(response));
	}
    
}
