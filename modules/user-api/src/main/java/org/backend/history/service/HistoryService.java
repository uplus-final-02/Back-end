package org.backend.history.service;

import common.enums.HistoryStatus;
import content.entity.Video;
import content.entity.WatchHistory;
import content.repository.VideoRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HistoryService {

  private final HistoryRepository historyRepository;
  private final VideoRepository videoRepository;

  @Transactional
  public SavePointResponse savePoint(Long userId, Long videoId, SavePointRequest request) {
    // 1. 영상 정보 조회 (총 길이를 알기 위해 필요)
    Video video = videoRepository.findById(videoId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 비디오입니다."));

    // 2. 시청 기록 조회 (없으면 생성 - Upsert 로직)
    WatchHistory history = historyRepository.findByUserIdAndVideoId(userId, videoId)
        .orElseGet(() -> {
          WatchHistory newHistory = WatchHistory.create(userId, video);
          return historyRepository.save(newHistory);
        });

    // 3. [핵심 로직] 상태 판별 및 업데이트
    updateHistoryStatusAndPosition(history, video.getDurationSec(), request);

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

  private void updateHistoryStatusAndPosition(WatchHistory history, Integer totalDuration, SavePointRequest request) {
    // 3-1. 위치 업데이트 (항상 저장)
    history.updatePosition(request.getPositionSec());

    // 3-2. 완독률 계산 (현재 위치 / 전체 길이)
    double progress = (double) request.getPositionSec() / totalDuration;

    // Rule 1: 90% 이상 시청 시 -> COMPLETED
    if (progress >= 0.9) {
      if (history.getStatus() != HistoryStatus.COMPLETED) {
        history.markAsCompleted(); // 상태 변경 및 완료 시간 기록
      }
    }
    // Rule 2: 60초 이상 지속 시청 시 & 아직 STARTED 상태라면 -> WATCHING
    else if (history.getStatus() == HistoryStatus.STARTED && request.getPlayDurationSec() >= 60) {
      history.updateStatus(HistoryStatus.WATCHING);
    }
  }
}