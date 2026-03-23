package org.backend.admin.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.enums.ContentStatus;
import common.enums.TranscodeStatus;
import content.entity.Content;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import core.events.video.VideoTranscodeResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backend.admin.sse.AdminPublishSseService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTranscodeResultConsumer {

    private final ObjectMapper objectMapper;
    private final AdminPublishSseService sseService;

    private final ContentRepository contentRepository;
    private final VideoFileRepository videoFileRepository;

    @KafkaListener(
            topics = "${app.kafka.topics.video-transcode-admin-result}",
            groupId = "${app.kafka.consumer.admin-result-group}"
    )
    public void onMessage(String message) {
        VideoTranscodeResultEvent event;
        try {
            event = objectMapper.readValue(message, VideoTranscodeResultEvent.class);
        } catch (Exception e) {
            log.error("[ADMIN_RESULT][INVALID] message={}", message, e);
            return;
        }

        log.info("[ADMIN_RESULT][RECV] eventId={}, status={}, contentId={}, videoId={}, videoFileId={}",
                event.eventId(), event.transcodeStatus(), event.contentId(), event.videoId(), event.videoFileId());

        if (event.contentId() != null) {
            sseService.publish(event.contentId(), "TRANSCODE_RESULT", message);
        }

        apply(event);
    }

    @Transactional
    protected void apply(VideoTranscodeResultEvent event) {
        if (event.contentId() == null) return;

        Content content = contentRepository.findById(event.contentId()).orElse(null);
        if (content == null) return;
        if (content.getStatus() == ContentStatus.DELETED) return;

        if (event.videoFileId() != null) {
            VideoFile vf = videoFileRepository.findById(event.videoFileId()).orElse(null);
            if (vf != null) {

                try {
                    Long vfContentId = vf.getVideo().getContent().getId();
                    if (!event.contentId().equals(vfContentId)) {
                        log.warn("[ADMIN_RESULT][SKIP] mismatched content. eventContentId={}, vfContentId={}, videoFileId={}",
                                event.contentId(), vfContentId, event.videoFileId());
                        return;
                    }
                } catch (Exception ignore) {
                }

                if ("DONE".equalsIgnoreCase(event.transcodeStatus())) {
                    vf.updateTranscodeStatus(TranscodeStatus.DONE);

                    if (event.hlsMasterKey() != null && !event.hlsMasterKey().isBlank()) {
                        vf.updateHlsKey(event.hlsMasterKey());
                    }
                    if (event.durationSec() != null) {
                        vf.updateDurationSec(event.durationSec());
                    }

                } else if ("FAILED".equalsIgnoreCase(event.transcodeStatus())) {
                    vf.updateTranscodeStatus(TranscodeStatus.FAILED);
                }
            }
        }

        if (!content.isPublishRequested()) {
            content.hide();
            return;
        }

        boolean anyDone = videoFileRepository.existsByVideo_Content_IdAndTranscodeStatus(
                content.getId(),
                TranscodeStatus.DONE
        );

        if (anyDone) content.activate();
        else content.hide();
    }
}