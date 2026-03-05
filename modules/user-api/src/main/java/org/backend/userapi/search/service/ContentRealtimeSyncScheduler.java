package org.backend.userapi.search.service;

import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentRealtimeSyncScheduler {

    private final ContentRepository contentRepository;
    private final ContentIndexingService contentIndexingService;

    @Value("${app.search.realtime-sync.enabled:true}")
    private boolean realtimeSyncEnabled;

    private LocalDateTime lastSyncedAt = LocalDateTime.now().minusMinutes(5);

    @Scheduled(fixedDelayString = "${app.search.realtime-sync.interval-ms:30000}")
    @Transactional(readOnly = true)
    public void syncUpdatedContents() {
        if (!realtimeSyncEnabled) {
            return;
        }

        List<Content> updatedContents = contentRepository.findByUpdatedAtAfter(lastSyncedAt);
        if (updatedContents.isEmpty()) {
            lastSyncedAt = LocalDateTime.now();
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (Content content : updatedContents) {
            try {
                contentIndexingService.indexContent(content.getId());
                successCount++;
            } catch (DataAccessException e) {
                // ES 다운 시: 해당 콘텐츠만 건너뛰고 다음 항목 계속 처리
                // 예외를 전파하지 않아 매 실행마다 에러 로그 폭발 방지
                failCount++;
                log.warn("[ES Sync] 콘텐츠 인덱싱 실패 - ES 연결 문제로 건너뜀 (contentId={}): {}",
                        content.getId(), e.getMessage());
            } catch (Exception e) {
                // 그 외 예상치 못한 예외도 catch하여 스케줄러 지속 실행 보장
                failCount++;
                log.warn("[ES Sync] 콘텐츠 인덱싱 실패 - 알 수 없는 오류 (contentId={}): {}",
                        content.getId(), e.getMessage());
            }
        }

        if (failCount > 0) {
            // 보수 전략:
            // 실패가 1건이라도 있으면 워터마크(lastSyncedAt)를 전진시키지 않는다.
            // -> 다음 주기에 같은 구간을 다시 읽어 실패 건 누락을 방지
            log.warn("[ES Sync] 실시간 동기화 부분 실패. success={}, fail={}, lastSyncedAt 유지={}",
                    successCount, failCount, lastSyncedAt);
        } else {
            lastSyncedAt = updatedContents.stream()
                    .map(Content::getUpdatedAt)
                    .filter(updatedAt -> updatedAt != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now());
            log.debug("[ES Sync] 실시간 동기화 완료. syncedCount={}, lastSyncedAt={}",
                    successCount, lastSyncedAt);
        }
    }
}
