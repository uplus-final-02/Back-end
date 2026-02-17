package org.backend.userapi.search.controller;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.dto.ContentSearchResponse;
import org.backend.userapi.search.service.ContentIndexingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentSearchController {

    private final ContentIndexingService contentIndexingService;

    @PostMapping("/index/rebuild")
    public ResponseEntity<ApiResponse<Void>> rebuildIndex() {
        contentIndexingService.indexAllContents();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/index/{contentId}")
    public ResponseEntity<ApiResponse<Void>> indexOne(@PathVariable Long contentId) {
        contentIndexingService.indexContent(contentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/index/{contentId}")
    public ResponseEntity<ApiResponse<Void>> deleteOne(@PathVariable Long contentId) {
        contentIndexingService.deleteContent(contentId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ContentSearchResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ContentDocument> result = contentIndexingService.search(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(ContentSearchResponse.from(result)));
    }
}
