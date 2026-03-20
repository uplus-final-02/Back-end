package org.backend.userapi.history.service;

import common.enums.HistoryStatus;
import content.entity.Video;
import content.entity.WatchHistory;
import content.repository.VideoRepository;
import content.repository.WatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.common.exception.VideoNotFoundException;
import org.backend.userapi.history.dto.SavePointRequest;
import org.backend.userapi.history.dto.SavePointResponse;
import org.backend.userapi.video.service.ViewCountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final WatchHistoryRepository historyRepository;
    private final VideoRepository videoRepository;
    private final ViewCountService viewCountService;

    // 허용 오차 버퍼 (5초)
    private static final int SCRUBBING_BUFFER_SEC = 5;

    @Transactional
    public SavePointResponse savePoint(Long userId, Long videoId, SavePointRequest request) {
        // 1. 영상 정보 조회 (총 길이를 알기 위해 필요)
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("존재하지 않는 비디오입니다."));

        // 2. 시청 기록 조회 (없으면 생성 - Upsert 로직)
        WatchHistory history = historyRepository.findByUserIdAndVideoId(userId, videoId)
                .orElseGet(() -> {
                    WatchHistory newHistory = WatchHistory.builder()
                            .userId(userId)
                            .video(video)
                            .build();
                    return historyRepository.save(newHistory);
                });

        // 3. [핵심 로직] 상태 판별 및 업데이트
        int totalDuration = 0;
        if (video.getVideoFile() != null) {
            totalDuration = video.getVideoFile().getDurationSec();
        } // user 영상이라면 아무것도 하지 않음
        else if (video.getContent().getUploaderId() != null) {
            return null;
        }

        updateHistoryStatusAndPosition(history, userId, videoId, totalDuration, request);

        // 4. [다음 화 조회] 완료 상태라면 다음 에피소드 ID 찾기
        //Long nextVideoId = null;
        //if (history.getStatus() == HistoryStatus.COMPLETED) {
        //    nextVideoId = videoRepository.findNextEpisodeId(video.getContent().getId(), video.getEpisodeNumber());
        //}

        return new SavePointResponse(
                history.getId(),
                history.getStatus(),
                history.getLastPositionSec()//,
                //nextVideoId
        );
    }

    private void updateHistoryStatusAndPosition(WatchHistory history, Long userId, Long videoId, Integer totalDuration, SavePointRequest request) {
        int previousPosition = history.getLastPositionSec();
        int requestedPosition = request.getPositionSec();
        int requestedPlayDuration = request.getPlayDurationSec();

        // 1, [어뷰징 검증] 구간 이동 거리 계산
        int positionJump = requestedPosition - previousPosition;

        // 역방향 점프(되감기)는 어뷰징으로 간주하지 않음 -> 검증 안함
        // 정방향 점프. '물리적인 시간' + '오차 버퍼(5초)'를 초과하는 점프인지 확인
        boolean isScrubbing = (positionJump > 0) && (positionJump > requestedPlayDuration + SCRUBBING_BUFFER_SEC);

        if (isScrubbing) {
            log.warn("어뷰징 의심 감지 (스크러빙): userId={}, videoId={}, 이전위치={}, 요청위치={}, 시청시간={}",
                    userId, videoId, previousPosition, requestedPosition, requestedPlayDuration);
        }

        // 2. 완독률 계산 (현재 위치 / 전체 길이)
        double progress = 0.0;
        if (totalDuration > 0) {
            progress = (double) request.getPositionSec() / totalDuration;
        }

        // 3. 상태 변경 및 쿨타임 처리 로직
        // Rule 1: 90% 이상 도달 & 어뷰징(스크러빙)이 아닌 경우 -> COMPLETED 전환 및 쿨타임 해제
        if (progress >= 0.9 && !isScrubbing) {
            if (history.getStatus() != HistoryStatus.COMPLETED) {
                history.markAsCompleted(); // 상태 변경 및 완료 시간 기록
                // 시청 완료 -> 해당 영상 다시 시청 -> 조회수 증가 로직을 위해. 쿨타임 초기화
                viewCountService.resetViewCoolTime(videoId, userId);
            }
        }
        // Rule 2: 20초 이상 지속 시청 시 & 아직 STARTED 상태라면 -> WATCHING
        else if (history.getStatus() == HistoryStatus.STARTED && request.getPlayDurationSec() >= 20) {
            // 스크러빙 여부와 무관하게 60초 이상 '실제 시청' 을 했다면, WATCHING 으로 인정
            history.updateStatus(HistoryStatus.WATCHING);
        }
        // Rule 3: COMPLETED 상태 & 현재 위치가 마지막 시청지점보다 작은 경우 -> WATCHING, 시청지점 저장
        else if (history.getStatus() == HistoryStatus.COMPLETED && requestedPosition < history.getLastPositionSec()) {
            history.updateStatus(HistoryStatus.WATCHING);
        }


        // // 4. 위치 업데이트 (항상 마지막에 수행하여 previousPosition 검증에 영향이 없도록 함)
        history.updatePosition(requestedPosition);
    }
}
