package org.backend.userapi.history.service;

import common.enums.HistoryStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.entity.WatchHistory;
import content.repository.VideoRepository;
import content.repository.WatchHistoryRepository;
import org.backend.userapi.history.dto.SavePointRequest;
import org.backend.userapi.history.dto.SavePointResponse;
import org.backend.userapi.video.service.ViewCountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @InjectMocks
    private HistoryService historyService;

    @Mock
    private WatchHistoryRepository historyRepository;

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private ViewCountService viewCountService;

    private Video mockVideo;
    private WatchHistory mockHistory;
    private final Long userId = 1L;
    private final Long videoId = 100L;
    private final int totalDuration = 100; // 100초짜리 영상

    @BeforeEach
    void setUp() {
        Content mockContent = Content.builder().build();
        ReflectionTestUtils.setField(mockContent, "id", 200L);

        VideoFile videoFile = VideoFile.builder()
                                       .durationSec(totalDuration)
                                       .build();

        mockVideo = Video.builder().build();
        ReflectionTestUtils.setField(mockVideo, "id", videoId);
        ReflectionTestUtils.setField(mockVideo, "videoFile", videoFile);
        ReflectionTestUtils.setField(mockVideo, "mockContent", mockContent);

        mockHistory = WatchHistory.builder()
                                  .userId(userId)
                                  .video(mockVideo)
                                  .status(HistoryStatus.STARTED)
                                  .lastPositionSec(0)
                                  .build();
    }

    @Test
    @DisplayName("정상 시청 시 90% 이상 도달하면 COMPLETED 상태로 변경되고 쿨타임이 해제된다.")
    void savePoint_NormalComplete() {
        // given
        // 0초에서 90초로 이동 (누적 시청 시간이 90초로, 이동량 90초와 일치하므로 정상 시청)
        SavePointRequest request = new SavePointRequest(90, 90);

        when(videoRepository.findById(videoId)).thenReturn(Optional.of(mockVideo));
        when(historyRepository.findByUserIdAndVideoId(userId, videoId)).thenReturn(Optional.of(mockHistory));

        // when
        SavePointResponse response = historyService.savePoint(userId, videoId, request);

        // then
        assertThat(response.getStatus()).isEqualTo(HistoryStatus.COMPLETED);
        assertThat(response.getSavedPositionSec()).isEqualTo(90);

        // 쿨타임 해제 로직이 정확히 1번 호출되었는지 검증
        verify(viewCountService, times(1)).resetViewCoolTime(videoId, userId);
    }

    @Test
    @DisplayName("스크러빙 어뷰징 감지 시 90% 위치여도 COMPLETED 전환 및 쿨타임 해제가 무시된다.")
    void savePoint_ScrubbingAbuseDetected() {
        // given
        // 0초에서 시작. 실제 누적 시청 시간은 10초인데, 현재 위치가 95초인 경우 (85초 점프)
        SavePointRequest request = new SavePointRequest(95, 10);

        when(videoRepository.findById(videoId)).thenReturn(Optional.of(mockVideo));
        when(historyRepository.findByUserIdAndVideoId(userId, videoId)).thenReturn(Optional.of(mockHistory));

        // when
        SavePointResponse response = historyService.savePoint(userId, videoId, request);

        // then
        // 스크러빙으로 판정되어 COMPLETED 전환 무시. 기존 상태(STARTED) 유지.
        assertThat(response.getStatus()).isEqualTo(HistoryStatus.STARTED);
        // 위치는 업데이트됨
        assertThat(response.getSavedPositionSec()).isEqualTo(95);

        // 쿨타임 해제 로직이 절대 호출되지 않아야 함
        verify(viewCountService, never()).resetViewCoolTime(any(), any());
    }

    @Test
    @DisplayName("이미 COMPLETED 상태인 경우 90% 이상 구간을 계속 탐색해도 쿨타임 해제는 발생하지 않는다.")
    void savePoint_AlreadyCompleted() {
        // given
        // 이전에 이미 COMPLETED를 달성했다고 가정
        ReflectionTestUtils.setField(mockHistory, "status", HistoryStatus.COMPLETED);
        ReflectionTestUtils.setField(mockHistory, "lastPositionSec", 91);

        // 91초에서 93초로 2초간 정상 시청
        SavePointRequest request = new SavePointRequest(93, 2);

        when(videoRepository.findById(videoId)).thenReturn(Optional.of(mockVideo));
        when(historyRepository.findByUserIdAndVideoId(userId, videoId)).thenReturn(Optional.of(mockHistory));

        // when
        SavePointResponse response = historyService.savePoint(userId, videoId, request);

        // then
        assertThat(response.getStatus()).isEqualTo(HistoryStatus.COMPLETED);

        // 상태는 이미 COMPLETED이므로, 쿨타임 해제 로직이 다시 호출되지 않아야 함
        verify(viewCountService, never()).resetViewCoolTime(any(), any());
    }

    @Test
    @DisplayName("되감기(역방향 점프)는 어뷰징으로 간주하지 않는다.")
    void savePoint_RewindIsNotAbuse() {
        // given
        ReflectionTestUtils.setField(mockHistory, "lastPositionSec", 80);

        // 80초에서 20초로 되감기 후 5초 시청 (총 5초 시청, 현재 위치 25초)
        SavePointRequest request = new SavePointRequest(25, 5);

        when(videoRepository.findById(videoId)).thenReturn(Optional.of(mockVideo));
        when(historyRepository.findByUserIdAndVideoId(userId, videoId)).thenReturn(Optional.of(mockHistory));

        // when
        SavePointResponse response = historyService.savePoint(userId, videoId, request);

        // then
        assertThat(response.getSavedPositionSec()).isEqualTo(25);
        // 정상적인 기록 업데이트가 수행됨
        verify(historyRepository, never()).save(any());
    }
}