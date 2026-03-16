package org.backend.userapi.search.service;

import content.entity.UserContent;
import content.repository.UserContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.recommendation.service.TagVectorService;
import org.backend.userapi.search.document.UserContentDocument;
import org.backend.userapi.search.repository.UserContentSearchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 유저 업로드 콘텐츠 ES 인덱싱 서비스 구현체.
 *
 * <p>[태그 벡터 전략]
 * UserContent에 contentTags가 없으므로 parentContent의 contentTags를 상속한다.
 * parentContent → contentTags → tag.id 를 추출해 TagVectorService.buildContentVector() 호출.
 *
 * <p>[인덱싱 대상]
 * contentStatus = ACTIVE 인 UserContent만 인덱싱한다.
 *
 * <p>[커서 기반 청크 처리]
 * 기존 ContentIndexingServiceImpl과 동일한 커서 방식(id > lastId)을 사용해
 * 대용량 데이터에서도 O(n) offset 없이 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserContentIndexingServiceImpl implements UserContentIndexingService {

    private final UserContentRepository userContentRepository;
    private final UserContentSearchRepository userContentSearchRepository;
    private final TagVectorService tagVectorService;

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);

    private String lastIndexingStatus = "IDLE";
    private String lastErrorMessage = null;
    private LocalDateTime lastRunTime = null;

    private static final int PAGE_SIZE = 500;

    // =========================================================
    //  Public API
    // =========================================================

    @Override
    @Async("indexingExecutor")
    @Transactional(readOnly = true)
    public void indexAllUserContents() {
        if (isIndexing.getAndSet(true)) {
            log.warn("[UserContent 인덱싱] 이미 진행 중 — 중복 실행 방지");
            return;
        }

        try {
            log.info("[UserContent 인덱싱] 전체 인덱싱 시작 (커서 기반 청크 처리)");
            lastIndexingStatus = "RUNNING";
            lastRunTime = LocalDateTime.now();
            lastErrorMessage = null;

            long lastId = 0L;
            long totalIndexed = 0;
            List<Long> ids;

            do {
                // Step 1: ID만 커서 페이징 (컬렉션 조인 없어 LIMIT 정확)
                ids = userContentRepository.findActiveIdsCursor(lastId, PageRequest.of(0, PAGE_SIZE));
                if (ids.isEmpty()) break;

                // Step 2: ID IN 절로 FETCH JOIN 조회 (N+1 방지, 페이징 오염 없음)
                List<UserContent> chunk = userContentRepository.findAllWithParentTagsByIds(ids);

                List<UserContentDocument> documents = chunk.stream()
                        .map(this::toDocument)
                        .toList();

                userContentSearchRepository.saveAll(documents);
                totalIndexed += documents.size();
                lastId = ids.get(ids.size() - 1);

                log.info("[UserContent 인덱싱] 누적 {}건 완료 (커서 ID: {})", totalIndexed, lastId);

            } while (ids.size() == PAGE_SIZE);

            log.info("[UserContent 인덱싱] 완료 — 총 {}건", totalIndexed);
            lastIndexingStatus = "SUCCESS";

        } catch (Exception e) {
            log.error("[UserContent 인덱싱] 치명적 오류 발생", e);
            lastIndexingStatus = "FAILED";
            lastErrorMessage = e.getMessage();
            throw new RuntimeException("유저 콘텐츠 인덱싱 실패", e);
        } finally {
            isIndexing.set(false);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void indexUserContent(Long userContentId) {
        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "UserContent 없음: " + userContentId));
        userContentSearchRepository.save(toDocument(uc));
        log.info("[UserContent 인덱싱] 단건 완료 — userContentId={}", userContentId);
    }

    @Override
    public void deleteUserContent(Long userContentId) {
        userContentSearchRepository.deleteById(userContentId);
        log.info("[UserContent 인덱싱] 단건 삭제 완료 — userContentId={}", userContentId);
    }

    @Override
    public Map<String, Object> getIndexingStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", lastIndexingStatus);
        status.put("lastRunTime", lastRunTime);
        if (lastErrorMessage != null) {
            status.put("error", lastErrorMessage);
        }
        return status;
    }

    // =========================================================
    //  내부 변환
    // =========================================================

    /**
     * UserContent → UserContentDocument 변환.
     *
     * <p>태그와 태그 벡터는 parentContent에서 상속한다.
     * thumbnailUrl도 parentContent에서 가져온다 (UserContent에 없음).
     */
    private UserContentDocument toDocument(UserContent uc) {
        // parentContent 태그 상속
        List<String> tagNames = uc.getParentContent().getContentTags().stream()
                .map(ct -> ct.getTag().getName())
                .collect(Collectors.toList());

        List<Long> tagIds = uc.getParentContent().getContentTags().stream()
                .map(ct -> ct.getTag().getId())
                .collect(Collectors.toList());

        float[] tagVector = tagVectorService.buildContentVector(tagIds);

        return UserContentDocument.builder()
                .userContentId(uc.getId())
                .parentContentId(uc.getParentContent().getId())
                .title(uc.getTitle())
                .tags(tagNames)
                .contentStatus(uc.getContentStatus().name())
                .accessLevel(uc.getAccessLevel().name())
                .thumbnailUrl(uc.getParentContent().getThumbnailUrl())  // UserContent에 thumbnailUrl 없음
                .totalViewCount(uc.getTotalViewCount())
                .bookmarkCount(uc.getBookmarkCount())
                .createdAt(uc.getCreatedAt())
                .tagVector(tagVector)
                .build();
    }
}
