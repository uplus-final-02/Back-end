package org.backend.userapi.auth.dto;

public record SetupTokenResponse(
        String setupToken,
        long setupTokenTtlSeconds
) {}
