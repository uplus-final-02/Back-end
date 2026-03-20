package org.backend.admin.metrics.service;

import common.enums.MetricJobStatus;
import content.entity.Content;
import content.entity.TrendingHistory;
import content.repository.ContentRepository;
import content.repository.TrendingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.backend.admin.metrics.dto.*;
import org.backend.admin.metrics.repository.AdminSnapshotBucketQueryRepository;
import org.backend.admin.metrics.repository.AdminTrendingTimelineQueryRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.repository.UserNicknameInfo;
import user.repository.UserRepository;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMetricQueryService {

    private final AdminSnapshotBucketQueryRepository snapshotBucketQueryRepository;
    private final AdminTrendingTimelineQueryRepository trendingTimelineQueryRepository;

    private final TrendingHistoryRepository trendingHistoryRepository;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 1) 버킷 리스트
    public List<AdminSnapshotBucketSummaryResponse> getSnapshotBucketSummaries(
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    ) {
        List<Object[]> rows = snapshotBucketQueryRepository.findBucketSummariesSnapshot10m(
                Timestamp.valueOf(from),
                Timestamp.valueOf(to),
                pageable.getPageSize(),
                (int) pageable.getOffset()
        );

        List<AdminSnapshotBucketSummaryResponse> result = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            LocalDateTime bucketStartAt = toLocalDateTime(r[0]);
            long rowsCount = toLong(r[1]);
            long sumView = toLong(r[2]);
            long sumBookmark = toLong(r[3]);
            long sumCompleted = toLong(r[4]);

            MetricJobStatus status = (r[5] == null) ? null : MetricJobStatus.valueOf(String.valueOf(r[5]));
            String message = (r[6] == null) ? null : String.valueOf(r[6]);

            result.add(new AdminSnapshotBucketSummaryResponse(
                    bucketStartAt,
                    rowsCount,
                    sumView,
                    sumBookmark,
                    sumCompleted,
                    status,
                    message
            ));
        }
        return result;
    }

    // 2) 트렌딩 상세(특정 calculatedAt) - 기존 trendingHistoryRepository 사용
    public AdminTrendingDetailResponse getTrendingDetail(LocalDateTime calculatedAt, int limit) {
        List<TrendingHistory> histories = trendingHistoryRepository
                .findAllByCalculatedAtOrderByRankingAsc(calculatedAt)
                .stream()
                .limit(Math.max(limit, 1))
                .toList();

        if (histories.isEmpty()) {
            return new AdminTrendingDetailResponse(calculatedAt, 0, List.of());
        }

        List<Long> contentIds = histories.stream().map(TrendingHistory::getContentId).toList();
        Map<Long, Content> contentMap = contentRepository.findAllById(contentIds).stream()
                .collect(Collectors.toMap(Content::getId, Function.identity(), (a, b) -> a));

        Map<Long, String> uploaderNicknameMap = getUploaderNicknameMap(contentMap.values());

        List<AdminTrendingItemResponse> items = new ArrayList<>(histories.size());
        for (TrendingHistory h : histories) {
            Content c = contentMap.get(h.getContentId());
            if (c == null) continue;

            Long uploaderId = c.getUploaderId();
            String uploaderName = (uploaderId == null) ? "관리자" : uploaderNicknameMap.getOrDefault(uploaderId, "알 수 없음");

            items.add(new AdminTrendingItemResponse(
                    h.getRanking(),
                    c.getId(),
                    c.getTitle(),
                    c.getType(),
                    h.getTrendingScore(),
                    h.getDeltaViewCount(),
                    h.getDeltaBookmarkCount(),
                    h.getDeltaCompletedCount(),
                    uploaderId,
                    uploaderName
            ));
        }

        return new AdminTrendingDetailResponse(calculatedAt, items.size(), items);
    }

    // 3) 특정 날짜 타임라인(정각별 topN)
    public AdminTrendingTimelineResponse getTrendingTimeline(String dateStr, int perHourLimit) {
        LocalDate date = LocalDate.parse(dateStr, DATE_FMT);
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        List<Object[]> rows = trendingTimelineQueryRepository.findTrendingRows(
                Timestamp.valueOf(from),
                Timestamp.valueOf(to)
        );

        if (rows.isEmpty()) {
            return new AdminTrendingTimelineResponse(dateStr, perHourLimit, List.of());
        }

        record Row(LocalDateTime t, int rank, long contentId, double score, long dv, long db, long dc) {}
        Map<LocalDateTime, List<Row>> grouped = new LinkedHashMap<>();

        for (Object[] r : rows) {
            LocalDateTime t = toLocalDateTime(r[0]);
            int rank = (int) toLong(r[1]);
            long cid = toLong(r[2]);
            double score = toDouble(r[3]);
            long dv = toLong(r[4]);
            long db = toLong(r[5]);
            long dc = toLong(r[6]);
            grouped.computeIfAbsent(t, k -> new ArrayList<>()).add(new Row(t, rank, cid, score, dv, db, dc));
        }

        Set<Long> allContentIds = grouped.values().stream()
                .flatMap(List::stream)
                .map(Row::contentId)
                .collect(Collectors.toSet());

        Map<Long, Content> contentMap = contentRepository.findAllById(allContentIds).stream()
                .collect(Collectors.toMap(Content::getId, Function.identity(), (a, b) -> a));

        Map<Long, String> uploaderNicknameMap = getUploaderNicknameMap(contentMap.values());

        List<AdminTrendingTimelineResponse.HourBlock> hours = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            LocalDateTime calculatedAt = entry.getKey();

            List<AdminTrendingItemResponse> items = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(Row::rank))
                    .limit(Math.max(perHourLimit, 1))
                    .map(tr -> {
                        Content c = contentMap.get(tr.contentId());
                        if (c == null) return null;

                        Long uploaderId = c.getUploaderId();
                        String uploaderName = (uploaderId == null) ? "관리자" : uploaderNicknameMap.getOrDefault(uploaderId, "알 수 없음");

                        return new AdminTrendingItemResponse(
                                tr.rank(),
                                c.getId(),
                                c.getTitle(),
                                c.getType(),
                                tr.score(),
                                tr.dv(),
                                tr.db(),
                                tr.dc(),
                                uploaderId,
                                uploaderName
                        );
                    })
                    .filter(Objects::nonNull)
                    .toList();

            hours.add(new AdminTrendingTimelineResponse.HourBlock(calculatedAt, items));
        }

        return new AdminTrendingTimelineResponse(dateStr, perHourLimit, hours);
    }

    // 4) 반영 검증: snapshots(1h 합) vs trending_history(delta) 비교
    public AdminTrendingVerifyResponse verifyTrending(LocalDateTime calculatedAt) {
        LocalDateTime windowStart = calculatedAt.minusHours(1);
        LocalDateTime windowEnd = calculatedAt;

        List<TrendingHistory> histories = trendingHistoryRepository.findAllByCalculatedAtOrderByRankingAsc(calculatedAt);
        if (histories.isEmpty()) {
            return new AdminTrendingVerifyResponse(calculatedAt, windowStart, windowEnd, 0, 0, 0, List.of());
        }

        List<Object[]> snapRows = snapshotBucketQueryRepository.findSnapshotSumsByContent(
                Timestamp.valueOf(windowStart),
                Timestamp.valueOf(windowEnd)
        );

        Map<Long, long[]> snapMap = new HashMap<>();
        for (Object[] r : snapRows) {
            long cid = toLong(r[0]);
            long dv = toLong(r[1]);
            long db = toLong(r[2]);
            long dc = toLong(r[3]);
            snapMap.put(cid, new long[]{dv, db, dc});
        }

        List<AdminTrendingVerifyResponse.Item> diffs = new ArrayList<>();
        int ok = 0, diff = 0;

        for (TrendingHistory h : histories) {
            long cid = h.getContentId();
            long[] s = snapMap.getOrDefault(cid, new long[]{0, 0, 0});

            boolean match =
                    h.getDeltaViewCount().equals(s[0]) &&
                            h.getDeltaBookmarkCount().equals(s[1]) &&
                            h.getDeltaCompletedCount().equals(s[2]);

            if (match) ok++;
            else {
                diff++;
                diffs.add(new AdminTrendingVerifyResponse.Item(
                        cid, h.getRanking(),
                        h.getDeltaViewCount(), s[0],
                        h.getDeltaBookmarkCount(), s[1],
                        h.getDeltaCompletedCount(), s[2],
                        false
                ));
            }
        }

        return new AdminTrendingVerifyResponse(
                calculatedAt, windowStart, windowEnd,
                histories.size(), ok, diff, diffs
        );
    }

    // ───────── helpers ─────────

    private Map<Long, String> getUploaderNicknameMap(Collection<Content> contents) {
        Set<Long> uploaderIds = contents.stream()
                .map(Content::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (uploaderIds.isEmpty()) return Collections.emptyMap();

        List<UserNicknameInfo> results = userRepository.findNicknamesByIds(uploaderIds);
        return results.stream()
                .collect(Collectors.toMap(UserNicknameInfo::getId, UserNicknameInfo::getNickname));
    }

    private static LocalDateTime toLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof LocalDateTime ldt) return ldt;
        return LocalDateTime.parse(String.valueOf(v));
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(v));
    }
}