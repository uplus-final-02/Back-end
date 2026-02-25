package org.backend.userapi.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class UplusVerificationRequest {
	
    @NotBlank(message = "전화번호는 필수입니다.")
    @Pattern(regexp = "^[0-9]+$", message = "전화번호는 숫자만 입력해야 합니다.")
    private String phoneNumber;
}
