package org.backend.userapi.search.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.dto.ContentSearchResponse;
import org.backend.userapi.search.service.ContentIndexingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
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

    // TODO: 운영 환경에서는 관리자 권한 체크 또는 내부망 접근 제한 필요
    @PostMapping("/index/rebuild")
    public ResponseEntity<ApiResponse<Void>> rebuildIndex() {
        contentIndexingService.indexAllContents();
        return ResponseEntity.ok(new ApiResponse<>(200, "전체 콘텐츠 엘라스틱서치 재색인(Rebuild) 작업이 백그라운드에서 시작되었습니다.", null));
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
        
        // 1. 일단 원래대로 검색을 합니다.
        Page<ContentDocument> result = contentIndexingService.search(keyword, category, genre, tag, userId, pageable);
        
        // 💡 [여기가 넷플릭스 4단계 추가 부분!] 검색 결과가 비어있다면?
        boolean isAlternative = false;
        if (result.isEmpty() && StringUtils.hasText(keyword)) {
            // 인기작(대체 콘텐츠)으로 결과물을 싹 덮어치기 합니다.
            result = contentIndexingService.getAlternativeContents(pageable);
            isAlternative = true;
        }

        // 2. 최종 결정된 result(원래 결과 or 대체 결과)로 DTO를 만듭니다.
        ContentSearchResponse responseData = ContentSearchResponse.from(result, keyword);

        // 💡 3. 상황에 맞게 프론트엔드에 띄워줄 센스 있는 메시지를 세팅합니다.
        String message;
        if (isAlternative) {
            message = "'" + keyword + "'에 대한 결과가 없어, 인기 추천 콘텐츠를 제공합니다.";
        } else if (result.isEmpty()) {
            message = "조건에 맞는 검색 결과가 없습니다.";
        } else {
            message = "검색 결과를 성공적으로 조회했습니다.";
        }

        return ResponseEntity.ok(new ApiResponse<>(200, message, responseData));
    }

    @GetMapping("/search/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> getSuggestions(@RequestParam String keyword) {
        if (!StringUtils.hasText(keyword)) {
             return ResponseEntity.ok(new ApiResponse<>(200, "검색어를 입력해주세요.", List.of())); 
        }
        
        List<String> suggestions = contentIndexingService.getSuggestions(keyword);
        
        String message = suggestions.isEmpty()
                ? "추천 자동완성 검색어가 없습니다."
                : "자동완성 검색어를 성공적으로 조회했습니다.";

        return ResponseEntity.ok(new ApiResponse<>(200, message, suggestions));
    }
    
    @GetMapping("/index/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getIndexingStatus() {
        Map<String, Object> status = contentIndexingService.getIndexingStatus();
        return ResponseEntity.ok(new ApiResponse<>(200, "엘라스틱서치 인덱싱 현재 상태를 조회했습니다.", status));
    }
}