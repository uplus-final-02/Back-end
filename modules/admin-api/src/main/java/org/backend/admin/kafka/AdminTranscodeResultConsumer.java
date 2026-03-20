package org.backend.admin.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.enums.ContentStatus;
import common.enums.ContentType;
import common.enums.TranscodeStatus;
import common.enums.VideoStatus;
import content.entity.Content;
import content.entity.Video;
import content.entity.VideoFile;
import content.repository.ContentRepository;
import content.repository.VideoFileRepository;
import content.repository.VideoRepository;
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
    private final VideoRepository videoRepository;
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
        if (event.contentId() == null || event.videoId() == null || event.videoFileId() == null) return;

        Content content = contentRepository.findById(event.contentId()).orElse(null);
        if (content == null) return;
        if (content.getStatus() == ContentStatus.DELETED) return;

        VideoFile vf = videoFileRepository.findById(event.videoFileId()).orElse(null);
        if (vf != null) {
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

        if (content.getType() != ContentType.SERIES) return;

        Video video = videoRepository.findById(event.videoId()).orElse(null);
        if (video == null) return;

        if ("DONE".equalsIgnoreCase(event.transcodeStatus())) {
            if (video.getStatus() != VideoStatus.PUBLIC) {
                video.updateStatus(VideoStatus.PRIVATE);
            }
        }

        boolean anyPublicDone = videoRepository.existsByContent_IdAndStatusAndVideoFile_TranscodeStatus(
                content.getId(),
                VideoStatus.PUBLIC,
                TranscodeStatus.DONE
        );

        if (anyPublicDone) content.activate();
        else content.hide();
    }
}