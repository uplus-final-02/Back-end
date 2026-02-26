package org.backend.userapi.membership.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UplusVerificationResponse {
	
	private boolean isVerified;
    private String phoneNumber;
    private LocalDateTime verifiedAt;
}
