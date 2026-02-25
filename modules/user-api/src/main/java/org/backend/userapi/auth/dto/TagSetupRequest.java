package org.backend.userapi.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TagSetupRequest(
        @NotNull(message = "선호 태그를 선택해주세요.")
        @Size(min = 3, max = 5, message = "선호 태그는 3개 이상 5개 이하로 선택해주세요.")
        List<Long> tagIds
) {}
