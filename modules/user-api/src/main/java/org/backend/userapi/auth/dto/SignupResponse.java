package org.backend.userapi.auth.dto;

import java.util.List;

public record SignupResponse(
        Long userId,
        String nickname,
        List<String> preferredTags
) {
}
