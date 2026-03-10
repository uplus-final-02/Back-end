package org.backend.userapi.search.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.search.dto.ContentSearchResponse;
import org.backend.userapi.search.service.ContentIndexingService;
import org.backend.userapi.search.service.SearchCacheService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils; // 💡 null safe 유틸 사용
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import core.security.principal.JwtPrincipal;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ContentSearchController {

    private final ContentIndexingService contentIndexingService;
    private final SearchCacheService searchCacheService;

    // TODO: 운영 환경에서는 관리자 권한 체크 또는 내부망 접근 제한 필요
    @PostMapping("/index/rebuild")
    public ResponseEntity<ApiResponse<Void>> rebuildIndex() {
        contentIndexingService.indexAllContents();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ContentSearchResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category, 
            @RequestParam(required = false) String genre,   
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "RELATED") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @AuthenticationPrincipal JwtPrincipal jwtPrincipal
    ) {
    	if (!StringUtils.hasText(keyword) && !StringUtils.hasText(tag) && !StringUtils.hasText(category) && !StringUtils.hasText(genre)) {
    	    throw new IllegalArgumentException("검색어나 필터를 하나 이상 입력해주세요..");
    	}

    	int safeSize = (size <= 0) ? 15 : Math.min(size, 50);
        Sort sortObj = switch (sort.toUpperCase(Locale.ROOT)) {
            case "LATEST" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("contentId"));
            case "POPULAR" -> Sort.by(Sort.Order.desc("totalViewCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("contentId"));
            case "RELATED" -> Sort.by(Sort.Order.desc("_score"), Sort.Order.desc("contentId"));
            default -> throw new IllegalArgumentException("지원하지 않는 정렬 방식입니다: " + sort);
        };

        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, sortObj);

        Long userId = (jwtPrincipal != null) ? jwtPrincipal.getUserId() : null;

        // Cache-Aside: Redis 캐시 조회 → 미스 시 ES 검색 → 결과 캐시 저장
        ContentSearchResponse response = searchCacheService.searchWithCache(
                keyword, category, genre, tag, userId, sort, pageable);

    	return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/search/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> getSuggestions(@RequestParam String keyword) {
        // 자동완성에서도 공백 입력 방어 필요
        if (!StringUtils.hasText(keyword)) {
             return ResponseEntity.ok(ApiResponse.success(List.of())); // 빈 리스트 반환이 UX상 자연스러움
        }
        List<String> suggestions = contentIndexingService.getSuggestions(keyword);
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }
    
    @GetMapping("/index/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getIndexingStatus() {
        Map<String, Object> status = contentIndexingService.getIndexingStatus();
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}