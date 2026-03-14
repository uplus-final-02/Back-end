package org.backend.admin.stats.service;

import org.backend.admin.stats.repository.TagHomeStatsJdbcRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.admin.stats.repository.TagHomeStatsJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagHomeStatsBatchService {

	private final TagHomeStatsJdbcRepository tagHomeStatsJdbcRepository;
	
	@Transactional
    public int saveDailyStats(LocalDate statDate) {
        validateStatDate(statDate);

        log.info("[TagHomeStats] 일별 통계 집계 시작: statDate={}", statDate);

        List<TagHomeStatsJdbcRepository.TagHomeStatsRow> rows =
                tagHomeStatsJdbcRepository.findDailyStatsRows(statDate);

        if (rows.isEmpty()) {
            log.info("[TagHomeStats] 저장 대상 데이터 없음: statDate={}", statDate);
            return 0;
        }

        int savedCount = tagHomeStatsJdbcRepository.upsert(rows);

        log.info("[TagHomeStats] 일별 통계 집계 완료: statDate={}, 대상건수={}, 저장건수={}",
                statDate, rows.size(), savedCount);
        
        return savedCount;
    }
	
	private void validateStatDate(LocalDate statDate) {
        if (statDate == null) {
            throw new IllegalArgumentException("statDate는 필수입니다.");
        }
    }
	
}
