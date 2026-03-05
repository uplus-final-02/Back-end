package org.backend.userapi.content.service;

import content.entity.Content;
import content.entity.ContentMetricSnapshot;
import content.entity.ContentMetricSnapshotId;
import content.repository.ContentMetricSnapshotRepository;
import content.repository.ContentRepository;
import content.repository.WatchHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentMetricSnapshotServiceTest {

    @InjectMocks
    private ContentMetricSnapshotService snapshotService;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentMetricSnapshotRepository snapshotRepository;

    @Mock
    private WatchHistoryRepository watchHistoryRepository;

    @Test
    @DisplayName("[Case C] 정상 상태: 지표 변화가 있는 콘텐츠만 정상적으로 Delta가 계산되어 스냅샷이 생성된다")
    void shouldCreateSnapshotOnlyForChangedContent() {
        // given
        LocalDateTime bucketStart = LocalDateTime.of(2026, 2, 25, 14, 10);
        LocalDateTime previousBucket = bucketStart.minusMinutes(10);

        Content contentA = mock(Content.class);
        when(contentA.getId()).thenReturn(1L);
        when(contentA.getTotalViewCount()).thenReturn(150L);
        when(contentA.getBookmarkCount()).thenReturn(10L);

        Content contentB = mock(Content.class);
        when(contentB.getId()).thenReturn(2L);
        when(contentB.getTotalViewCount()).thenReturn(100L);
        when(contentB.getBookmarkCount()).thenReturn(20L);

        when(contentRepository.findByUpdatedAtGreaterThanEqual(eq(previousBucket), any()))
            .thenReturn(new SliceImpl<>(List.of(contentA, contentB)));

        ContentMetricSnapshot prevA = mock(ContentMetricSnapshot.class);
        when(prevA.getId()).thenReturn(new ContentMetricSnapshotId(previousBucket, 1L));
        when(prevA.getSnapshotViewCount()).thenReturn(100L);
        when(prevA.getSnapshotBookmarkCount()).thenReturn(10L);

        ContentMetricSnapshot prevB = mock(ContentMetricSnapshot.class);
        when(prevB.getId()).thenReturn(new ContentMetricSnapshotId(previousBucket, 2L));
        when(prevB.getSnapshotViewCount()).thenReturn(100L);
        when(prevB.getSnapshotBookmarkCount()).thenReturn(20L);

        when(snapshotRepository.findLatestSnapshotsByContentIds(any()))
            .thenReturn(List.of(prevA, prevB));

        List<Object[]> completedStats = new ArrayList<>();
        completedStats.add(new Object[]{1L, 5L});

        when(watchHistoryRepository.findCompletedUserCounts(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(completedStats);

        // when
        snapshotService.createSnapshotsForBucket(bucketStart);

        // then: A만 저장되고 B는 skip
        verify(snapshotRepository, times(1)).saveAll(argThat(list -> {
            List<ContentMetricSnapshot> snapshots = (List<ContentMetricSnapshot>) list;
            assertThat(snapshots).hasSize(1);
            ContentMetricSnapshot saved = snapshots.get(0);
            assertThat(saved.getId().getContentId()).isEqualTo(1L);
            assertThat(saved.getDeltaViewCount()).isEqualTo(50L); // 150 - 100 = 50
            assertThat(saved.getDeltaCompletedUserCount()).isEqualTo(5L);
            return true;
        }));
    }

    @Test
    @DisplayName("[Case A] 신규 콘텐츠: Baseline 초기화 환경에서 이전 스냅샷이 없으면 신규 업로드로 간주하고 현재 값을 Delta로 반영한다")
    void shouldHandleNewContentWithoutPreviousSnapshot() {
        // given
        LocalDateTime bucketStart = LocalDateTime.of(2026, 2, 25, 14, 10);
        LocalDateTime previousBucket = bucketStart.minusMinutes(10);

        Content newContent = mock(Content.class);
        when(newContent.getId()).thenReturn(99L);
        when(newContent.getTotalViewCount()).thenReturn(10L); // 현재 누적값 10
        when(newContent.getBookmarkCount()).thenReturn(2L);

        when(contentRepository.findByUpdatedAtGreaterThanEqual(eq(previousBucket), any()))
            .thenReturn(new SliceImpl<>(List.of(newContent)));

        // 최근 스냅샷 없음 (빈 리스트 반환) -> 신규 콘텐츠임을 의미
        when(snapshotRepository.findLatestSnapshotsByContentIds(any()))
            .thenReturn(List.of());

        // 시청 완료 통계 Mock (없어도 조회수/북마크가 0이 아니므로 필터링을 통과함)
        when(watchHistoryRepository.findCompletedUserCounts(any(), any()))
            .thenReturn(List.of());

        // when
        snapshotService.createSnapshotsForBucket(bucketStart);

        // then: 방어 로직(0)이 아니라, 현재 값 전체가 순수 증분(Delta)으로 인정되어야 함
        verify(snapshotRepository).saveAll(argThat(argument -> {
            List<ContentMetricSnapshot> snapshots = (List<ContentMetricSnapshot>) argument;
            assertThat(snapshots).hasSize(1);
            ContentMetricSnapshot saved = snapshots.get(0);

            // 현재 값이 그대로 Delta에 반영되는지 검증
            assertThat(saved.getDeltaViewCount()).isEqualTo(10L);
            assertThat(saved.getDeltaBookmarkCount()).isEqualTo(2L);
            assertThat(saved.getSnapshotViewCount()).isEqualTo(10L);
            return true;
        }));
    }

    @Test
    @DisplayName("[Case B] 단절 상태: 현재 조회수가 과거보다 작아진 경우(초기화), Delta가 0인 기준점(Baseline)으로 생성된다")
    void shouldHandleReversedViewCount() {
        // given
        LocalDateTime bucketStart = LocalDateTime.of(2026, 2, 25, 14, 10);
        LocalDateTime previousBucket = bucketStart.minusMinutes(10);

        Content reversedContent = mock(Content.class);
        when(reversedContent.getId()).thenReturn(77L);
        when(reversedContent.getTotalViewCount()).thenReturn(50L); // 현재 누적 50 (초기화됨)
        when(reversedContent.getBookmarkCount()).thenReturn(5L);

        when(contentRepository.findByUpdatedAtGreaterThanEqual(eq(previousBucket), any()))
            .thenReturn(new SliceImpl<>(List.of(reversedContent)));

        // 과거 스냅샷 모킹 (과거 누적값이 100으로 현재보다 큼)
        ContentMetricSnapshot prevSnapshot = mock(ContentMetricSnapshot.class);
        when(prevSnapshot.getId()).thenReturn(new ContentMetricSnapshotId(bucketStart.minusHours(1), 77L));
        when(prevSnapshot.getSnapshotViewCount()).thenReturn(100L);
        when(prevSnapshot.getSnapshotBookmarkCount()).thenReturn(5L);

        when(snapshotRepository.findLatestSnapshotsByContentIds(any()))
            .thenReturn(List.of(prevSnapshot));

        // 필터링 통과용
        List<Object[]> completedStats = new ArrayList<>();
        completedStats.add(new Object[]{77L, 1L});
        when(watchHistoryRepository.findCompletedUserCounts(any(), any()))
            .thenReturn(completedStats);

        // when
        snapshotService.createSnapshotsForBucket(bucketStart);

        // then: 누적값이 역전되었으므로 단절로 간주하여 Delta가 0이어야 함
        verify(snapshotRepository).saveAll(argThat(argument -> {
            List<ContentMetricSnapshot> snapshots = (List<ContentMetricSnapshot>) argument;
            assertThat(snapshots).hasSize(1);
            ContentMetricSnapshot saved = snapshots.get(0);
            assertThat(saved.getDeltaViewCount()).isEqualTo(0L);
            return true;
        }));
    }
}