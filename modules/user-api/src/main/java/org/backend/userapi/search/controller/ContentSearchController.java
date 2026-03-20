package org.backend.userapi.search.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.backend.userapi.common.dto.ApiResponse;
import org.backend.userapi.recommendation.dto.UserRecommendedContentResponse;
import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.dto.ContentSearchResponse;
import org.backend.userapi.search.service.ContentIndexingService;
import org.backend.userapi.search.service.EsSyncFailureService;
import org.backend.userapi.search.service.SearchCacheService;
import org.backend.userapi.search.service.SearchLogService;
import org.backend.userapi.search.service.SuggestionService;
import org.backend.userapi.search.service.UserContentSearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "검색 API", description = "콘텐츠 검색, 실시간 추천 및 엘라스틱서치 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ContentSearchController {

    private final ContentIndexingService contentIndexingService;
    private final SearchCacheService searchCacheService;
    private final SuggestionService suggestionService;
    private final SearchLogService searchLogService;
    private final EsSyncFailureService esSyncFailureService;
    private final UserContentSearchService userContentSearchService;

    @Operation(
        summary = "전체 콘텐츠 재색인 (Admin)", 
        description = "DB의 전체 콘텐츠 데이터를 엘라스틱서치에 백그라운드로 다시 색인합니다. (시간 소요됨)"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/index/rebuild")
    public ResponseEntity<ApiResponse<Void>> rebuildIndex() {
        contentIndexingService.indexAllContents();
        return ResponseEntity.ok(new ApiResponse<>(200, "전체 콘텐츠 엘라스틱서치 재색인(Rebuild) 작업이 백그라운드에서 시작되었습니다.", null));
    }

    @Operation(
        summary = "콘텐츠 메인 검색", 
        description = "키워드, 카테고리, 장르, 태그를 조합하여 콘텐츠를 검색합니다. 초성 검색 및 N-gram 부분 일치를 지원합니다."
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ContentSearchResponse>> search(
            @Parameter(description = "검색 키워드 (초성 가능)", example = "무빙") @RequestParam(required = false) String keyword,
            @Parameter(description = "카테고리 필터", example = "SERIES") @RequestParam(required = false) String category, 
            @Parameter(description = "장르 필터", example = "액션") @RequestParam(required = false) String genre,   
            @Parameter(description = "태그 필터", example = "히어로") @RequestParam(required = false) String tag,
            @Parameter(description = "정렬 방식 (LATEST, POPULAR, RELATED)", example = "RELATED") @RequestParam(defaultValue = "RELATED") String sort,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "15") @RequestParam(defaultValue = "15") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal JwtPrincipal jwtPrincipal
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

        ContentSearchResponse response = searchCacheService.searchWithCache(
                keyword, category, genre, tag, userId, sort, pageable);

        boolean isAlternative = false;
        int actualResultCount = response.contents().size();
        
        String didYouMean = null;
        if (actualResultCount == 0 && StringUtils.hasText(keyword)) {
            didYouMean = suggestionService.getDidYouMean(keyword);
        }
        
        if (response.contents().isEmpty()) {
            Page<ContentDocument> altPage = contentIndexingService.getAlternativeContents(pageable);
            response = ContentSearchResponse.from(altPage, keyword);
            isAlternative = true;
        }
        
        if (didYouMean != null) {
            response = response.withDidYouMean(didYouMean);
        }

        String message;
        if (actualResultCount == 0 && !isAlternative) {
            message = "조건에 맞는 검색 결과가 없습니다.";
        } else if (isAlternative) {
            message = StringUtils.hasText(keyword)
                ? "'" + keyword + "'에 대한 결과가 없어, 인기 추천 콘텐츠를 제공합니다."
                : "선택하신 조건에 맞는 결과가 없어, 인기 추천 콘텐츠를 제공합니다.";
        } else {
            message = "검색 결과를 성공적으로 조회했습니다.";
        }
        
        if (StringUtils.hasText(keyword)) {
            searchLogService.log(keyword, actualResultCount, userId);
        }

        return ResponseEntity.ok(new ApiResponse<>(200, message, response));

    }

    @Operation(
        summary = "검색어 자동완성 추천", 
        description = "사용자가 타이핑 중인 키워드를 기반으로 연관된 콘텐츠 제목을 추천합니다."
    )
    @GetMapping("/search/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> getSuggestions(
            @Parameter(description = "입력 중인 검색어 (초성 가능)", example = "ㅁㅂ") @RequestParam String keyword
    ) {
        if (!StringUtils.hasText(keyword)) {
             return ResponseEntity.ok(new ApiResponse<>(200, "검색어를 입력해주세요.", List.of())); 
        }
        
        List<String> suggestions = suggestionService.getSuggestions(keyword);
        
        String message = suggestions.isEmpty()
                ? "추천 자동완성 검색어가 없습니다."
                : "자동완성 검색어를 성공적으로 조회했습니다.";

        return ResponseEntity.ok(new ApiResponse<>(200, message, suggestions));
    }
       
    @Operation(
            summary = "인덱싱 상태 조회", 
            description = "현재 백그라운드에서 진행 중인 엘라스틱서치 색인 작업의 상태를 조회합니다. (Admin)"
        )
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/index/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getIndexingStatus() {
        Map<String, Object> status = contentIndexingService.getIndexingStatus();
        return ResponseEntity.ok(new ApiResponse<>(200, "엘라스틱서치 인덱싱 현재 상태를 조회했습니다.", status));
    }
    
    @Operation(
        summary = "빈 화면용 인기 콘텐츠 추천", 
        description = "검색 결과가 없거나 부족할 때, 화면 하단에 띄워줄 전체 인기작 목록을 가져옵니다."
    )
    @GetMapping("/search/related")
    public ResponseEntity<ApiResponse<ContentSearchResponse>> getRelatedContents(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "10") @RequestParam(defaultValue = "10") int size
    ) {
        int safeSize = (size <= 0) ? 10 : Math.min(size, 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        
        Page<ContentDocument> relatedPage = contentIndexingService.getAlternativeContents(pageable);
        
        ContentSearchResponse response = ContentSearchResponse.from(relatedPage, null);
        
        return ResponseEntity.ok(new ApiResponse<>(200, "연관/인기 콘텐츠를 성공적으로 조회했습니다.", response));
    }
    
    // 어드민용 조회 엔드포인트
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/search/log/zero-results")
    public ResponseEntity<ApiResponse<List<String>>> getZeroResultKeywords(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(new ApiResponse<>(200, "결과 없는 검색어 목록",
                searchLogService.getZeroResultKeywords(days)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/search/log/top-keywords")
    public ResponseEntity<ApiResponse<List<String>>> getTopKeywords(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(new ApiResponse<>(200, "인기 검색어 목록",
                searchLogService.getTopKeywords(days)));
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/search/dlq/retry")
    public ResponseEntity<ApiResponse<Void>> retryDlq() {
        esSyncFailureService.retryAll();
        return ResponseEntity.ok(new ApiResponse<>(200, "DLQ 수동 재시도 완료", null));
    }
    
    @Operation(
        summary = "크리에이터 전체 피드 / 콘텐츠별 영상 목록",
        description = "parentContentId가 없으면 전체 유저 영상을, 있으면 해당 OTT 콘텐츠에 매핑된 영상만 반환합니다."
    )
    @GetMapping("/search/creator")
    public ResponseEntity<ApiResponse<List<UserRecommendedContentResponse>>> searchCreatorContents(
            @Parameter(description = "관리자 콘텐츠 ID (선택: 전체 조회 시 생략)", example = "16264")
            @RequestParam(required = false) Long parentContentId, // 🌟 필수 아님!
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "15")
            @RequestParam(defaultValue = "15") int size
    ) {
        int safeSize = (size <= 0) ? 15 : Math.min(size, 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // 이름 변경: searchByParentContentId -> searchCreatorContents
        List<UserRecommendedContentResponse> results =
                userContentSearchService.searchCreatorContents(parentContentId, pageable);

        String message = results.isEmpty()
                ? "조회할 크리에이터 영상이 없습니다."
                : "크리에이터 영상을 성공적으로 조회했습니다.";

        return ResponseEntity.ok(new ApiResponse<>(200, message, results));
    }

    @Operation(
        summary = "크리에이터 페이지 — 특정 유저가 올린 영상 목록",
        description = "uploaderId로 해당 유저의 공개 영상 목록을 반환합니다."
    )
    @GetMapping("/search/creator/user")
    public ResponseEntity<ApiResponse<List<UserRecommendedContentResponse>>> searchByUploader(
            @Parameter(description = "업로더 유저 ID", example = "1")
            @RequestParam Long uploaderId,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "15")
            @RequestParam(defaultValue = "15") int size
    ) {
        int safeSize = (size <= 0) ? 15 : Math.min(size, 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        List<UserRecommendedContentResponse> results =
                userContentSearchService.searchByUploaderId(uploaderId, pageable);

        String message = results.isEmpty()
                ? "해당 크리에이터의 영상이 없습니다."
                : "크리에이터 영상을 성공적으로 조회했습니다.";

        return ResponseEntity.ok(new ApiResponse<>(200, message, results));
    }
    
    @Operation(
        summary = "크리에이터 영상 키워드 검색",
        description = "유저 업로드 영상을 제목으로 검색합니다. 태그 검색은 지원하지 않습니다."
    )
    @GetMapping("/search/creator/search")
    public ResponseEntity<ApiResponse<List<UserRecommendedContentResponse>>> searchCreatorByKeyword(
            @Parameter(description = "검색 키워드", example = "무빙")
            @RequestParam String keyword,
            @Parameter(description = "페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "15")
            @RequestParam(defaultValue = "15") int size
    ) {
        if (!StringUtils.hasText(keyword)) {
            throw new IllegalArgumentException("검색어를 입력해주세요.");
        }

        int safeSize = (size <= 0) ? 15 : Math.min(size, 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        List<UserRecommendedContentResponse> results =
                userContentSearchService.searchByKeyword(keyword, pageable);

        String message = results.isEmpty()
                ? "검색 결과가 없습니다."
                : "크리에이터 영상 검색 결과를 조회했습니다.";

        return ResponseEntity.ok(new ApiResponse<>(200, message, results));
    }

}