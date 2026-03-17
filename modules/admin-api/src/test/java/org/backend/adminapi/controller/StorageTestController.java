package org.backend.adminapi.controller;

import core.storage.ObjectStorageService;
import java.time.Duration;
// import lombok.RequiredArgsConstructor; 👈 지웠습니다!
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
// @RequiredArgsConstructor 👈 지웠습니다!
public class StorageTestController {

    private final ObjectStorageService objectStorageService;

    // 🌟 롬복 대신 직접 생성자를 만들어 주입합니다. (이게 스프링 공식 권장 방식이기도 합니다)
    public StorageTestController(ObjectStorageService objectStorageService) {
        this.objectStorageService = objectStorageService;
    }

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