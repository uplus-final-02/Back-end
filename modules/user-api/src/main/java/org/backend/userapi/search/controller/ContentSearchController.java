package org.backend.userapi.search.controller;

import lombok.RequiredArgsConstructor;
import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.dto.ContentSearchResponse;
import org.backend.userapi.search.service.ContentIndexingService;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ContentSearchController {

    private final ContentIndexingService contentIndexingService;

    @PostMapping("/index/rebuild")
    public ResponseEntity<ApiResponse<Void>> rebuildIndex() {
        contentIndexingService.indexAllContents();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ContentSearchResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "RELATED") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        Sort sortObj = switch (sort.toUpperCase()) {
            case "LATEST" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "POPULAR" -> Sort.by(Sort.Direction.DESC, "totalViewCount");
            default -> Sort.unsorted();
        };

        Pageable pageable = PageRequest.of(page, size, sortObj);
        
        Page<ContentDocument> result = contentIndexingService.search(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(ContentSearchResponse.from(result, keyword)));
    }

    @GetMapping("/search/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> getSuggestions(@RequestParam String keyword) {
        List<String> suggestions = contentIndexingService.getSuggestions(keyword);
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }
}