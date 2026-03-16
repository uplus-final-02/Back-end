package org.backend.userapi.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.search.entity.EsSyncFailure;
import org.backend.userapi.search.repository.EsSyncFailureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EsSyncFailureService {

    private static final int MAX_RETRY = 5;

    private final EsSyncFailureRepository esSyncFailureRepository;
    private final ContentIndexingService contentIndexingService;
    private final EsSyncRetryExecutor retryExecutor; // 💡 REQUIRES_NEW 전용 실행기

    // 실패 기록 저장 — 동일 contentId가 이미 있으면 retry_count만 증가
    @Transactional
    public void record(Long contentId, String errorMessage) {
        esSyncFailureRepository.findByContentIdAndResolvedFalse(contentId)
                .ifPresentOrElse(
                        existing -> {
                            existing.incrementRetry(errorMessage);
                            log.warn("[DLQ] 재실패 기록 업데이트: contentId={}, retryCount={}",
                                    contentId, existing.getRetryCount());
                        },
                        () -> {
                            esSyncFailureRepository.save(EsSyncFailure.of(contentId, errorMessage));
                            log.warn("[DLQ] 신규 실패 기록 저장: contentId={}", contentId);
                        }
                );
    }

    // 재시도 배치 — 스케줄러에서 주기적으로 호출
    public void retryAll() {
        List<EsSyncFailure> targets = esSyncFailureRepository.findRetryTargets(MAX_RETRY);
        if (targets.isEmpty()) return;

        log.info("[DLQ Retry] 재시도 대상: {}건", targets.size());
        int successCount = 0;
        int failCount = 0;

        for (EsSyncFailure failure : targets) {
            boolean result = retryExecutor.retrySingle(failure, contentIndexingService);
            if (result) {
                successCount++;
                log.info("[DLQ Retry] 재시도 성공: contentId={}", failure.getContentId());
            } else {
                failCount++;
                log.warn("[DLQ Retry] 재시도 실패: contentId={}", failure.getContentId());
            }
        }

        log.info("[DLQ Retry] 완료. success={}, fail={}", successCount, failCount);
    }
}