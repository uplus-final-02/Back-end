package org.backend.userapi.search.service;

import java.util.Collections;
import java.util.List;

import org.backend.userapi.recommendation.dto.UserRecommendedContentResponse;
import org.backend.userapi.search.document.UserContentDocument;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import content.entity.UserContent;
import content.repository.UserContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserContentSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final UserContentRepository userContentRepository;

    /**
     * 크리에이터 탭/피드: 전체 유저 영상 조회 or 특정 관리자 콘텐츠 매핑 영상 조회
     * ES 우선 조회, ES 다운 시 DB Fallback.
     */
    public List<UserRecommendedContentResponse> searchCreatorContents(
            Long parentContentId, Pageable pageable) {
        try {
            return searchCreatorFromEs(parentContentId, pageable);
        } catch (DataAccessException e) {
            log.warn("[크리에이터 피드 ES DOWN] parentContentId={} → DB Fallback", parentContentId);
            return searchCreatorFromDb(parentContentId, pageable);
        }
    }

    /**
     * 크리에이터 페이지: 특정 유저가 올린 영상 목록.
     * ES 우선 조회, ES 다운 시 빈 결과.
     */
    public List<UserRecommendedContentResponse> searchByUploaderId(
            Long uploaderId, Pageable pageable) {
        try {
            return searchByUploaderFromEs(uploaderId, pageable);
        } catch (DataAccessException e) {
            log.warn("[크리에이터 페이지 ES DOWN] uploaderId={} → 빈 결과", uploaderId);
            return List.of();
        }
    }

    // ── ES 조회 ──────────────────────────────────────────────────

    private List<UserRecommendedContentResponse> searchCreatorFromEs(
            Long parentContentId, Pageable pageable) {

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    // 🌟 동적 쿼리: parentContentId가 있을 때만 필터 추가
                    if (parentContentId != null) {
                        b.filter(f -> f.term(t -> t
                                .field("parentContentId").value(parentContentId)));
                    }
                    // 공통 조건: 무조건 공개(ACTIVE)된 영상만
                    b.filter(f -> f.term(t -> t
                            .field("contentStatus").value("ACTIVE")));
                    return b;
                }))
                .withPageable(pageable)
                .build();

        SearchHits<UserContentDocument> hits =
                elasticsearchOperations.search(query, UserContentDocument.class);

        log.info("[크리에이터 피드] parentContentId={} → ES 결과 {}건",
                parentContentId == null ? "전체조회" : parentContentId, hits.getTotalHits());

        return hits.getSearchHits().stream()
                .map(hit -> UserRecommendedContentResponse.from(hit.getContent()))
                .toList();
    }

    private List<UserRecommendedContentResponse> searchByUploaderFromEs(
            Long uploaderId, Pageable pageable) {

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f -> f.term(t -> t
                            .field("uploaderId").value(uploaderId)));
                    b.filter(f -> f.term(t -> t
                            .field("contentStatus").value("ACTIVE")));
                    return b;
                }))
                .withPageable(pageable)
                .build();

        SearchHits<UserContentDocument> hits =
                elasticsearchOperations.search(query, UserContentDocument.class);

        log.info("[크리에이터 페이지] uploaderId={} → ES 결과 {}건",
                uploaderId, hits.getTotalHits());

        return hits.getSearchHits().stream()
                .map(hit -> UserRecommendedContentResponse.from(hit.getContent()))
                .toList();
    }

    // ── DB Fallback ──────────────────────────────────────────────

    private List<UserRecommendedContentResponse> searchCreatorFromDb(
            Long parentContentId, Pageable pageable) {

        List<UserContent> contents;
        
        // 🌟 DB도 동적 분기 처리
        if (parentContentId != null) {
            // 영화 상세 탭용 (특정 영화 관련 영상만)
            contents = userContentRepository.findActiveByParentContentId(parentContentId, pageable);
        } else {
            // 메인 피드용 (전체 유저 영상) 👈 여기서 방금 만든 쿼리를 호출!
            contents = userContentRepository.findAllActiveContents(pageable);
        }

        return contents.stream()
                .map(uc -> new UserRecommendedContentResponse(
                        uc.getId(),
                        uc.getParentContent().getId(),
                        uc.getTitle(),
                        uc.getThumbnailUrl() != null
                                ? uc.getThumbnailUrl()
                                : uc.getParentContent().getThumbnailUrl(),
                        uc.getAccessLevel().name(),
                        uc.getTotalViewCount(),
                        uc.getBookmarkCount(),
                        uc.getParentContent().getContentTags().stream()
                                .map(ct -> ct.getTag().getName())
                                .toList()
                ))
                .toList();
    }
    
    public List<UserRecommendedContentResponse> searchByKeyword(
            String keyword, Pageable pageable) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> {
                        b.must(m -> m.bool(bool -> {
                            bool.should(s -> s.match(ma -> ma
                                    .field("title")
                                    .query(keyword)
                                    .boost(15.0f)));
                            bool.should(s -> s.match(ma -> ma
                                    .field("title.ngram")
                                    .query(keyword)
                                    .boost(5.0f)));
                            bool.minimumShouldMatch("1");
                            return bool;
                        }));
                        b.filter(f -> f.term(t -> t
                                .field("contentStatus").value("ACTIVE")));
                        return b;
                    }))
                    .withPageable(pageable)
                    .build();

            SearchHits<UserContentDocument> hits =
                    elasticsearchOperations.search(query, UserContentDocument.class);

            log.info("[크리에이터 검색] keyword={} → ES 결과 {}건",
                    keyword, hits.getTotalHits());

            return hits.getSearchHits().stream()
                    .map(hit -> UserRecommendedContentResponse.from(hit.getContent()))
                    .toList();

        } catch (DataAccessException e) {
            log.warn("[크리에이터 검색 ES DOWN] keyword={} → 빈 결과", keyword);
            return List.of();
        }
    }
}