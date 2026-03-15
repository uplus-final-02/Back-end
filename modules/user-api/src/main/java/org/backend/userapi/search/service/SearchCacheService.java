package org.backend.userapi.search.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.backend.userapi.search.document.ContentDocument;
import org.backend.userapi.search.dto.ContentSearchItem;
import org.backend.userapi.search.dto.ContentSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import user.repository.UserPreferredTagRepository;

/**
 * 검색 결과 Redis 캐시 — Cache-Aside 패턴 + 로그인 사용자 Java 재정렬.
 *
 * <p>[캐싱 전략]
 * <ul>
 *   <li>베이스 캐시: 비로그인/로그인 공통. 항상 userId=null (비개인화)로 ES 조회 → 캐시 오염 방지.
 *   <li>로그인 사용자: 베이스 캐시 결과를 DB에서 조회한 선호 태그로 Java 재정렬만 수행.
 *       → ES 추가 호출 없이 취향 반영, kNN 대비 정확도는 소폭 낮지만 ES 부하 대폭 감소.
 *   <li>캐시 키: {@code search:base:{enc(keyword)}|{enc(category)}|{enc(genre)}|{enc(tag)}|{sort}|{page}|{size}}
 *       — URL 인코딩으로 {@code |} 구분자 충돌 방지.
 *   <li>TTL: 5분.
 * </ul>
 *
 * <p>[재정렬 방식]
 * <ul>
 *   <li>기준: {@code ContentSearchItem.tags}와 유저 선호 태그의 교집합 수 (태그 매칭 스코어).
 *   <li>동점 시 원래 BM25 relevance 순서 유지 (stable sort).
 *   <li>선호 태그 DB 조회 실패 시 재정렬 스킵, 베이스 결과 그대로 반환.
 * </ul>
 *
 * <p>[Redis 장애 시]
 * <ul>
 *   <li>캐시 조회 실패 → 캐시 미스로 처리 → ES/DB 직접 조회 (fail-open).
 *   <li>캐시 저장 실패 → 무시 (결과는 정상 반환).
 *   <li>역직렬화 실패 → 오염된 캐시 항목 무시 → ES 재조회.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchCacheService {

    private final ContentIndexingService contentIndexingService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserPreferredTagRepository userPreferredTagRepository;

    private static final String CACHE_KEY_PREFIX = "search:base:";
    private static final Duration CACHE_TTL      = Duration.ofMinutes(5);

    /**
     * 재정렬 적용 가능한 sort 타입.
     * RELATED: 관련도 기반 — 태그 선호 재정렬과 의미가 맞음.
     * LATEST, POPULAR: 날짜/인기 기반 — 재정렬 시 API 정렬 계약 위반.
     */
    private static final String RERANK_SORT = "RELATED";

    // ──────────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 공용 Cache-Aside + RELATED 정렬 시 로그인 사용자 Java 재정렬.
     *
     * <ol>
     *   <li>베이스 캐시 조회 (항상 비개인화 결과, 로그인/비로그인 공통).
     *   <li>캐시 미스 시 userId=null 로 ES 조회 → 베이스 캐시 저장.
     *   <li>로그인 + sort=RELATED: 선호 태그 기반 Java 재정렬 후 반환.
     *   <li>그 외 (비로그인 또는 sort=LATEST/POPULAR): 베이스 결과 그대로 반환.
     * </ol>
     *
     * <p>[재정렬을 RELATED에만 적용하는 이유]
     * <ul>
     *   <li>LATEST: 최신순 — 태그 재정렬 시 날짜 역전, API 정렬 계약 위반.
     *   <li>POPULAR: 인기순 — 태그 재정렬 시 조회수·북마크 순위 역전, 동일 위반.
     *   <li>RELATED: 관련도 기반 — 관련도 안에서 취향 선호 순으로 올리는 것은 계약에 부합.
     * </ul>
     *
     * <p>[페이지 단위 재정렬의 한계]
     * <ul>
     *   <li>재정렬은 각 페이지 캐시 내부에서만 수행됨.
     *   <li>다른 페이지의 항목과 순서를 비교할 수 없으므로 전역 정합성은 보장되지 않음.
     *   <li>RELATED + page=0 요청이 트래픽의 80%+ → 실용적 허용 범위.
     * </ul>
     *
     * @param keyword  검색어 (null 허용)
     * @param category 카테고리 필터 (null 허용)
     * @param genre    장르 필터 (null 허용)
     * @param tag      태그 필터 (null 허용)
     * @param userId   로그인 사용자 ID — RELATED 재정렬에만 사용. null이면 비로그인.
     * @param sort     정렬 방식 문자열 (RELATED / LATEST / POPULAR)
     * @param pageable 페이지·정렬 정보
     * @return 검색 결과 DTO (로그인 + RELATED 시 재정렬 적용)
     */
    public ContentSearchResponse searchWithCache(
            String keyword, String category, String genre, String tag,
            Long userId, String sort, Pageable pageable) {

        // ── 1. 베이스 캐시 조회 (비개인화, 로그인/비로그인 공통) ────────────
        String baseCacheKey = buildCacheKey(keyword, category, genre, tag, sort,
                pageable.getPageNumber(), pageable.getPageSize());

        ContentSearchResponse baseResponse = getFromCache(baseCacheKey).orElseGet(() -> {
            // ── 2. 캐시 미스 → ES 조회 (항상 userId=null 로 비개인화) ───────
            log.debug("[Cache MISS] 검색 캐시 미스 → ES 검색: key={}", baseCacheKey);
            Page<ContentDocument> result = contentIndexingService.search(
                    keyword, category, genre, tag, null, pageable);
            ContentSearchResponse fetched = ContentSearchResponse.from(result, keyword);
            putToCache(baseCacheKey, fetched);
            return fetched;
        });

        // ── 3. 로그인 + RELATED: 선호 태그 기반 Java 재정렬 ─────────────────
        //    LATEST/POPULAR는 날짜·인기 기반 정렬 계약 유지를 위해 재정렬 스킵
        if (userId != null && RERANK_SORT.equalsIgnoreCase(sort)) {
            return rerank(baseResponse, userId);
        }

        // ── 4. 그 외: 베이스 결과 그대로 반환 ────────────────────────────────
        return baseResponse;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  재정렬 (로그인 사용자 전용)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 선호 태그 기반 재정렬.
     *
     * <p>각 콘텐츠의 {@code tags}와 유저 선호 태그의 교집합 수를 스코어로 환산.
     * 스코어 내림차순 → 동점 시 원래 BM25 순서 (stable) 유지.
     *
     * <p>선호 태그 조회 실패(DB 오류 등) 시 재정렬 없이 베이스 결과 반환.
     */
    private ContentSearchResponse rerank(ContentSearchResponse base, Long userId) {
        Set<String> preferredTags = fetchPreferredTagNames(userId);

        if (preferredTags.isEmpty()) {
            log.debug("[Rerank SKIP] 선호 태그 없음 또는 조회 실패: userId={}", userId);
            return base;
        }

        List<ContentSearchItem> contents = base.contents();

        List<ContentSearchItem> reranked = IntStream.range(0, contents.size())
                .boxed()
                .sorted(Comparator
                        .comparingInt((Integer i) -> {
                            ContentSearchItem item = contents.get(i);
                            // 💡 제목 직접 매칭된 콘텐츠는 태그 재정렬 대상에서 제외 → 항상 상위 고정
                            if (StringUtils.hasText(item.highlightTitle())) {
                                return Integer.MAX_VALUE;
                            }
                            return tagMatchScore(item.tags(), preferredTags);
                        })
                        .reversed()
                        .thenComparingInt(i -> i)) // 동점 시 원래 ES 점수 순서 유지
                .map(contents::get)
                .toList();

        log.debug("[Rerank] 재정렬 완료: userId={}, preferredTags={}, items={}",
                userId, preferredTags.size(), reranked.size());

        return new ContentSearchResponse(reranked, base.hasNext(), base.didYouMean());
    }

    /**
     * 콘텐츠 태그 리스트와 유저 선호 태그의 교집합 수 반환.
     * 태그 비교는 소문자 정규화하여 대소문자 차이 흡수.
     */
    private int tagMatchScore(List<String> contentTags, Set<String> preferredTags) {
        if (contentTags == null || contentTags.isEmpty()) return 0;
        return (int) contentTags.stream()
                .filter(t -> t != null && preferredTags.contains(t.toLowerCase(Locale.ROOT)))
                .count();
    }

    /**
     * DB에서 유저 선호 태그 이름 목록 조회 (fetch join → N+1 없음).
     * 조회 실패 시 빈 Set 반환 → 재정렬 스킵.
     */
    private Set<String> fetchPreferredTagNames(Long userId) {
        try {
            return userPreferredTagRepository.findAllByUserIdWithTag(userId).stream()
                    .map(upt -> upt.getTag().getName().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("[Rerank] 선호 태그 조회 실패 - 재정렬 스킵: userId={}, error={}", userId, e.getMessage());
            return Set.of();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  캐시 헬퍼
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 캐시 키 생성.
     * <pre>search:base:{enc(keyword)}|{enc(category)}|{enc(genre)}|{enc(tag)}|{sort}|{page}|{size}</pre>
     * <ul>
     *   <li>keyword: 소문자 정규화 후 URL 인코딩
     *   <li>category: 대문자 정규화 후 URL 인코딩
     *   <li>genre, tag: URL 인코딩 (구분자 충돌 방지)
     *   <li>sort: RELATED/LATEST/POPULAR 알파벳 고정 → 인코딩 불필요
     *   <li>page, size: 숫자 → 인코딩 불필요
     * </ul>
     */
    String buildCacheKey(String keyword, String category, String genre, String tag,
                         String sort, int page, int size) {
    	// TODO: 한국어 키워드 포함 시 키가 길어질 수 있음
        //       키 길이 문제 발생 시 SHA-256 해싱으로 교체 고려
        //       DigestUtils.sha256Hex(raw).substring(0, 16)
        return CACHE_KEY_PREFIX
                + encode(nvl(keyword).toLowerCase(Locale.ROOT)) + "|"
                + encode(nvl(category).toUpperCase(Locale.ROOT)) + "|"
                + encode(nvl(genre))                             + "|"
                + encode(nvl(tag))                               + "|"
                + nvl(sort).toUpperCase(Locale.ROOT)             + "|"
                + page                                           + "|"
                + size;
    }

    /**
     * Redis에서 캐시 조회.
     * Redis 장애 또는 역직렬화 실패 시 Optional.empty() → 캐시 미스로 처리.
     */
    private Optional<ContentSearchResponse> getFromCache(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return Optional.empty();
            }
            log.debug("[Cache HIT] 검색 캐시 히트: key={}", cacheKey);
            return Optional.of(objectMapper.readValue(json, ContentSearchResponse.class));

        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 검색 캐시 조회 실패 - ES 직접 조회: key={}", cacheKey);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[Cache] 역직렬화 실패 - 캐시 무시: key={}, error={}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Redis에 결과 저장.
     * Redis 장애 또는 직렬화 실패 시 무시 (결과는 정상 반환).
     */
    private void putToCache(String cacheKey, ContentSearchResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
            log.debug("[Cache] 검색 결과 저장: key={}, items={}", cacheKey, response.contents().size());

        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis DOWN] 검색 캐시 저장 실패 - 무시: key={}", cacheKey);
        } catch (Exception e) {
            log.warn("[Cache] 직렬화 실패 - 무시: key={}, error={}", cacheKey, e.getMessage());
        }
    }

    /**
     * URL 인코딩 — {@code |} 등 구분자가 값에 포함되어도 캐시 키 충돌 방지.
     * 빈 문자열은 그대로 반환.
     */
    private String encode(String s) {
        return s.isEmpty() ? s : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** null-safe 빈 문자열 변환 */
    private String nvl(String s) {
        return s != null ? s : "";
    }
}
