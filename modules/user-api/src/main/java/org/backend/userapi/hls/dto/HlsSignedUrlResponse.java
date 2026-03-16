package org.backend.userapi.hls.dto;

public record HlsSignedUrlResponse(
        String url,
        long expires
) {}