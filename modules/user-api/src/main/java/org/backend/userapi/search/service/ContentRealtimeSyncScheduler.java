package org.backend.userapi.search.service;

import content.entity.Content;
import content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

        for (Content content : updatedContents) {
            contentIndexingService.indexContent(content.getId());
        }

        lastSyncedAt = updatedContents.stream()
                .map(Content::getUpdatedAt)
                .filter(updatedAt -> updatedAt != null)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());

        log.debug("Realtime content sync completed. syncedCount={}, lastSyncedAt={}", updatedContents.size(), lastSyncedAt);
    }
}
