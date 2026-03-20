package org.backend.userapi.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.search.entity.SearchLog;
import org.backend.userapi.search.repository.SearchLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;  
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchLogService {

    private final SearchLogRepository searchLogRepository;

    // 검색 응답 속도에 영향 없도록 비동기 처리
    @Async("searchLogExecutor")
    public void log(String keyword, int resultCount, Long userId) {
        if (!StringUtils.hasText(keyword)) return;
        try {
            searchLogRepository.save(SearchLog.of(keyword, resultCount, userId));
        } catch (Exception e) {
            log.warn("[SearchLog] 검색어 로그 저장 실패 (무시): keyword={}, error={}", keyword, e.getMessage());
        }
    }

    // 결과 없는 검색어 조회 (사전 추가 후보)
    public List<String> getZeroResultKeywords(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Pageable top50 = PageRequest.of(0, 50);
        return searchLogRepository.findZeroResultKeywords(since, top50)
                .stream()
                .map(row -> (String) row[0])
                .toList();
    }

    // 인기 검색어 조회
    public List<String> getTopKeywords(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Pageable top20 = PageRequest.of(0, 20);
        return searchLogRepository.findTopKeywords(since, top20)
                .stream()
                .map(row -> (String) row[0])
                .toList();
    }

    // [피드백 반영] 90일 이상 된 로그 자동 삭제 — 테이블 무한 증가 방지
    // 한 번에 전체 삭제 시 대량 DELETE로 테이블 락 위험 → 10000건씩 배치 삭제
    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    public void cleanupOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int total = 0;
        int deleted;

        do {
            deleted = searchLogRepository.deleteOldBatch(cutoff);
            total += deleted;
        } while (deleted > 0);

        if (total > 0) {
            log.info("[SearchLog] 만료 로그 삭제 완료: {}건 (기준: {}일 이전)", total, 90);
        }
    }
}