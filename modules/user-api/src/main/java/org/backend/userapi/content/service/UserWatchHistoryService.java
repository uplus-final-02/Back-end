package org.backend.userapi.content.service;

import common.enums.ContentStatus;
import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.entity.UserWatchHistory;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import content.repository.UserWatchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserWatchHistoryService {

    private final UserWatchHistoryRepository userWatchHistoryRepository;
    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;

    /**
     * 유저 콘텐츠 시청 기록 Upsert.
     *
     * <p>재생 가능한 콘텐츠(ACTIVE + PUBLIC + DONE + hlsUrl 존재)에 대해서만 이력을 저장한다.
     * 이미 이력이 있으면 {@code lastWatchedAt}만 갱신하고, 없으면 INSERT한다.
     *
     * <p>[동시성] 첫 시청 시 동시 요청이 들어오면 UNIQUE(user_id, content_id) 제약 위반이
     * 발생할 수 있다. {@link DataIntegrityViolationException}을 잡아 재조회 후 갱신하는
     * 방식으로 원자성을 보장한다.
     *
     * @param userId        현재 로그인 유저 ID
     * @param userContentId 시청한 유저 콘텐츠 ID
     */
    @Transactional
    public void upsertWatchHistory(Long userId, Long userContentId) {
        UserContent uc = userContentRepository.findById(userContentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "USER_CONTENT_NOT_FOUND: " + userContentId));

        // /play와 동일한 재생 가능 검증 — /watch를 직접 호출해도 비공개·미완료 이력이 쌓이지 않도록
        validatePlayable(uc, userContentId);

        userWatchHistoryRepository.findByUserIdAndUserContent_Id(userId, userContentId)
                .ifPresentOrElse(
                        existing -> existing.updateLastWatchedAt(LocalDateTime.now()),
                        () -> insertWithFallback(userId, uc)
                );
    }

    /**
     * INSERT 시도 → UNIQUE 충돌 발생 시 재조회 후 UPDATE로 폴백.
     * 동시 첫 시청 요청에서 둘 다 isEmpty()를 보고 내려온 경우를 처리한다.
     */
    private void insertWithFallback(Long userId, UserContent uc) {
        try {
            userWatchHistoryRepository.save(
                    UserWatchHistory.builder()
                            .userId(userId)
                            .userContent(uc)
                            .lastWatchedAt(LocalDateTime.now())
                            .build()
            );
        } catch (DataIntegrityViolationException e) {
            log.debug("[UserWatchHistory] UNIQUE 충돌 → 재조회 후 UPDATE. userId={}, contentId={}",
                    userId, uc.getId());
            userWatchHistoryRepository
                    .findByUserIdAndUserContent_Id(userId, uc.getId())
                    .ifPresent(existing -> existing.updateLastWatchedAt(LocalDateTime.now()));
        }
    }

    private void validatePlayable(UserContent uc, Long userContentId) {
        if (uc.getContentStatus() != ContentStatus.ACTIVE) {
            throw new IllegalStateException("CONTENT_NOT_AVAILABLE");
        }

        UserVideoFile uvf = userVideoFileRepository.findByContent_Id(userContentId)
                .orElseThrow(() -> new IllegalStateException(
                        "USER_VIDEO_FILE_NOT_FOUND: contentId=" + userContentId));

        if (uvf.getVideoStatus() != VideoStatus.PUBLIC) {
            throw new IllegalStateException("VIDEO_NOT_PUBLIC");
        }
        if (uvf.getTranscodeStatus() != TranscodeStatus.DONE) {
            throw new IllegalStateException("VIDEO_NOT_READY: transcodeStatus=" + uvf.getTranscodeStatus());
        }
        if (uvf.getHlsUrl() == null || uvf.getHlsUrl().isBlank()) {
            throw new IllegalStateException("HLS_URL_NOT_READY");
        }
    }
}
