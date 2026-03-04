package org.backend.admin.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import content.entity.VideoFile;
import content.repository.VideoFileRepository;
import common.enums.TranscodeStatus;
import core.events.video.VideoTranscodeRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodeKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final VideoFileRepository videoFileRepository;

    @KafkaListener(
            topics = "video.transcode.requested",
            groupId = "${app.kafka.transcode.group-id:transcoder-dev}"
    )
    @Transactional
    public void onMessage(String message) {
        try {
            // 1) 역직렬화
            VideoTranscodeRequestedEvent event =
                    objectMapper.readValue(message, VideoTranscodeRequestedEvent.class);

            // 2) VideoFile 조회
            VideoFile vf = videoFileRepository.findById(event.videoFileId())
                    .orElseThrow(() -> new IllegalStateException("VIDEO_FILE_NOT_FOUND: " + event.videoFileId()));

            // 3) 상태 전이: WAITING -> PROCESSING (또는 DONE/FAILED가 아니면 PROCESSING)
            if (vf.getTranscodeStatus() == TranscodeStatus.DONE || vf.getTranscodeStatus() == TranscodeStatus.FAILED) {
                log.info("[TRANSCODE][SKIP] already finished. videoFileId={}, status={}",
                        vf.getId(), vf.getTranscodeStatus());
                return;
            }

            vf.updateTranscodeStatus(TranscodeStatus.PROCESSING);

            log.info("[TRANSCODE][CONSUMED] eventId={}, contentId={}, videoId={}, videoFileId={}, status=PROCESSING, originalKey={}",
                    event.eventId(), event.contentId(), event.videoId(), event.videoFileId(), event.originalKey());

        } catch (Exception e) {
            // 여기서 예외를 던지면 Kafka가 재시도(설정에 따라)할 수 있습니다.
            log.error("[TRANSCODE][CONSUME_FAIL] message={}", message, e);
            throw new IllegalStateException("KAFKA_CONSUME_FAILED", e);
        }
    }
}