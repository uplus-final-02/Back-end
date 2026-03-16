package org.backend.userapi.hls;

import core.security.principal.JwtPrincipal;
import core.storage.service.LocalUrlSignatureService;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.hls.dto.HlsSignedUrlResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/hls")
public class UserHlsSignController {

    private final LocalUrlSignatureService urlSignatureService;

    @GetMapping("/sign")
    public HlsSignedUrlResponse sign(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam Long videoFileId,
            @RequestParam(defaultValue = "master") String type
    ) {
        if (principal == null || principal.getUserId() == null) {
            throw new IllegalArgumentException("LOGIN_REQUIRED");
        }

        long expires = Instant.now().plusSeconds(600).getEpochSecond();

        String path;
        if ("master".equalsIgnoreCase(type)) {
            path = "/api/hls/" + videoFileId + "/master.m3u8";
        } else {
            throw new IllegalArgumentException("UNSUPPORTED_TYPE: " + type);
        }

        String sig = urlSignatureService.sign(path, expires);
        String url = path + "?expires=" + expires + "&signature=" + sig;

        return new HlsSignedUrlResponse(url, expires);
    }
}