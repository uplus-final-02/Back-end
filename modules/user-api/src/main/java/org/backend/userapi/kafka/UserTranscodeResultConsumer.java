package org.backend.userapi.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.enums.ContentStatus;
import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import core.events.video.VideoTranscodeResultEvent;
import content.entity.UserContent;
import content.entity.UserVideoFile;
import content.repository.UserContentRepository;
import content.repository.UserVideoFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.userapi.sse.UserPublishSseService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserTranscodeResultConsumer {

    private final ObjectMapper objectMapper;
    private final UserPublishSseService sseService;
    private final UserContentRepository userContentRepository;
    private final UserVideoFileRepository userVideoFileRepository;

    @Transactional
    @KafkaListener(
            topics = "${app.kafka.topics.video-transcode-user-result}",
            groupId = "${app.kafka.consumer.user-result-group}"
    )
    public void onMessage(String message) {
        final VideoTranscodeResultEvent event;
        try {
            event = objectMapper.readValue(message, VideoTranscodeResultEvent.class);
        } catch (Exception e) {
            log.error("[USER_RESULT][INVALID] message={}", message, e);
            return;
        }

        log.info("[USER_RESULT][RECV] eventId={}, status={}, contentId={}, videoFileId={}, hlsMasterKey={}, durationSec={}",
                event.eventId(), event.transcodeStatus(), event.contentId(), event.videoFileId(),
                event.hlsMasterKey(), event.durationSec());

        if (event.contentId() != null) {
            sseService.publish(event.contentId(), "TRANSCODE_RESULT", message);
        }

        applyResultToDb(event);
    }

    private void applyResultToDb(VideoTranscodeResultEvent event) {
        if (event.contentId() == null || event.videoFileId() == null) {
            log.warn("[USER_RESULT][SKIP] contentId/videoFileId is null. eventId={}", event.eventId());
            return;
        }

        Long userContentId = event.contentId();
        Long userVideoFileId = event.videoFileId();

        UserContent uc = userContentRepository.findById(userContentId).orElse(null);
        if (uc == null) {
            log.warn("[USER_RESULT][SKIP] UserContent not found. contentId={}", userContentId);
            return;
        }

        UserVideoFile uvf = userVideoFileRepository.findById(userVideoFileId).orElse(null);
        if (uvf == null) {
            log.warn("[USER_RESULT][SKIP] UserVideoFile not found. fileId={}", userVideoFileId);
            return;
        }

        if (uc.getContentStatus() == ContentStatus.DELETED) {
            log.info("[USER_RESULT][SKIP] deleted content. contentId={}", userContentId);
            return;
        }

        if (uvf.getContent() != null && uvf.getContent().getId() != null
                && !uvf.getContent().getId().equals(userContentId)) {
            log.warn("[USER_RESULT][SKIP] mismatch: event.contentId={} != uvf.contentId={}",
                    userContentId, uvf.getContent().getId());
            return;
        }

        TranscodeStatus resultStatus = normalize(event.transcodeStatus());
        uvf.updateTranscodeStatus(resultStatus);

        if (resultStatus == TranscodeStatus.DONE) {
            if (event.durationSec() != null) {
                uvf.updateDurationSec(event.durationSec());
            }
            if (event.hlsMasterKey() != null && !event.hlsMasterKey().isBlank()) {
                uvf.updateHlsKey(event.hlsMasterKey());
            }
        }

        if (uc.isPublishRequested() && resultStatus == TranscodeStatus.DONE) {
            uc.activate();
            uvf.updateVideoStatus(VideoStatus.PUBLIC);
            log.info("[USER_RESULT][PUBLISH] contentId={}, fileId={} => ACTIVE/PUBLIC", userContentId, userVideoFileId);
            return;
        }

        uc.hide();
        uvf.updateVideoStatus(VideoStatus.PRIVATE);
        log.info("[USER_RESULT][HIDE] contentId={}, publishRequested={}, ts={} => HIDDEN/PRIVATE",
                userContentId, uc.isPublishRequested(), resultStatus);
    }

    private TranscodeStatus normalize(String raw) {
        if (raw == null) return TranscodeStatus.FAILED;
        if ("DONE".equalsIgnoreCase(raw)) return TranscodeStatus.DONE;
        if ("FAILED".equalsIgnoreCase(raw)) return TranscodeStatus.FAILED;
        return TranscodeStatus.FAILED;
    }
}