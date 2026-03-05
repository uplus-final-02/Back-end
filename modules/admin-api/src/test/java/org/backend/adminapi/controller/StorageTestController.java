package org.backend.adminapi.controller;

import core.storage.ObjectStorageService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StorageTestController {

    private final ObjectStorageService objectStorageService;

    @GetMapping("/admin/storage/test")
    public String test() {
        var key = objectStorageService.buildObjectKey("videos/original", 999L, "test.mp4");

        var put = objectStorageService.generatePutPresignedUrl(key, "video/mp4", Duration.ofMinutes(10));
        var get = objectStorageService.generateGetPresignedUrl(key, Duration.ofMinutes(10));

        return """
                OK
                key=%s

                putUrl=%s

                getUrl=%s
                """.formatted(put.objectKey(), put.url(), get.url());
    }
}