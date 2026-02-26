package org.backend.userapi.content.service;

import content.entity.Content;
import content.entity.ContentMetricSnapshot;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("지표 변화가 있는 콘텐츠만 스냅샷이 생성되어야 한다")
    void shouldCreateSnapshotOnlyForChangedContent() {
        // given: 10분 단위 버킷 시간 설정
        LocalDateTime bucketStart = LocalDateTime.of(2026, 2, 25, 14, 10);
        LocalDateTime previousBucket = bucketStart.minusMinutes(10);

        // 콘텐츠 A: 조회수 증가 (100 -> 150)
        Content contentA = mock(Content.class);
        when(contentA.getId()).thenReturn(1L);
        when(contentA.getTotalViewCount()).thenReturn(150L);
        when(contentA.getBookmarkCount()).thenReturn(10L);

        // 콘텐츠 B: 지표 변화 없음 (100 -> 100)
        Content contentB = mock(Content.class);
        when(contentB.getId()).thenReturn(2L);
        when(contentB.getTotalViewCount()).thenReturn(100L);
        when(contentB.getBookmarkCount()).thenReturn(20L);

        // Repository Mock 설정
        when(contentRepository.findByUpdatedAtGreaterThanEqual(eq(previousBucket), any()))
            .thenReturn(new SliceImpl<>(List.of(contentA, contentB)));

        // 이전 스냅샷 데이터 Mock (A는 100회, B는 100회 기록됨)
        ContentMetricSnapshot prevA = mock(ContentMetricSnapshot.class);
        when(prevA.getId()).thenReturn(new content.entity.ContentMetricSnapshotId(previousBucket, 1L));
        when(prevA.getSnapshotViewCount()).thenReturn(100L);
        when(prevA.getSnapshotBookmarkCount()).thenReturn(10L);

        ContentMetricSnapshot prevB = mock(ContentMetricSnapshot.class);
        when(prevB.getId()).thenReturn(new content.entity.ContentMetricSnapshotId(previousBucket, 2L));
        when(prevB.getSnapshotViewCount()).thenReturn(100L);
        when(prevB.getSnapshotBookmarkCount()).thenReturn(20L);

        when(snapshotRepository.findByIdBucketStartAtAndContentIdIn(eq(previousBucket), any()))
            .thenReturn(List.of(prevA, prevB));

        // 시청 완료 통계 Mock (A만 5명 완료)
        List<Object[]> completedStats = new ArrayList<>();
        completedStats.add(new Object[]{1L, 5L});

        when(watchHistoryRepository.findCompletedUserCounts(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(completedStats);

        // when
        snapshotService.createSnapshotsForBucket(bucketStart);

        // then: 지표 변화가 있는 A만 저장되어야 함 (B는 skip)
        verify(snapshotRepository, times(1)).saveAll(argThat(list -> {
            List<ContentMetricSnapshot> snapshots = (List<ContentMetricSnapshot>) list;
            assertThat(list).hasSize(1);
            ContentMetricSnapshot saved = snapshots.get(0);
            assertThat(saved.getContent().getId()).isEqualTo(1L);
            assertThat(saved.getDeltaViewCount()).isEqualTo(50L); // 150 - 100
            assertThat(saved.getDeltaCompletedUserCount()).isEqualTo(5L);
            return true;
        }));
    }

    @Test
    @DisplayName("신규 콘텐츠는 이전 스냅샷이 없어도 현재 값이 Delta로 계산되어야 한다")
    void shouldHandleNewContentWithoutPreviousSnapshot() {
        // given
        LocalDateTime bucketStart = LocalDateTime.of(2026, 2, 25, 14, 10);
        LocalDateTime previousBucket = bucketStart.minusMinutes(10);

        Content newContent = mock(Content.class);
        when(newContent.getId()).thenReturn(99L);
        when(newContent.getTotalViewCount()).thenReturn(10L);
        when(newContent.getBookmarkCount()).thenReturn(2L);

        when(contentRepository.findByUpdatedAtGreaterThanEqual(any(), any()))
            .thenReturn(new SliceImpl<>(List.of(newContent)));

        // 이전 스냅샷 조회 시 빈 리스트 반환 (신규 콘텐츠)
        when(snapshotRepository.findByIdBucketStartAtAndContentIdIn(any(), any()))
            .thenReturn(List.of());

        // when
        snapshotService.createSnapshotsForBucket(bucketStart);

        // then: 이전 값이 0으로 취급되어 현재 값이 곧 Delta가 됨
        verify(snapshotRepository).saveAll(argThat(argument -> {
            List<ContentMetricSnapshot> snapshots = (List<ContentMetricSnapshot>) argument;
            ContentMetricSnapshot saved = snapshots.get(0);
            assertThat(saved.getDeltaViewCount()).isEqualTo(10L);
            return true;
        }));
    }
}