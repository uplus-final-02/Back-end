package org.backend.userapi.membership.controller;

import core.security.principal.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.membership.dto.UplusVerificationRequest;
import org.backend.userapi.membership.dto.UplusVerificationResponse;
import org.backend.userapi.membership.service.UplusMembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/membership/uplus")
@RequiredArgsConstructor
public class UplusMembershipController {

	private final UplusMembershipService uplusMembershipService;

	@PostMapping("/verify")
	public ResponseEntity<ApiResponse<UplusVerificationResponse>> verify(
	        @Valid @RequestBody UplusVerificationRequest request,
	        Authentication authentication) {

		Long userId = extractUserId();

	    UplusVerificationResponse response = uplusMembershipService.verify(userId, request);

	    return ResponseEntity.ok(ApiResponse.success(response));
	}
    
	private Long extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("인증 정보가 없습니다.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.getUserId();
        }

        String name = authentication.getName();
        if (name != null) {
            try {
                return Long.parseLong(name);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }

        throw new IllegalStateException("유효하지 않은 인증 주체(principal)입니다.");
    }
}
