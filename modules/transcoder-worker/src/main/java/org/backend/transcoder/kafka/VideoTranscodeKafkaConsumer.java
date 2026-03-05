package org.backend.transcoder.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import common.enums.TranscodeStatus;
import content.entity.VideoFile;
import content.repository.VideoFileRepository;
import core.events.video.VideoTranscodeRequestedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodeKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final VideoFileRepository videoFileRepository;

    @KafkaListener(
            topics = "${app.kafka.topics.video-transcode}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void onMessage(String message) {
        try {
            VideoTranscodeRequestedEvent event =
                    objectMapper.readValue(message, VideoTranscodeRequestedEvent.class);

            VideoFile vf = videoFileRepository.findById(event.videoFileId())
                    .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + event.videoFileId()));

            if (vf.getTranscodeStatus() == TranscodeStatus.DONE || vf.getTranscodeStatus() == TranscodeStatus.FAILED) {
                log.info("[TRANSCODE][SKIP] already finished. videoFileId={}, status={}",
                        vf.getId(), vf.getTranscodeStatus());
                return;
            }

            vf.updateTranscodeStatus(TranscodeStatus.PROCESSING);

            log.info("[TRANSCODE][START] eventId={}, contentId={}, videoId={}, videoFileId={}, originalKey={}",
                    event.eventId(), event.contentId(), event.videoId(), event.videoFileId(), event.originalKey());

        } catch (Exception e) {
            log.error("[TRANSCODE][CONSUME_FAIL] message={}", message, e);
            throw new IllegalStateException("KAFKA_CONSUME_FAILED", e);
        }
    }
}